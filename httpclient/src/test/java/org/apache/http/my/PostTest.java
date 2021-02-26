package org.apache.http.my;

import com.alibaba.fastjson.JSON;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PostTest {

    public static void main(String[] args) throws IOException {
        System.out.println("======================================");
        // 创建默认的httpClient实例
        CloseableHttpClient client = HttpClients.createDefault();
        // 创建post请求
        HttpPost post = new HttpPost("http://localhost:8765/user//direct/airinfo/list");
        // 设置请求参数
        Map<String, String> map = new HashMap<String, String>();
        map.put("userName", "张三");
        map.put("realName", "zhangsan");
        StringEntity stringEntity = new StringEntity(JSON.toJSONString(map), ContentType.APPLICATION_JSON);
        stringEntity.setContentEncoding("utf-8");
        post.setEntity(stringEntity);
        post.releaseConnection();
        // 执行请求
        CloseableHttpResponse response = client.execute(post);
        // 获取结果
        if (response.getStatusLine().getStatusCode()!= HttpStatus.SC_OK){
            System.out.println("请求出错：状态为："+response.getStatusLine().getStatusCode());
        }
        System.out.println(EntityUtils.toString(response.getEntity()));
        // 释放链接
        response.close();
    }
}
