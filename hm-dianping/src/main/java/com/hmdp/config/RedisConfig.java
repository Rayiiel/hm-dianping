package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: IIE
 * @name: RedisConfig
 * @Date: 2024/5/7
 */
@Configuration
public class RedisConfig {
    //通过无参构造器来生成一个RedissonClient
    @Bean
    public RedissonClient redissonClient(){
        //配置类
        Config config = new Config();
        //添加redis地址,这里添加了单点地址
        config.useSingleServer().setAddress("redis://localhost:6379").setPassword("123456");
        //创建客户端
        return Redisson.create(config);
    }
}
