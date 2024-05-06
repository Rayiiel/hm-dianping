package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @Author: IIE
 * @name: RedisWorker
 * @Date: 2024/5/5
 */
@Component
public class RedisIdWorker {
    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP=1640995200L;
    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 需要使用构造函数进行属性数值的注入
     * @param stringRedisTemplate
     */
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix){
        //1.生成时间戳
        long timeStamp=LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)-BEGIN_TIMESTAMP;
        //2.生成序列号
        /**
         * 相同的业务有可能的数据量超过32位的容量，因此需要进行处理，拼接每天的字符串，从这里开始自增
         */
        //2.1 获取当前的日期
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2 生成序列号
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //3.拼接并返回
        return timeStamp<<COUNT_BITS|count;


    }
}
