package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.omg.CORBA.Object;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static jdk.nashorn.internal.runtime.regexp.joni.Config.log;

/**
 * @Author: IIE
 * @name: RefreshTokenInterceptor
 * @Date: 2024/4/24
 */
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {

    //不能使用Autowired进行注入，使用构造函数注入
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     *自己写的拦截器中没有写Override注解，导致preHandle方法中的代码一直不执行，afterCompletion也是，必须要写Override注解
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, java.lang.Object handler) throws Exception {
        System.out.println("拦截器执行");
        //1.获取请求头中的token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            return true;
        }
        String tokenKey=RedisConstants.LOGIN_USER_KEY+token;
        //2.获取redis中的用户
        Map<java.lang.Object, java.lang.Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);

        //3.判断当前用户是否存在
        if(userMap.isEmpty()){
            return true;
        }
        //4.当前用户存在，保存在当前线程下
        //4.1 将userMap转换为UserDTO
        UserDTO userDTO= BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);

        UserHolder.saveUser(userDTO);

        //5.刷新token的有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, java.lang.Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }

}
