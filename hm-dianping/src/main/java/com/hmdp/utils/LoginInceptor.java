package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.omg.CORBA.Object;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @Author: IIE
 * @name: LoginInceptor
 * @Date: 2024/4/23
 */
public class LoginInceptor implements HandlerInterceptor {
    //不能使用Autowired进行注入，使用构造函数注入
    private StringRedisTemplate stringRedisTemplate;

    public LoginInceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取请求头中的token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            response.setStatus(401);
            return false;
        }
        String tokenKey=RedisConstants.LOGIN_USER_KEY+token;
        //2.获取redis中的用户
        Map<java.lang.Object, java.lang.Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);

        //3.判断当前用户是否存在
        if(userMap==null){
            //返回401状态码
            response.setStatus(401);
            return false;
        }
        //4.当前用户存在，保存在当前线程下
        //4.1 将userMap转换为UserDTO
        UserDTO userDTO=BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);

        UserHolder.saveUser(userDTO);

        //5.刷新token的有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }


    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
