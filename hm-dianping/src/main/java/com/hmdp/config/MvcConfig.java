package com.hmdp.config;

import com.hmdp.utils.LoginInceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

import static jdk.nashorn.internal.runtime.regexp.joni.Config.log;

/**
 * @Author: IIE
 * @name: MvcConfig
 * @Date: 2024/4/23
 */
@Configuration
@Slf4j
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    LoginInceptor loginInceptor;


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("拦截器开始执行");
        registry.addInterceptor(new LoginInceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                ).order(1);
        //token刷新的拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
