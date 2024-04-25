package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    RedisTemplate redisTemplate;

    public Result queryTypeList(){

        //1.查询Redis中是否有对应的数据
        Long length=redisTemplate.opsForList().size(CACHE_SHOP_TYPE_KEY);
        //2.如果有的话，get并返回
        if(length>0){
            List shopTypelists = redisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, length - 1);
            return Result.ok(shopTypelists);
        }
        //3.没有后从数据库中查找数据
        List<ShopType> list = query().orderByAsc("sort").list();
        //4.没有返回查询失败
        if(list.size()==0){
            return Result.fail("404NoFound");
        }
        //5.有的话存储在Redis当中，返回
        redisTemplate.opsForList().leftPushAll(CACHE_SHOP_TYPE_KEY,list);
        return Result.ok(list);
    }
}
