package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOW_KEY;

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


    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

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
        String key=FOLLOW_KEY+userId;
        //2.如果要关注，添加关注信息到关注信息的列表
        Follow follow = new Follow();
        follow.setUserId(userId);
        follow.setFollowUserId(id);
        follow.setCreateTime(LocalDateTime.now());
        if(isFollow){
            save(follow);
            stringRedisTemplate.opsForSet().add(key,id.toString());
            return Result.ok();
        }
        //3.如果取消关注，删除关注信息
        //3.1 查询订单返回id
        Follow follow1 = query().eq("user_id", userId).eq("follow_user_id", id).one();
        if(follow1==null){
            return Result.ok();
        }
        removeById(follow1.getId());
        stringRedisTemplate.opsForSet().remove(key,id.toString());
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

    /**
     * 查询共同关注列表
     * @param id
     * @return
     */
    @Override
    public Result commonFollow(Long id) {
        //1.获取当前用户id
        Long userId=UserHolder.getUser().getId();
        //2.查找两个用户的关注交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(FOLLOW_KEY + userId, FOLLOW_KEY + id);
        if(intersect==null||intersect.isEmpty()){
            return Result.ok();
        }
        List<Long> follows = intersect
                .stream().map(Long::valueOf)
                .collect(Collectors.toList());
        List<User> users = userService.listByIds(follows);
        List<UserDTO> usersDTOs = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(usersDTOs);

    }
}
