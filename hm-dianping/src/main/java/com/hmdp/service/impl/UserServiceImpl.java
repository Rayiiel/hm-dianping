package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    public Result sendCode(String phone, HttpSession session){
        //1.校验手机号，不符合，抛出异常并返回
        if(!RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式不正确，请重新输入");
        }
        //2.生成验证码
        int[] code = RandomUtil.randomInts(6);
        //3.保存验证码到当前的session当中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code.toString(),LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //4.发送验证码
        log.info("发送验证码成功，验证码为{}",code);
        //5.返回
        return Result.ok();
    }

    /**
     * 用户登录验证
     * @param loginForm
     * @param session
     * @return
     */
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone=loginForm.getPhone();
        //1.验证手机号是否正确
        if(!RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式不正确，请重新输入");
        }
        //2.验证当前验证码和本地的是否一致
        String cacheCode=stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code=loginForm.getCode();
        if(code!=null&&code.equals(cacheCode)){
            return Result.fail("验证码不正确");
        }
        //3.按照手机号查找用户，找到登陆成功
        //query()父类的方法 select,传入this.getMapper的UserMapper，eq就是where后面的语句,one()是返回一个，list是返回多个
        User user=query().eq("phone",phone).one();
        //4.没有找到需要注册用户，将手机号的密码保存到数据库中
        if(user==null){
            user=createUserWithPhone(loginForm.getPhone());
        }
        // 5.生成token
        String token= UUID.randomUUID().toString(true);
        // 6.保存用户信息
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        /**
         * stringRedisTemplate.opsForHash().put(token,userDTO.phone,phone);
         * put方法三个参数说明：key,hashkey,hashvalue
         */

        String tokenKey=LOGIN_USER_KEY+token;
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //存储到redis当中
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //设置token的有效期，应该是最近一次访问之后的有效期--应该在每次拦截器校验的时候更新有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);

        return Result.ok(token);
    }

    /**
     * 新建并用户信息
     */
    private User createUserWithPhone(String phone){
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
