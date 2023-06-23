package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @project: 全局唯一id生成器
 * @author: mikudd3
 * @version: 1.0
 */
@Component
public class RedisIdWorker {
    //开始时间戳
    private static final long BEGIN_TIMESTAMP = 1674086400L;

    //序列号位数
    private static final int COUNT_BITS = 32;


    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime time = LocalDateTime.now();
        long nowSecond = time.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        //2.生成序列号,redis自增长,redis单个key自增长有上限，2的64次方
        //2.1获取当前日期，精确到天
        String date = time.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //3.拼接并返回,不能使用字符串方式拼接
        return timestamp << COUNT_BITS | count;//先向左移32位，那么低32位全为0，跟序列号进行或操作
    }

}
