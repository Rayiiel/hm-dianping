package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import io.lettuce.core.XReadArgs;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService iSeckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private IVoucherOrderService proxy;

    //关于判断是否具有购买资格的Lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT=new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    //线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR= Executors.newSingleThreadExecutor();
    //类初始化之后就开始执行任务
    @PostConstruct  //该注解表示在类初始化完毕再去执行
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            String queueName="stream.orders";
            while(true){
                try {
                    //1.获取消息队列中的订单信息
                    //XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2.获取不成功，重试或者返回
                    if(list==null||list.isEmpty()){
                        continue;
                    }
                    //3.获取成功，写入数据库
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    proxy.createVoucherOrder(voucherOrder);
                    //4.ack确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                   log.error("处理订单异常：",e);
                    try {
                        handlePengdingList();
                    } catch (InterruptedException interruptedException) {
                        interruptedException.printStackTrace();
                    }
                }
            }
        }
    }

    private void handlePengdingList() throws InterruptedException {
        String queueName="stream.orders";
        while(true){
            try {
                //1.获取pengding-list消息队列中的订单信息
                //XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(queueName, ReadOffset.from("0"))
                );
                //2.获取不成功，重试或者返回
                if(list==null||list.isEmpty()){
                    //如果获取失败，说明pengding-list没有异常消息，跳出循环
                    break;
                }
                //3.获取成功，写入数据库
                MapRecord<String, Object, Object> record = list.get(0);
                Map<Object, Object> value = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                proxy.createVoucherOrder(voucherOrder);
                //4.ack确认
                stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
            } catch (Exception e) {
                log.error("处理订单异常：",e);
                Thread.sleep(10);
            }
        }
    }
    //基于阻塞队列的线程任务
//    //阻塞队列
//    private BlockingQueue<VoucherOrder> orderTasks=new ArrayBlockingQueue<>(1024*1024);
//    /**
//     * 线程任务
//     */
//    private class VoucherOrderHandler implements Runnable{
//
//        @Override
//        public void run() {
//            while(true){
//                //1.获取队列中的订单信息
//                try {
//                    //take函数获取和删除该队列的头部，如果需要则等待直到元素可用，所以没有元素，会直接阻塞在这里
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    //2.创建订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                   log.error("处理订单异常：",e);
//                }
//
//            }
//        }
//    }

    /**
     * 处理订单写入数据库
     * @param voucherOrder
     * @return
     */
    private Result handleVoucherOrder(VoucherOrder voucherOrder) {
        long userId=voucherOrder.getUserId();
        //1.获取锁
        RLock lock = redissonClient.getLock("lock:order" + userId);
        boolean isLock = lock.tryLock();
        //2.获取锁失败，下单失败
        if(!isLock){
            //获取锁失败，返回错误或重试
            log.error("不允许重复下单");
            return null;
        }
        try {
            //该线程是子线程，无法在该处获取到事务的对象，因为需要提前获取
            //IVoucherOrderService proxy=(IVoucherOrderService) AopContext.currentProxy();
            proxy.createVoucherOrder(voucherOrder);
            return Result.ok();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 基于stream消息队列的优惠券秒杀业务逻辑
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId){
        //1.执行lua脚本
        Long userId= UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),userId.toString(),String.valueOf(orderId));
        //2.判断结果是否为0
        //3.如果不为0，返回异常信息
        int r = result.intValue();
        if(r!=0){
            if(r==1){
                return Result.fail("库存不足");
            }else if(r==2){
                return Result.fail("每人只限购一单");
            }
        }
        //4.lua脚本中已经将用户的订单信息加入到了消息队列中
        //5.获取代理对象
        proxy=(IVoucherOrderService) AopContext.currentProxy();
        //5.返回订单id

        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder){
        //5.一人一单
        long userId= voucherOrder.getUserId();
        long voucherId=voucherOrder.getVoucherId();
        //5.1 查询订单
        int count=query().eq("user_id",userId).eq("voucher_id",voucherId).count();
        //5.2 判断是否存在
        if(count>0){
            log.error("用户已经购买过一次了");
            return;
        }
        //6.扣减库存
//        boolean success = iSeckillVoucherService.update()
//                .setSql("stock=stock-1")
//                .eq("voucher_id", voucherId)
//                .eq("stock",stock)
//                .update();
        //解决效率低的问题
        boolean success = iSeckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();
        if(!success){
            log.error("优惠券已经抢完了");
            return;
        }

        save(voucherOrder);
    }

//    /**
//     * 基于阻塞队列的秒杀业务逻辑
//     * @param voucherId
//     * @return
//     */
//    @Override
//    public Result seckillVoucher(Long voucherId){
//        //1.执行lua脚本
//        Long userId= UserHolder.getUser().getId();
//        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
//                Collections.emptyList(),voucherId.toString(),userId.toString());
//        //2.判断结果是否为0
//        //3.如果不为0，返回异常信息
//        int r = result.intValue();
//        if(r!=0){
//            if(r==1){
//                return Result.fail("库存不足");
//            }else if(r==2){
//                return Result.fail("每人只限购一单");
//            }
//        }
//        //4.为0，将信息添加到阻塞队列里面
//        //4.生成订单
//        VoucherOrder voucherOrder=new VoucherOrder();
//        //4.1 订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //4.2 用户id
//        voucherOrder.setUserId(userId);
//        //4.3 代金券id
//        voucherOrder.setVoucherId(voucherId);
//        orderTasks.add(voucherOrder);
//        //5.获取代理对象
//        proxy=(IVoucherOrderService) AopContext.currentProxy();
//        //5.返回订单id
//
//        return Result.ok(orderId);
//    }

//    /**
//     * 不同的业务秒杀逻辑
//     * @param voucherId
//     * @return
//     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠券
//        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否开始
//        if(LocalDateTime.now().isBefore(voucher.getBeginTime())){
//            return Result.fail("秒杀还未开始");
//        }
//        //3.判断秒杀是否已经结束
//        if(LocalDateTime.now().isAfter(voucher.getEndTime())){
//            return Result.fail("秒杀已经结束");
//        }
//        //4.判断库存是否充足
//        int stock=voucher.getStock();
//        if(stock<=0){
//            return Result.fail("优惠券已经被抢完");
//        }
//        Long userId= UserHolder.getUser().getId();
//
////        //事务使用的是代理对象进行的，所以要获取代理对象再进行调用对应的与事务有关的函数
////        synchronized (userId.toString().intern()){
////            IVoucherOrderService proxy =(IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//        //集群问题下的一人一单问题
//        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate, "order" + userId);
//
//        RLock simpleRedisLock = redissonClient.getLock("lock:order" + userId);
//
//        boolean isLock = simpleRedisLock.tryLock();
//        if(!isLock){
//            return Result.fail("每个人只限抢购一单");
//        }
//        try {
//            IVoucherOrderService proxy=(IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            simpleRedisLock.unlock();
//        }
//    }

//    @Transactional
//    public Result createVoucherOrder(Long voucherId){
//        //5.一人一单
//        long userId= UserHolder.getUser().getId();
//        //5.1 查询订单
//        int count=query().eq("user_id",userId).eq("voucher_id",voucherId).count();
//        //5.2 判断是否存在
//        if(count>0){
//            return Result.fail("用户已经购买过一次了!");
//        }
//        //6.扣减库存
////        boolean success = iSeckillVoucherService.update()
////                .setSql("stock=stock-1")
////                .eq("voucher_id", voucherId)
////                .eq("stock",stock)
////                .update();
//        //解决效率低的问题
//        boolean success = iSeckillVoucherService.update()
//                .setSql("stock=stock-1")
//                .eq("voucher_id", voucherId)
//                .gt("stock",0)
//                .update();
//        if(!success){
//            return Result.fail("优惠券已经被抢完");
//        }
//
////        自己写的方案
////        if(stock==iSeckillVoucherService.getById(voucherId).getStock()){
////            boolean success = iSeckillVoucherService.update()
////                    .setSql("stock=stock-1")
////                    .eq("voucher_id", voucherId)
////                    .update();
////            if(!success){
////                return Result.fail("优惠券已经被抢完");
////            }
////        }else{
////            return Result.fail("当前系统繁忙请重试");
////        }
//
//        //7.生成订单
//        VoucherOrder voucherOrder=new VoucherOrder();
//        //7.1 订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //7.2 用户id
//        voucherOrder.setUserId(userId);
//        //7.3 代金券id
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//        //8.返回订单id
//        return Result.ok(orderId);
//    }

}
