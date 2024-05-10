package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    ShopServiceImpl shopService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    private ExecutorService es= Executors.newFixedThreadPool(500);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void save() throws InterruptedException {
        shopService.saveShop2Redis(1L,15L);
    }

    @Test
    public void testIdWorker() throws InterruptedException {
        CountDownLatch latch=new CountDownLatch(300);
        Runnable task=()->{
            for (int i = 0; i <100 ; i++) {
                long id=redisIdWorker.nextId("order");
                System.out.println(id);
            }
            latch.countDown();
        };
        long begin=System.currentTimeMillis();
        for (int i = 0; i <300 ; i++) {
            es.submit(task);
        }
        latch.await();
        long end=System.currentTimeMillis();
        System.out.println("time="+(end-begin));
    }

    @Test
    void loadShopData(){
        //1.查询到所有的店铺信息
        List<Shop> list = shopService.list();
        //2.将店铺信息以类型进行分组
        Map<Long, List<Shop>> maps = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.将店铺的地址位置信息分批存入Redis当中
        //对map数据结构进行遍历
        for(Map.Entry<Long,List<Shop>> entry : maps.entrySet()){
            Long typeId = entry.getKey();
            List<Shop> shops = entry.getValue();
            if(shops==null||shops.isEmpty()){
                continue;
            }
            //写入数据库
            for (Shop shop:shops) {
                stringRedisTemplate.opsForGeo().add(SHOP_GEO_KEY+typeId,
                        new Point(shop.getX(),shop.getY()),shop.getId().toString());
            }
        }
    }

}
