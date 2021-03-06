/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.http.impl.conn;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Lookup;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.HttpClientConnectionOperator;
import org.apache.http.conn.HttpConnectionFactory;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.pool.ConnFactory;
import org.apache.http.pool.ConnPoolControl;
import org.apache.http.pool.PoolEntry;
import org.apache.http.pool.PoolEntryCallback;
import org.apache.http.pool.PoolStats;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;
import org.apache.http.util.Asserts;

/**
 * {@code ClientConnectionPoolManager} maintains a pool of
 * {@link HttpClientConnection}s and is able to service connection requests
 * from multiple execution threads. Connections are pooled on a per route
 * basis. A request for a route which already the manager has persistent
 * connections for available in the pool will be services by leasing
 * a connection from the pool rather than creating a brand new connection.
 * <p>
 * {@code ClientConnectionPoolManager} maintains a maximum limit of connection
 * on a per route basis and in total. Per default this implementation will
 * create no more than than 2 concurrent connections per given route
 * and no more 20 connections in total. For many real-world applications
 * these limits may prove too constraining, especially if they use HTTP
 * as a transport protocol for their services. Connection limits, however,
 * can be adjusted using {@link ConnPoolControl} methods.
 * </p>
 * <p>
 * Total time to live (TTL) set at construction time defines maximum life span
 * of persistent connections regardless of their expiration setting. No persistent
 * connection will be re-used past its TTL value.
 * </p>
 * <p>
 * The handling of stale connections was changed in version 4.4.
 * Previously, the code would check every connection by default before re-using it.
 * The code now only checks the connection if the elapsed time since
 * the last use of the connection exceeds the timeout that has been set.
 * The default timeout is set to 2000ms
 * </p>
 *
 * @since 4.3
 *
 * 连接池管理器，不要说成连接池，容易被误导
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public class PoolingHttpClientConnectionManager
    //
    implements HttpClientConnectionManager, ConnPoolControl<HttpRoute>, Closeable {

    private final Log log = LogFactory.getLog(getClass());

    private final ConfigData configData;
    // CPool才是连接池 里面包含 InternalConnectionFactory(本类的内部类)
    private final CPool pool;

    // DefaultHttpClientConnectionOperator
    private final HttpClientConnectionOperator connectionOperator;
    private final AtomicBoolean isShutDown;

    /**
     *
     */
    private static Registry<ConnectionSocketFactory> getDefaultRegistry() {
        return RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", SSLConnectionSocketFactory.getSocketFactory())
                .build();
    }

    /**
     * default constructor
     */
    public PoolingHttpClientConnectionManager() {
        this(getDefaultRegistry());
    }

    public PoolingHttpClientConnectionManager(final long timeToLive, final TimeUnit timeUnit) {
        this(getDefaultRegistry(), null, null ,null, timeToLive, timeUnit);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry) {
        this(socketFactoryRegistry, null, null);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final DnsResolver dnsResolver) {
        this(socketFactoryRegistry, null, dnsResolver);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> connFactory) {
        this(socketFactoryRegistry, connFactory, null);
    }

    public PoolingHttpClientConnectionManager(
            final HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> connFactory) {
        this(getDefaultRegistry(), connFactory, null);
    }

    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> connFactory,
            final DnsResolver dnsResolver) {
        this(socketFactoryRegistry, connFactory, null, dnsResolver, -1, TimeUnit.MILLISECONDS);
    }

    /**
     * org.apache.http.impl.client.HttpClientBuilder#build() 调用
     * connFactory=schemePortResolver=dnsResolvernull
     */
    public PoolingHttpClientConnectionManager(
            final Registry<ConnectionSocketFactory> socketFactoryRegistry,
            final HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> connFactory,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver,
            final long timeToLive, final TimeUnit timeUnit) {

        this(new DefaultHttpClientConnectionOperator(socketFactoryRegistry, schemePortResolver, dnsResolver),
            connFactory,
            timeToLive, timeUnit
        );
    }

    /**
     * @since 4.4
     */
    public PoolingHttpClientConnectionManager(
        final HttpClientConnectionOperator httpClientConnectionOperator,
        final HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> connFactory,
        final long timeToLive,
        final TimeUnit timeUnit) {

        super();
        // 内部类1
        this.configData = new ConfigData();
        // 内部类2 (其实就是对 HttpConnectionFactory包装了一层,为毛要这么做呢??? )
        InternalConnectionFactory connectionFactory = new InternalConnectionFactory(this.configData, connFactory);
        // 每个router默认最大2个链接
        this.pool = new CPool(connectionFactory, 2, 20, timeToLive, timeUnit);
        System.out.println("PoolingHttpClientConnectionManager() CPool = " + pool);

        this.pool.setValidateAfterInactivity(2000);

        this.connectionOperator = Args.notNull(httpClientConnectionOperator, "HttpClientConnectionOperator");
        this.isShutDown = new AtomicBoolean(false);
    }

    /**
     * Visible for test.
     */
    PoolingHttpClientConnectionManager(
            final CPool pool,
            final Lookup<ConnectionSocketFactory> socketFactoryRegistry,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver) {
        super();
        this.configData = new ConfigData();
        this.pool = pool;
        DefaultHttpClientConnectionOperator operator =
                new DefaultHttpClientConnectionOperator(socketFactoryRegistry, schemePortResolver, dnsResolver);
        this.connectionOperator = operator;
        this.isShutDown = new AtomicBoolean(false);
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            shutdown();
        } finally {
            super.finalize();
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    private String format(final HttpRoute route, final Object state) {
        final StringBuilder buf = new StringBuilder();
        buf.append("[route: ").append(route).append("]");
        if (state != null) {
            buf.append("[state: ").append(state).append("]");
        }
        return buf.toString();
    }

    private String formatStats(final HttpRoute route) {
        final StringBuilder buf = new StringBuilder();
        final PoolStats totals = this.pool.getTotalStats();
        final PoolStats stats = this.pool.getStats(route);
        buf.append("[total available: ").append(totals.getAvailable()).append("; ");
        buf.append("route allocated: ").append(stats.getLeased() + stats.getAvailable());
        buf.append(" of ").append(stats.getMax()).append("; ");
        buf.append("total allocated: ").append(totals.getLeased() + totals.getAvailable());
        buf.append(" of ").append(totals.getMax()).append("]");
        return buf.toString();
    }

    private String format(final CPoolEntry entry) {
        final StringBuilder buf = new StringBuilder();
        buf.append("[id: ").append(entry.getId()).append("]");
        buf.append("[route: ").append(entry.getRoute()).append("]");
        final Object state = entry.getState();
        if (state != null) {
            buf.append("[state: ").append(state).append("]");
        }
        return buf.toString();
    }

    private SocketConfig resolveSocketConfig(final HttpHost host) {
        SocketConfig socketConfig = this.configData.getSocketConfig(host);
        if (socketConfig == null) {
            socketConfig = this.configData.getDefaultSocketConfig();
        }
        if (socketConfig == null) {
            socketConfig = SocketConfig.DEFAULT;
        }
        return socketConfig;
    }

    /**
     * org.apache.http.impl.execchain.MainClientExec#execute() 真正执行的时候才会从连接池获取 http conn
     */
    @Override
    public ConnectionRequest requestConnection(final HttpRoute route, final Object state) {

        Args.notNull(route, "HTTP route");

        if (this.log.isDebugEnabled()) {
            this.log.debug("Connection request: " + format(route, state) + formatStats(route));
        }

        Asserts.check(!this.isShutDown.get(), "Connection pool shut down");

        /*
         * CPool继承AbstractConnPool中返回匿名内部类
         */
        final Future<CPoolEntry> future = this.pool.lease(route, state, null);

        /*
         * 匿名内部类 MainClientExec#execute()
         */
        ConnectionRequest connectionRequest = new ConnectionRequest() {

            @Override
            public boolean cancel() {
                return future.cancel(true);
            }

            //
            @Override
            public HttpClientConnection get(final long timeout, final TimeUnit timeUnit)
                    throws InterruptedException, ExecutionException, ConnectionPoolTimeoutException {

                //获取一个conn链接                    下面一个方法
                final HttpClientConnection conn = leaseConnection(future, timeout, timeUnit);

                if (conn.isOpen()) {
                    final HttpHost host;
                    if (route.getProxyHost() != null) {
                        host = route.getProxyHost();
                    } else {
                        host = route.getTargetHost();
                    }

                    final SocketConfig socketConfig = resolveSocketConfig(host);
                    conn.setSocketTimeout(socketConfig.getSoTimeout());
                }

                return conn;
            }
        };

        System.out.println(this
                + " requestConnection() 创建匿名内部类 connectionRequest = " + connectionRequest);

        return  connectionRequest;
    }

    /**
     * 上一个方里 ConnectionRequest 调用了我
     */
    protected HttpClientConnection leaseConnection(
            final Future<CPoolEntry> future,
            final long timeout,
            final TimeUnit timeUnit)
            throws InterruptedException, ExecutionException, ConnectionPoolTimeoutException {

        final CPoolEntry entry;
        try {

            // future: org.apache.http.pool.AbstractConnPool.lease() 中返回的匿名内部类
            entry = future.get(timeout, timeUnit);

            if (entry == null || future.isCancelled()) {
                throw new ExecutionException(new CancellationException("Operation cancelled"));
            }

            Asserts.check(entry.getConnection() != null, "Pool entry with no connection");

            if (this.log.isDebugEnabled()) {
                this.log.debug("Connection leased: " + format(entry) + formatStats(entry.getRoute()));
            }

            //
            HttpClientConnection httpClientConnection = CPoolProxy.newProxy(entry);
            return httpClientConnection;
        } catch (final TimeoutException ex) {
            throw new ConnectionPoolTimeoutException("Timeout waiting for connection from pool");
        }
    }

    /**
     *
     */
    @Override
    public void releaseConnection(
            final HttpClientConnection managedConn,
            final Object state,
            final long keepalive, final TimeUnit timeUnit) {

        Args.notNull(managedConn, "Managed connection");

        synchronized (managedConn) {

            final CPoolEntry entry = CPoolProxy.detach(managedConn);
            if (entry == null) {
                return;
            }

            final ManagedHttpClientConnection conn = entry.getConnection();
            try {
                if (conn.isOpen()) {
                    final TimeUnit effectiveUnit = timeUnit != null ? timeUnit : TimeUnit.MILLISECONDS;
                    entry.setState(state);
                    entry.updateExpiry(keepalive, effectiveUnit);
                    if (this.log.isDebugEnabled()) {
                        final String s;
                        if (keepalive > 0) {
                            s = "for " + (double) effectiveUnit.toMillis(keepalive) / 1000 + " seconds";
                        } else {
                            s = "indefinitely";
                        }
                        this.log.debug("Connection " + format(entry) + " can be kept alive " + s);
                    }
                    conn.setSocketTimeout(0);
                }
            } finally {
                this.pool.release(entry, conn.isOpen() && entry.isRouteComplete());
                if (this.log.isDebugEnabled()) {
                    this.log.debug("Connection released: " + format(entry) + formatStats(entry.getRoute()));
                }
            }
        }
    }

    /**
     * org.apache.http.impl.execchain.MainClientExec#establishRoute() 中调用
     */
    @Override
    public void connect(
            final HttpClientConnection managedConn,
            final HttpRoute route,
            final int connectTimeout,
            final HttpContext context) throws IOException {

        Args.notNull(managedConn, "Managed Connection");
        Args.notNull(route, "HTTP route");

        final ManagedHttpClientConnection conn;
        synchronized (managedConn) {
            final CPoolEntry entry = CPoolProxy.getPoolEntry(managedConn);
            // LoggingManagedHttpClientConnection
            conn = entry.getConnection();
        }
        final HttpHost host;
        if (route.getProxyHost() != null) {
            host = route.getProxyHost();
        } else {
            host = route.getTargetHost();
        }
        InetSocketAddress socketAddress = route.getLocalSocketAddress();
        SocketConfig socketConfig = resolveSocketConfig(host);

        this.connectionOperator.connect(conn, host, socketAddress, connectTimeout, socketConfig, context);
    }

    @Override
    public void upgrade(
            final HttpClientConnection managedConn,
            final HttpRoute route,
            final HttpContext context) throws IOException {

        Args.notNull(managedConn, "Managed Connection");
        Args.notNull(route, "HTTP route");

        final ManagedHttpClientConnection conn;
        synchronized (managedConn) {
            final CPoolEntry entry = CPoolProxy.getPoolEntry(managedConn);
            conn = entry.getConnection();
        }
        this.connectionOperator.upgrade(conn, route.getTargetHost(), context);
    }

    @Override
    public void routeComplete(
            final HttpClientConnection managedConn,
            final HttpRoute route,
            final HttpContext context) throws IOException {

        Args.notNull(managedConn, "Managed Connection");
        Args.notNull(route, "HTTP route");

        synchronized (managedConn) {
            final CPoolEntry entry = CPoolProxy.getPoolEntry(managedConn);
            entry.markRouteComplete();
        }
    }

    @Override
    public void shutdown() {
        if (this.isShutDown.compareAndSet(false, true)) {
            this.log.debug("Connection manager is shutting down");
            try {
                this.pool.enumLeased(new PoolEntryCallback<HttpRoute, ManagedHttpClientConnection>() {

                    @Override
                    public void process(final PoolEntry<HttpRoute, ManagedHttpClientConnection> entry) {
                        final ManagedHttpClientConnection connection = entry.getConnection();
                        if (connection != null) {
                            try {
                                connection.shutdown();
                            } catch (final IOException iox) {
                                if (log.isDebugEnabled()) {
                                    log.debug("I/O exception shutting down connection", iox);
                                }
                            }
                        }
                    }

                });
                this.pool.shutdown();
            } catch (final IOException ex) {
                this.log.debug("I/O exception shutting down connection manager", ex);
            }
            this.log.debug("Connection manager shut down");
        }
    }

    /**
     *
     *
     */
    @Override
    public void closeIdleConnections(final long idleTimeout, final TimeUnit timeUnit) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("Closing connections idle longer than " + idleTimeout + " " + timeUnit);
        }
        this.pool.closeIdle(idleTimeout, timeUnit);
    }

    @Override
    public void closeExpiredConnections() {
        this.log.debug("Closing expired connections");
        this.pool.closeExpired();
    }

    protected void enumAvailable(final PoolEntryCallback<HttpRoute, ManagedHttpClientConnection> callback) {
        this.pool.enumAvailable(callback);
    }

    protected void enumLeased(final PoolEntryCallback<HttpRoute, ManagedHttpClientConnection> callback) {
        this.pool.enumLeased(callback);
    }

    @Override
    public int getMaxTotal() {
        return this.pool.getMaxTotal();
    }

    @Override
    public void setMaxTotal(final int max) {
        this.pool.setMaxTotal(max);
    }

    @Override
    public int getDefaultMaxPerRoute() {
        return this.pool.getDefaultMaxPerRoute();
    }

    @Override
    public void setDefaultMaxPerRoute(final int max) {
        this.pool.setDefaultMaxPerRoute(max);
    }

    @Override
    public int getMaxPerRoute(final HttpRoute route) {
        return this.pool.getMaxPerRoute(route);
    }

    @Override
    public void setMaxPerRoute(final HttpRoute route, final int max) {
        this.pool.setMaxPerRoute(route, max);
    }

    @Override
    public PoolStats getTotalStats() {
        return this.pool.getTotalStats();
    }

    @Override
    public PoolStats getStats(final HttpRoute route) {
        return this.pool.getStats(route);
    }

    /**
     * @since 4.4
     */
    public Set<HttpRoute> getRoutes() {
        return this.pool.getRoutes();
    }

    public SocketConfig getDefaultSocketConfig() {
        return this.configData.getDefaultSocketConfig();
    }

    public void setDefaultSocketConfig(final SocketConfig defaultSocketConfig) {
        this.configData.setDefaultSocketConfig(defaultSocketConfig);
    }

    public ConnectionConfig getDefaultConnectionConfig() {
        return this.configData.getDefaultConnectionConfig();
    }

    public void setDefaultConnectionConfig(final ConnectionConfig defaultConnectionConfig) {
        this.configData.setDefaultConnectionConfig(defaultConnectionConfig);
    }

    public SocketConfig getSocketConfig(final HttpHost host) {
        return this.configData.getSocketConfig(host);
    }

    public void setSocketConfig(final HttpHost host, final SocketConfig socketConfig) {
        this.configData.setSocketConfig(host, socketConfig);
    }

    public ConnectionConfig getConnectionConfig(final HttpHost host) {
        return this.configData.getConnectionConfig(host);
    }

    public void setConnectionConfig(final HttpHost host, final ConnectionConfig connectionConfig) {
        this.configData.setConnectionConfig(host, connectionConfig);
    }

    /**
     * @see #setValidateAfterInactivity(int)
     *
     * @since 4.4
     */
    public int getValidateAfterInactivity() {
        return pool.getValidateAfterInactivity();
    }

    /**
     * Defines period of inactivity in milliseconds after which persistent connections must
     * be re-validated prior to being {@link #leaseConnection(java.util.concurrent.Future,
     *   long, java.util.concurrent.TimeUnit) leased} to the consumer. Non-positive value passed
     * to this method disables connection validation. This check helps detect connections
     * that have become stale (half-closed) while kept inactive in the pool.
     *
     * @see #leaseConnection(java.util.concurrent.Future, long, java.util.concurrent.TimeUnit)
     *
     * @since 4.4
     */
    public void setValidateAfterInactivity(final int ms) {
        pool.setValidateAfterInactivity(ms);
    }

    /**
     * 内部类 1
     */
    static class ConfigData {

        private final Map<HttpHost, SocketConfig> socketConfigMap;
        private final Map<HttpHost, ConnectionConfig> connectionConfigMap;

        private volatile SocketConfig defaultSocketConfig;
        private volatile ConnectionConfig defaultConnectionConfig;

        /**
         * constructor
         */
        ConfigData() {
            super();
            this.socketConfigMap = new ConcurrentHashMap<HttpHost, SocketConfig>();
            this.connectionConfigMap = new ConcurrentHashMap<HttpHost, ConnectionConfig>();
        }

        public SocketConfig getDefaultSocketConfig() {
            return this.defaultSocketConfig;
        }

        public void setDefaultSocketConfig(final SocketConfig defaultSocketConfig) {
            this.defaultSocketConfig = defaultSocketConfig;
        }

        public ConnectionConfig getDefaultConnectionConfig() {
            return this.defaultConnectionConfig;
        }

        public void setDefaultConnectionConfig(final ConnectionConfig defaultConnectionConfig) {
            this.defaultConnectionConfig = defaultConnectionConfig;
        }

        public SocketConfig getSocketConfig(final HttpHost host) {
            return this.socketConfigMap.get(host);
        }

        public void setSocketConfig(final HttpHost host, final SocketConfig socketConfig) {
            this.socketConfigMap.put(host, socketConfig);
        }

        public ConnectionConfig getConnectionConfig(final HttpHost host) {
            return this.connectionConfigMap.get(host);
        }

        public void setConnectionConfig(final HttpHost host, final ConnectionConfig connectionConfig) {
            this.connectionConfigMap.put(host, connectionConfig);
        }

    }

    /**
     * 内部类 2  内部conn工厂，
     * 但是不是实际干活的，干活的是 connFactory: ManagedHttpClientConnectionFactory
     */
    static class InternalConnectionFactory implements ConnFactory<HttpRoute, ManagedHttpClientConnection> {

        //
        private final ConfigData configData;
        // ManagedHttpClientConnectionFactory
        private final HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> connFactory;

        /**
         * default constructor
         */
        InternalConnectionFactory(
                final ConfigData configData,
                final HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> connFactory) {
            super();
            // 内部类1
            this.configData = configData != null ? configData : new ConfigData();
            // 连接工厂
            this.connFactory = connFactory != null ? connFactory : ManagedHttpClientConnectionFactory.INSTANCE;
        }

        /**
         * 把活交给了 ManagedHttpClientConnectionFactory
         * @param route
         */
        @Override
        public ManagedHttpClientConnection create(final HttpRoute route) throws IOException {
            ConnectionConfig config = null;
            if (route.getProxyHost() != null) {
                config = this.configData.getConnectionConfig(route.getProxyHost());
            }
            if (config == null) {
                config = this.configData.getConnectionConfig(route.getTargetHost());
            }
            if (config == null) {
                config = this.configData.getDefaultConnectionConfig();
            }
            if (config == null) {
                config = ConnectionConfig.DEFAULT;
            }
            //
            return this.connFactory.create(route, config);
        }

    }

}
