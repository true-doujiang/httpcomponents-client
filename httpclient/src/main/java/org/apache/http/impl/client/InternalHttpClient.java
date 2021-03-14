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

package org.apache.http.impl.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

//import org.apache.commons.logging.Log;
//import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthState;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.Configurable;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.HttpClientParamConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Lookup;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpParamsNames;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;
import org.apache.log4j.Logger;

/**
 * Internal class.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
@SuppressWarnings("deprecation")
class InternalHttpClient extends CloseableHttpClient implements Configurable {

//    private final Log log = LogFactory.getLog(getClass());
    private final Logger log = Logger.getLogger(getClass());

    // 执行链的下一个之心器
    private final ClientExecChain execChain;
    // PoolingHttpClientConnectionManager
    private final HttpClientConnectionManager connManager;

    // DefaultRoutePlanner
    private final HttpRoutePlanner routePlanner;

    private final Lookup<CookieSpecProvider> cookieSpecRegistry;
    private final Lookup<AuthSchemeProvider> authSchemeRegistry;

    private final CookieStore cookieStore;
    private final CredentialsProvider credentialsProvider;
    //
    private final RequestConfig defaultConfig;
    private final List<Closeable> closeables;

    /**
     * default constructor
     *
     * @see org.apache.http.impl.client.HttpClientBuilder#build 调用
     */
    public InternalHttpClient(
            final ClientExecChain execChain,
            final HttpClientConnectionManager connManager,
            final HttpRoutePlanner routePlanner,
            final Lookup<CookieSpecProvider> cookieSpecRegistry,
            final Lookup<AuthSchemeProvider> authSchemeRegistry,
            final CookieStore cookieStore,
            final CredentialsProvider credentialsProvider,
            final RequestConfig defaultConfig,
            final List<Closeable> closeables) {
        super();
        Args.notNull(execChain, "HTTP client exec chain");
        Args.notNull(connManager, "HTTP connection manager");
        Args.notNull(routePlanner, "HTTP route planner");

        this.execChain = execChain;
        this.connManager = connManager;

        this.routePlanner = routePlanner;
        this.cookieSpecRegistry = cookieSpecRegistry;
        this.authSchemeRegistry = authSchemeRegistry;
        this.cookieStore = cookieStore;
        this.credentialsProvider = credentialsProvider;
        this.defaultConfig = defaultConfig;
        this.closeables = closeables;
    }

    /**
     * 解析出一个 HttpRoute implements RouteInfo
     */
    private HttpRoute determineRoute(
            final HttpHost target,
            final HttpRequest request,
            final HttpContext context) throws HttpException {
        HttpHost host = target;
        if (host == null) {
            host = (HttpHost) request.getParams().getParameter(ClientPNames.DEFAULT_HOST);
        }
        //
        HttpRoute httpRoute = this.routePlanner.determineRoute(host, request, context);
        return httpRoute;
    }


    /**
     * 给httpClientContext底册的map中添加
     */
    private void setupContext(final HttpClientContext context) {
        if (context.getAttribute(HttpClientContext.TARGET_AUTH_STATE) == null) {
            context.setAttribute(HttpClientContext.TARGET_AUTH_STATE, new AuthState());
        }
        if (context.getAttribute(HttpClientContext.PROXY_AUTH_STATE) == null) {
            context.setAttribute(HttpClientContext.PROXY_AUTH_STATE, new AuthState());
        }
        if (context.getAttribute(HttpClientContext.AUTHSCHEME_REGISTRY) == null) {
            context.setAttribute(HttpClientContext.AUTHSCHEME_REGISTRY, this.authSchemeRegistry);
        }
        if (context.getAttribute(HttpClientContext.COOKIESPEC_REGISTRY) == null) {
            context.setAttribute(HttpClientContext.COOKIESPEC_REGISTRY, this.cookieSpecRegistry);
        }
        if (context.getAttribute(HttpClientContext.COOKIE_STORE) == null) {
            context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
        }
        if (context.getAttribute(HttpClientContext.CREDS_PROVIDER) == null) {
            context.setAttribute(HttpClientContext.CREDS_PROVIDER, this.credentialsProvider);
        }
        if (context.getAttribute(HttpClientContext.REQUEST_CONFIG) == null) {
            context.setAttribute(HttpClientContext.REQUEST_CONFIG, this.defaultConfig);
        }
    }

    /**
     * 创建一个HttpContext
     * 创建一个HttRoute
     * execChain 真正干活的
     */
    @Override
    protected CloseableHttpResponse doExecute(
            final HttpHost target,
            final HttpRequest request,
            final HttpContext context) throws IOException, ClientProtocolException {
        Args.notNull(request, "HTTP request");

        //
        HttpExecutionAware execAware = null;
        if (request instanceof HttpExecutionAware) {
            execAware = (HttpExecutionAware) request;
        }

        try {
            //
            final HttpRequestWrapper requestWrapper = HttpRequestWrapper.wrap(request, target);
            // 为本次请求创建一个httpContext 底层就是个ConcurrentHashMap
            HttpContext httpContext = context != null ? context : new BasicHttpContext();
            final HttpClientContext httpClientContext = HttpClientContext.adapt(httpContext);

            RequestConfig config = null;
            if (request instanceof Configurable) {
                config = ((Configurable) request).getConfig();
            }

            if (config == null) {
                final HttpParams params = request.getParams();
                if (params instanceof HttpParamsNames) {
                    if (!((HttpParamsNames) params).getNames().isEmpty()) {
                        config = HttpClientParamConfig.getRequestConfig(params, this.defaultConfig);
                    }
                } else {
                    config = HttpClientParamConfig.getRequestConfig(params, this.defaultConfig);
                }
            }

            // 一路走下来 config = null
            if (config != null) {
                httpClientContext.setRequestConfig(config);
            }

            // 给httpClientContext底册的map中添加
            setupContext(httpClientContext);
            //
            final HttpRoute route = determineRoute(target, requestWrapper, httpClientContext);

            return this.execChain.execute(route, requestWrapper, httpClientContext, execAware);
        } catch (final HttpException httpException) {
            throw new ClientProtocolException(httpException);
        }
    }

    @Override
    public RequestConfig getConfig() {
        return this.defaultConfig;
    }

    @Override
    public void close() {
        if (this.closeables != null) {
            for (final Closeable closeable: this.closeables) {
                try {
                    closeable.close();
                } catch (final IOException ex) {
                    this.log.error(ex.getMessage(), ex);
                }
            }
        }
    }

    @Override
    public HttpParams getParams() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ClientConnectionManager getConnectionManager() {

        return new ClientConnectionManager() {

            @Override
            public void shutdown() {
                connManager.shutdown();
            }

            @Override
            public ClientConnectionRequest requestConnection(
                    final HttpRoute route, final Object state) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void releaseConnection(
                    final ManagedClientConnection conn,
                    final long validDuration, final TimeUnit timeUnit) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SchemeRegistry getSchemeRegistry() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void closeIdleConnections(final long idletime, final TimeUnit timeUnit) {
                connManager.closeIdleConnections(idletime, timeUnit);
            }

            @Override
            public void closeExpiredConnections() {
                connManager.closeExpiredConnections();
            }

        };

    }

}
