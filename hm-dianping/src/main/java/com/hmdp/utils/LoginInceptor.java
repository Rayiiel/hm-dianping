package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import lombok.extern.slf4j.Slf4j;
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
import static jdk.nashorn.internal.runtime.regexp.joni.Config.log;

/**
 * @Author: IIE
 * @name: LoginInceptor
 * @Date: 2024/4/23
 */
@Component
public class LoginInceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, java.lang.Object handler) throws Exception {
        //判断要不要拦截
        if(UserHolder.getUser()==null){
            response.setStatus(401);
            return false;
        }else{
            return true;
        }
    }
}
