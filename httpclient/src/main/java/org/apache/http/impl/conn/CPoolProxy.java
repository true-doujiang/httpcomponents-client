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

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.SSLSession;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpConnectionMetrics;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.protocol.HttpContext;

/**
 * @since 4.3
 *
 * CPoolProxy 他是一个HttpConnection
 */
class CPoolProxy implements ManagedHttpClientConnection, HttpContext {

    // 注意这里用了 volatile修饰
    // 里面包含了 connection
    private volatile CPoolEntry poolEntry;


    /**
     * 调用方
     * @see org.apache.http.impl.conn.PoolingHttpClientConnectionManager#leaseConnection
     */
    public static HttpClientConnection newProxy(final CPoolEntry poolEntry) {
        return new CPoolProxy(poolEntry);
    }

    private static CPoolProxy getProxy(final HttpClientConnection conn) {
        if (!CPoolProxy.class.isInstance(conn)) {
            throw new IllegalStateException("Unexpected connection proxy class: " + conn.getClass());
        }
        return CPoolProxy.class.cast(conn);
    }

    /**
     *
     * @param proxy
     */
    public static CPoolEntry getPoolEntry(final HttpClientConnection proxy) {
        CPoolProxy cPoolProxy = getProxy(proxy);
        final CPoolEntry entry = cPoolProxy.getPoolEntry();
        if (entry == null) {
            throw new ConnectionShutdownException();
        }
        return entry;
    }

    public static CPoolEntry detach(final HttpClientConnection conn) {
        return getProxy(conn).detach();
    }


    /**
     * default constructor
     */
    CPoolProxy(final CPoolEntry entry) {
        super();
        this.poolEntry = entry;
    }


    CPoolEntry getPoolEntry() {
        return this.poolEntry;
    }

    CPoolEntry detach() {
        final CPoolEntry local = this.poolEntry;
        this.poolEntry = null;
        return local;
    }

    //
    ManagedHttpClientConnection getConnection() {
        final CPoolEntry local = this.poolEntry;
        if (local == null) {
            return null;
        }
        return local.getConnection();
    }

    //
    ManagedHttpClientConnection getValidConnection() {
        final ManagedHttpClientConnection conn = getConnection();
        if (conn == null) {
            throw new ConnectionShutdownException();
        }
        return conn;
    }

    @Override
    public void close() throws IOException {
        final CPoolEntry local = this.poolEntry;
        if (local != null) {
            local.closeConnection();
        }
    }

    @Override
    public void shutdown() throws IOException {
        final CPoolEntry local = this.poolEntry;
        if (local != null) {
            local.shutdownConnection();
        }
    }

    @Override
    public boolean isOpen() {
        final CPoolEntry local = this.poolEntry;
        return local != null ? !local.isClosed() : false;
    }

    @Override
    public boolean isStale() {
        final HttpClientConnection conn = getConnection();
        return conn != null ? conn.isStale() : true;
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        managedHttpClientConnection.setSocketTimeout(timeout);
    }

    @Override
    public int getSocketTimeout() {
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        return managedHttpClientConnection.getSocketTimeout();
    }

    @Override
    public String getId() {
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        return managedHttpClientConnection.getId();
    }

    /**
     * 进行tcp三次握手
     */
    @Override
    public void bind(final Socket socket) throws IOException {
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        managedHttpClientConnection.bind(socket);
    }

    @Override
    public Socket getSocket() {
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        return managedHttpClientConnection.getSocket();
    }

    @Override
    public SSLSession getSSLSession() {
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        return managedHttpClientConnection.getSSLSession();
    }

    @Override
    public boolean isResponseAvailable(final int timeout) throws IOException {
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        return managedHttpClientConnection.isResponseAvailable(timeout);
    }

    /**
     *
     */
    @Override
    public void sendRequestHeader(final HttpRequest request) throws HttpException, IOException {
        // DefaultBHttpClientConnection
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        managedHttpClientConnection.sendRequestHeader(request);
    }

    @Override
    public void sendRequestEntity(final HttpEntityEnclosingRequest request) throws HttpException, IOException {
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        managedHttpClientConnection.sendRequestEntity(request);
    }

    @Override
    public HttpResponse receiveResponseHeader() throws HttpException, IOException {
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        return managedHttpClientConnection.receiveResponseHeader();
    }

    @Override
    public void receiveResponseEntity(final HttpResponse response) throws HttpException, IOException {
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        managedHttpClientConnection.receiveResponseEntity(response);
    }

    @Override
    public void flush() throws IOException {
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        managedHttpClientConnection.flush();
    }

    @Override
    public HttpConnectionMetrics getMetrics() {
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        return managedHttpClientConnection.getMetrics();
    }

    @Override
    public InetAddress getLocalAddress() {
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        return managedHttpClientConnection.getLocalAddress();
    }

    @Override
    public int getLocalPort() {
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        return managedHttpClientConnection.getLocalPort();
    }

    @Override
    public InetAddress getRemoteAddress() {
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        return managedHttpClientConnection.getRemoteAddress();
    }

    @Override
    public int getRemotePort() {
        ManagedHttpClientConnection managedHttpClientConnection = getValidConnection();
        return managedHttpClientConnection.getRemotePort();
    }

    @Override
    public Object getAttribute(final String id) {
        final ManagedHttpClientConnection conn = getValidConnection();
        return conn instanceof HttpContext ? ((HttpContext) conn).getAttribute(id) : null;
    }

    @Override
    public void setAttribute(final String id, final Object obj) {
        final ManagedHttpClientConnection conn = getValidConnection();
        if (conn instanceof HttpContext) {
            ((HttpContext) conn).setAttribute(id, obj);
        }
    }

    @Override
    public Object removeAttribute(final String id) {
        final ManagedHttpClientConnection conn = getValidConnection();
        return conn instanceof HttpContext ? ((HttpContext) conn).removeAttribute(id) : null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CPoolProxy{");
        final ManagedHttpClientConnection conn = getConnection();
        if (conn != null) {
            sb.append(conn);
        } else {
            sb.append("detached");
        }
        sb.append('}');
        return sb.toString();
    }

}
