package com.qingledger.utils;

import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;

/**
 * IP 工具类
 */
@Slf4j
@Component
public class IpUtil {

    private static Searcher searcher;

    static {
        try {
            ClassPathResource resource = new ClassPathResource("ip2region.xdb");
            byte[] cBuff = StreamUtils.copyToByteArray(resource.getInputStream());
            searcher = Searcher.newWithBuffer(cBuff);
        } catch (IOException e) {
            log.warn("IP2Region 数据库加载失败,将使用默认值", e);
            searcher = null;
        }
    }

    /**
     * 提取 IP 段(前三个段)
     * 例如: 192.168.1.100 -> 192.168.1
     */
    public static String extractIpSegment(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return "";
        }
        String[] parts = ipAddress.split("\\.");
        if (parts.length >= 3) {
            return parts[0] + "." + parts[1] + "." + parts[2];
        }
        return ipAddress;
    }

    /**
     * 查询 IP 归属地城市
     * 返回格式: 中国|0|北京|北京市|联通
     * 提取城市: 北京
     */
    public static String getCity(String ipAddress) {
        if (searcher == null || ipAddress == null || ipAddress.isEmpty()) {
            return "未知";
        }

        try {
            String region = searcher.search(ipAddress);
            if (region != null) {
                String[] parts = region.split("\\|");
                if (parts.length >= 3) {
                    return parts[2]; // 城市
                }
            }
        } catch (Exception e) {
            log.warn("IP 归属地查询失败: {}", ipAddress, e);
        }

        return "未知";
    }

    /**
     * 判断两个 IP 是否在同一城市
     */
    public static boolean isSameCity(String ip1, String ip2) {
        String city1 = getCity(ip1);
        String city2 = getCity(ip2);
        return city1.equals(city2);
    }
}
