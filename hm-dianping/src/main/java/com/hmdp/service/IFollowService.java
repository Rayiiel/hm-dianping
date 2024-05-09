package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注用户
     * @param id
     * @param isFollow
     * @return
     */
    Result followUser(Long id,boolean isFollow);

    /**
     * 查看是否关注了该用户
     * @param id
     * @return
     */
    Result queryFollowUser(Long id);
}
