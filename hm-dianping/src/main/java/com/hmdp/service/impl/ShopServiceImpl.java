package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

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
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    public Result queryById(Long id){

        //1.从Redis中查询商铺缓存
        String shopJSON = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //1.1 若存在，返回查到的商铺信息
        if(StrUtil.isNotBlank(shopJSON)){
            Shop shop= JSONUtil.toBean(shopJSON,Shop.class);
            return Result.ok(shop);
        }

        //1.2 若不存在，访问数据库，查询对应的结果
        Shop shop1 = query().eq("id", id).one();

        if(shop1!=null){
            //1.3 数据库中有数据，返回数据，并写入Redis当中
            String shop1JSON = JSONUtil.toJsonStr(shop1);
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,shop1JSON,CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.ok(shop1);
        }

        //1.4 数据库中没有数据，返回Result.fail()
        return Result.fail("404NotFound");
    };
}
