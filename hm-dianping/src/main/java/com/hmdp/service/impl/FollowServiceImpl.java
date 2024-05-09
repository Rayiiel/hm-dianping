package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    /**
     * 关注该用户
     * @param id
     * @param isFollow
     * @return
     */
    @Override
    public Result followUser(Long id, boolean isFollow) {
        //1.获取当前用户id
        UserDTO user = UserHolder.getUser();
        Long userId=user.getId();
        //2.如果要关注，添加关注信息到关注信息的列表
        Follow follow = new Follow();
        follow.setUserId(userId);
        follow.setFollowUserId(id);
        follow.setCreateTime(LocalDateTime.now());
        if(isFollow){
            save(follow);
            return Result.ok();
        }
        //3.如果取消关注，删除关注信息
        //3.1 查询订单返回id
        Follow follow1 = query().eq("user_id", userId).eq("follow_user_id", id).one();
        if(follow1==null){
            return Result.ok();
        }
        removeById(follow1.getId());
        return Result.ok();

    }

    /**
     * 查询是否关注了该用户
     * @param id
     * @return
     */
    @Override
    public Result queryFollowUser(Long id) {
        //1.获取当前用户id
        UserDTO user = UserHolder.getUser();
        Long userId=user.getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", id).count();
        return Result.ok(count>0);

    }
}
