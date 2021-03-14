package org.apache.http.my;

import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.Configurable;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import java.io.IOException;

public class GetTest {

    private final static Logger log = Logger.getLogger(GetTest.class);

    public static void main(String[] args) {
        // InternalHttpClient
//        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpClient httpClient = HttpClients.custom()
                .disableAutomaticRetries()
                .disableAuthCaching()
                .disableRedirectHandling()
                .disableDefaultUserAgent()

                //.setHttpProcessor(请求响应拦截器集合)  不要自己设置, build()方法中会添加的
                .build();

        if (httpClient instanceof Configurable) {
            RequestConfig config = ((Configurable) httpClient).getConfig();
            System.out.println("main config = " + config);
        }

        try {
//            HttpGet httpGet = new HttpGet("http://www.baidu.com/");
            HttpGet httpGet = new HttpGet("http://localhost:8080/test/get?userName=333");
            httpGet.setHeader("testHeader", "testValue");

            // 小写http服务端就会报错
            httpGet.setProtocolVersion(new ProtocolVersion("HTTP", 1, 1));

            // 每次返回的是一个新对象
            RequestLine requestLine = httpGet.getRequestLine();
            ProtocolVersion protocolVersion = httpGet.getProtocolVersion();
            RequestConfig config = httpGet.getConfig();



            // HttpResponseProxy
            CloseableHttpResponse response = null;
            response = httpClient.execute(httpGet);
            try {
                // entity: ResponseEntityProxy
                HttpEntity entity = response.getEntity();
                System.out.println(response.getStatusLine());
                if (entity != null) {
                    System.out.println("内容长度：" + entity.getContentLength());
                    //System.out.println("响应内容：" + EntityUtils.toString(entity));
                }
            } finally {
                // 不要小看这个方法哦
                response.close();
            }

            for (int i = 0; i < 15; i++) {
                Thread.sleep(1000);
                System.out.println("i = " + i);
            }

            new Thread(() -> {
                int i = 0;
                while (true) {
                    System.out.println("i = " + i);
                    i++;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            CloseableHttpResponse response2 = null;
            response2 = httpClient.execute(httpGet);
            try {
                HttpEntity entity2 = response2.getEntity();
                System.out.println(response2.getStatusLine());
                if (entity2 != null) {
                    System.out.println("内容长度2：" + entity2.getContentLength());
                    System.out.println("响应内容2：" + EntityUtils.toString(entity2));
                }
            } finally {
                response2.close();
            }



        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
