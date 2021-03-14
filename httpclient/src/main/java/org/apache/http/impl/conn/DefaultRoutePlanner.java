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

import java.net.InetAddress;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolException;
import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.conn.UnsupportedSchemeException;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;

/**
 * Default implementation of an {@link HttpRoutePlanner}. It will not make use of
 * any Java system properties, nor of system or browser proxy settings.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class DefaultRoutePlanner implements HttpRoutePlanner {

    // 只是根据HttpHost对象中的  http/https 返回一个port
    private final SchemePortResolver schemePortResolver;

    /**
     * default constructor
     */
    public DefaultRoutePlanner(final SchemePortResolver schemePortResolver) {
        super();
        this.schemePortResolver = schemePortResolver != null
                ? schemePortResolver : DefaultSchemePortResolver.INSTANCE;
    }

    /**
     * 解析出一个route
     */
    @Override
    public HttpRoute determineRoute(
            final HttpHost host,
            final HttpRequest request,
            final HttpContext context) throws HttpException {

        Args.notNull(request, "Request");
        if (host == null) {
            throw new ProtocolException("Target host is not specified");
        }

        final HttpClientContext clientContext = HttpClientContext.adapt(context);
        final RequestConfig config = clientContext.getRequestConfig();
        final InetAddress localAddress = config.getLocalAddress();

        HttpHost proxy = config.getProxy();
        if (proxy == null) {
            // 空实现 返回null
            proxy = determineProxy(host, request, context);
        }

        final HttpHost target;
        if (host.getPort() <= 0) {
            try {
                String hostName = host.getHostName();
                String schemeName = host.getSchemeName();
                // 解析协议的端口号
                int resolve = this.schemePortResolver.resolve(host);
                target = new HttpHost(hostName, resolve, schemeName);
            } catch (final UnsupportedSchemeException ex) {
                throw new HttpException(ex.getMessage());
            }
        } else {
            target = host;
        }

        final boolean secure = target.getSchemeName().equalsIgnoreCase("https");

        if (proxy == null) {
            // http
            return new HttpRoute(target, localAddress, secure);
        } else {
            // https
            return new HttpRoute(target, localAddress, proxy, secure);
        }

//        return proxy == null
//                        ? new HttpRoute(target, local, secure)
//                        : new HttpRoute(target, local, proxy, secure);
    }

    /**
     * This implementation returns null.
     *
     * @throws HttpException may be thrown if overridden
     */
    protected HttpHost determineProxy(
            final HttpHost target,
            final HttpRequest request,
            final HttpContext context) throws HttpException {
        return null;
    }

}
