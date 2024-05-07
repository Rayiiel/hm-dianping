package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService iSeckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if(LocalDateTime.now().isBefore(voucher.getBeginTime())){
            return Result.fail("秒杀还未开始");
        }
        //3.判断秒杀是否已经结束
        if(LocalDateTime.now().isAfter(voucher.getEndTime())){
            return Result.fail("秒杀已经结束");
        }
        //4.判断库存是否充足
        int stock=voucher.getStock();
        if(stock<=0){
            return Result.fail("优惠券已经被抢完");
        }
        Long userId= UserHolder.getUser().getId();

//        //事务使用的是代理对象进行的，所以要获取代理对象再进行调用对应的与事务有关的函数
//        synchronized (userId.toString().intern()){
//            IVoucherOrderService proxy =(IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
        //集群问题下的一人一单问题
        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate, "order" + userId);
        boolean isLock = simpleRedisLock.tryLock(20);
        if(!isLock){
            return Result.fail("每个人只限抢购一单");
        }
        try {
            IVoucherOrderService proxy=(IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            simpleRedisLock.unLock();
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId){
        //5.一人一单
        long userId= UserHolder.getUser().getId();
        //5.1 查询订单
        int count=query().eq("user_id",userId).eq("voucher_id",voucherId).count();
        //5.2 判断是否存在
        if(count>0){
            return Result.fail("用户已经购买过一次了!");
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
            return Result.fail("优惠券已经被抢完");
        }

//        自己写的方案
//        if(stock==iSeckillVoucherService.getById(voucherId).getStock()){
//            boolean success = iSeckillVoucherService.update()
//                    .setSql("stock=stock-1")
//                    .eq("voucher_id", voucherId)
//                    .update();
//            if(!success){
//                return Result.fail("优惠券已经被抢完");
//            }
//        }else{
//            return Result.fail("当前系统繁忙请重试");
//        }

        //7.生成订单
        VoucherOrder voucherOrder=new VoucherOrder();
        //7.1 订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //7.2 用户id
        voucherOrder.setUserId(userId);
        //7.3 代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        //8.返回订单id
        return Result.ok(orderId);
    }

}
