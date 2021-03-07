package org.apache.http.my;

import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
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
                .build();

        log.warn("httpClient: " + httpClient);
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

            CloseableHttpResponse response = null;
            response = httpClient.execute(httpGet);
            try {
                HttpEntity entity = response.getEntity();
                System.out.println(response.getStatusLine());
                if (entity != null) {
                    System.out.println("内容长度：" + entity.getContentLength());
                    System.out.println("响应内容：" + EntityUtils.toString(entity));
                }
            } finally {
                response.close();
            }
        } catch (IOException e) {
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
