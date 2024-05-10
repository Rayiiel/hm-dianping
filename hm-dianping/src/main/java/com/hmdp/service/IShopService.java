package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    Result queryById(Long id) throws InterruptedException;

    /**
     * 更新店铺信息
     * @return
     */
    Result update(Shop shop);

    /**
     * 按照距离远近查询商家信息
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return
     */
    Result queryShopByTypeAndDistance(Integer typeId, Integer current, Double x, Double y);
}
