package org.apache.http.my;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.Date;

public class PoolingConnManagerTest {

    public static void main(String[] args) throws InterruptedException {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        //设置最大连接数不超过200
        //cm.setMaxTotal(200);
        //每个路由默认的连接数20
        //cm.setDefaultMaxPerRoute(20);

        CloseableHttpClient httpclient = HttpClients.custom()
                .setConnectionManager(cm)
                // 禁用重试策略
                .disableAutomaticRetries()
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
//                                .setConnectionRequestTimeout(2000)
                                .build();

        String[] urisToGet = {
//                "http://www.baidu.com/",
                "http://localhost:8080//test/get?userName=333",
//                "http://www.domain3cc.com/",
//                "http://www.domain4dd.com/"
        };

        GetThread[] threads = new GetThread[urisToGet.length];
        for (int i = 0; i < threads.length; i++) {
            for (int j = 0; j < 20; j++) {
                HttpGet httpGet = new HttpGet(urisToGet[i]);
                httpGet.setConfig(requestConfig);

                GetThread getThread = new GetThread(httpclient, httpGet);
                getThread.start();
                //threads[i] = new GetThread(httpclient, httpGet);
            }
        }

        while (true) {
            Thread.sleep(1000);
            System.out.println("threads = " + new Date().getSeconds());
        }
        // start the threads
//        for (int j = 0; j < threads.length; j++) {
//            threads[j].start();
//        }
//
//        // join the threads
//        for (int j = 0; j < threads.length; j++) {
//            threads[j].join();
//        }
    }
}


class GetThread extends Thread {

    private final CloseableHttpClient httpClient;
    private final HttpContext context;
    private final HttpGet httpget;

    public GetThread(CloseableHttpClient httpClient, HttpGet httpget) {
        this.httpClient = httpClient;
        this.context = HttpClientContext.create();
        this.httpget = httpget;
    }

    @Override
    public void run() {
        try {
            CloseableHttpResponse response = httpClient.execute(httpget, context);
            try {
                HttpEntity entity = response.getEntity();
                String s = EntityUtils.toString(entity);
                System.out.println(Thread.currentThread().getName() + " s = " + s);
            } finally {
                response.close();
            }
        } catch (ClientProtocolException ex) {
            ex.printStackTrace();
            // Handle protocol errors
        } catch (IOException ex) {
            ex.printStackTrace();
            // Handle I/O errors
        }
    }

}