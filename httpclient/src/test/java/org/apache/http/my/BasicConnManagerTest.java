package org.apache.http.my;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class BasicConnManagerTest {

    public static void main(String[] args) throws Exception {
        //创建HTTP上下文
        HttpClientContext context = HttpClientContext.create();
        //创建HTTP连接管理器
        HttpClientConnectionManager connMrg = new BasicHttpClientConnectionManager();
        HttpHost httpHost = new HttpHost("www.baidu.com", 80, "http://");
        //创建连接路由线路
        HttpRoute route = new HttpRoute(httpHost);
        //请求一个新的Connection,这可能需要处理很长时间。  匿名内部类
        ConnectionRequest connRequest = connMrg.requestConnection(route, null);
        //只等待10秒，有可能抛出InterruptedException, ExecutionException 异常
        HttpClientConnection conn = connRequest.get(10, TimeUnit.SECONDS);
        try {
            System.out.println("conn = " + conn);
            if (conn.isOpen()) {
                //根据Route info建立连接
                connMrg.connect(conn, route, 1000, context);
                //将其标记为路由已完成
                connMrg.routeComplete(conn, route, context);
            }
            // Do useful things with the connection.
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            connMrg.releaseConnection(conn, null, 1, TimeUnit.MINUTES);
        }

    }
}
