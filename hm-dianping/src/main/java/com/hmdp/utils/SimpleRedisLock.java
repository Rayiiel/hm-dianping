package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @Author: IIE
 * @name: SimpleRedisLock
 * @Date: 2024/5/6
 */
public class SimpleRedisLock implements ILock {

    private StringRedisTemplate stringRedisTemplate;

    private String key;

    private String LOCK_PROFIX="lock:";

    private String ID_PROFIX= UUID.randomUUID().toString(true)+"-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String key) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.key = key;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String id = ID_PROFIX+Thread.currentThread().getId();
        Boolean success = stringRedisTemplate
                .opsForValue()
                .setIfAbsent(LOCK_PROFIX+key, id , timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock(){
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(LOCK_PROFIX+key),
                ID_PROFIX+Thread.currentThread().getId());
    }
//    @Override
//    public void unLock() {
//        String nowId = ID_PROFIX+Thread.currentThread().getId();
//        //如何将以下的语句保证原子性
//        //通过Lua脚本实现多条Redis语句的一致性问题
//        if(nowId.equals(stringRedisTemplate.opsForValue().get(LOCK_PROFIX+key))){
//            stringRedisTemplate.delete(LOCK_PROFIX+key);
//        }
//    }
}
