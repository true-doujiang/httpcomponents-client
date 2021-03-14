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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

//import org.apache.commons.logging.Log;
//import org.apache.commons.logging.LogFactory;
import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.pool.AbstractConnPool;
import org.apache.http.pool.ConnFactory;
import org.apache.http.pool.PoolEntryCallback;
import org.apache.log4j.Logger;

/**
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.SAFE)
class CPool extends AbstractConnPool<HttpRoute, ManagedHttpClientConnection, CPoolEntry> {

    /*
     * 本类基本没做啥事，逻辑都在 AbstractConnPool中了
     */
    private static final AtomicLong COUNTER = new AtomicLong();

//    private final Log log = LogFactory.getLog(CPool.class);
    private final Logger log = Logger.getLogger(getClass());

    private final long timeToLive;
    private final TimeUnit timeUnit;

    /**
     * default constructor
     */
    public CPool(final ConnFactory<HttpRoute, ManagedHttpClientConnection> connFactory,
            final int defaultMaxPerRoute, final int maxTotal,
            final long timeToLive, final TimeUnit timeUnit) {
        // 父类构造器
        super(connFactory, defaultMaxPerRoute, maxTotal);
        this.timeToLive = timeToLive;
        this.timeUnit = timeUnit;
    }

    /**
     * 创建 CPoolEntry
     *
     * 父类中调用
     */
    @Override
    protected CPoolEntry createEntry(final HttpRoute route, final ManagedHttpClientConnection conn) {
        final String id = Long.toString(COUNTER.getAndIncrement());
        //
        CPoolEntry cPoolEntry = new CPoolEntry(this.log, id, route, conn, this.timeToLive, this.timeUnit);
        log.info("createEntry = " + cPoolEntry);
        return cPoolEntry;
    }

    /**
     *
     */
    @Override
    protected boolean validate(final CPoolEntry entry) {
        ManagedHttpClientConnection connection = entry.getConnection();
        return !connection.isStale();
    }

    @Override
    protected void enumAvailable(final PoolEntryCallback<HttpRoute, ManagedHttpClientConnection> callback) {
        super.enumAvailable(callback);
    }

    @Override
    protected void enumLeased(final PoolEntryCallback<HttpRoute, ManagedHttpClientConnection> callback) {
        super.enumLeased(callback);
    }

}
