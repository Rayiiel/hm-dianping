package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 热点数据预热
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //1.查询所有的热点数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.保存到Redis中完成数据预热
        String key=CACHE_SHOP_KEY+id;
        String shopJson = JSONUtil.toJsonStr(redisData);
        stringRedisTemplate.opsForValue().set(key,shopJson);
    }

    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    public Result queryById(Long id) throws InterruptedException {
        //1.缓存穿透 Shop shop=queryWithPassThrough(id);

        //2.缓存击穿 Shop shop=queryWithMutex(id);

        //3.缓存击穿-设置过期时间
        //Shop shop = queryWithExpireTime(id);

        Shop shop=queryWithPassThrough(id);
        if(shop==null){
            return Result.fail("数据不存在");
        }else{
            return Result.ok(shop);
        }
    }

    /**
     * 保存空值的缓存穿透的解决方案
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id){
        //1.从Redis中查询商铺缓存
        String shopJSON = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //1.1.1 若存在，返回查到的商铺信息
        //StrUtil中只有是真正字符串的时候才是ture
        if(StrUtil.isNotBlank(shopJSON)){
            Shop shop= JSONUtil.toBean(shopJSON,Shop.class);
            return shop;
        }
        //1.1.2 判断当前字符串是否为空值
        if("".equals(shopJSON)){
            return null;
        }
        //1.1.3 为null时，才进行数据库的搜索，并将“”写入Redis当中，这里可能出现空指针异常，要注意空指针异常问题

        //1.2 若不存在，访问数据库，查询对应的结果
        Shop shop1 = query().eq("id", id).one();

        if(shop1!=null){
            //1.3 数据库中有数据，返回数据，并写入Redis当中
            String shop1JSON = JSONUtil.toJsonStr(shop1);
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,shop1JSON,CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop1;
        }else{
            //进行缓存穿透处理
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
        }
        //1.4 数据库中没有数据，返回Result.fail()
        return null;
    }


    /**
     * 基于互斥锁的缓存穿透解决方案
     * @param id
     * @return
     * @throws InterruptedException
     */
    public Shop queryWithMutex(Long id) throws InterruptedException {
        //1.从Redis中查询商铺缓存
        String shopJSON = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //1.1.1 若存在，返回查到的商铺信息
        //StrUtil中只有是真正字符串的时候才是ture
        if(StrUtil.isNotBlank(shopJSON)){
            Shop shop= JSONUtil.toBean(shopJSON,Shop.class);
            return shop;
        }
        //1.1.2 判断当前字符串是否为空值
        if(shopJSON!=null){
            return null;
        }
        //1.1.3 只有为”“时候，才进行数据库的搜索

        //2.实现缓存重建
        //2.1 获取互斥锁
        String lockKey=LOCK_SHOP_KEY+id;
        Shop shop1 = null;
        try {
            boolean isLock = getLock(lockKey);
            while(!isLock){
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //2.2 访问数据库，查询对应的结果
            shop1 = query().eq("id", id).one();

            //1.4 数据库中没有数据，写入Redis null
            if(shop1==null){
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;

            }
            //1.3 数据库中有数据，返回数据，并写入Redis当中
            String shop1JSON = JSONUtil.toJsonStr(shop1);
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,shop1JSON,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            unLock(lockKey);
        }
        return shop1;

    }

    /**
     * 获取锁和释放锁
     * @param key
     * @return
     */
    private boolean getLock(String key){
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key,"1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.and(result);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     * 逻辑过期时间解决缓存击穿的问题
     * @param id
     * @return
     * @throws InterruptedException
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    public Shop queryWithExpireTime(Long id) throws InterruptedException {
        //1.从Redis中查询商铺缓存
        String shopJSON = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //2.若不存在，缓存空
        if(StrUtil.isBlank(shopJSON)){
            return null;
        }

        //3.若存在，数据反序列化
        RedisData redisData = JSONUtil.toBean(shopJSON, RedisData.class);
        JSONObject data=(JSONObject)redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);

        //4.判断是否过期，过期的话，开启一个线程去更新
        if(!redisData.getExpireTime().isAfter(LocalDateTime.now())){
            //4.1 尝试获取锁
            String lockKey=LOCK_SHOP_KEY+id;
            try {
                boolean isLock = getLock(lockKey);
                if(isLock){
                    //4.2 开启一个线程去更新逻辑过期时间
                    CACHE_REBUILD_EXECUTOR.submit(()->{
                        try {
                            this.saveShop2Redis(id,20L);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }finally{
                //4.3 释放锁
                unLock(lockKey);
            }
        }

        //5. 返回redisData中的店铺信息
        return shop;
    }

    /**
     * 更新数据库的数据
     * @param shop
     * @return
     */
    @Transactional
    public Result update(Shop shop){
        Long id =shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空值");
        }
        //1.更新数据库信息
        boolean updateResult = updateById(shop);
        //2.删除缓存
        if(updateResult){
            stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        }
        return Result.ok();
    }
}
