package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.DayOfWeek;
import java.util.List;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public Result queryBlogById(String id){
        // 1.查询blog
        Blog blog=getById(id);
        if(blog==null){
            return Result.fail("笔记不存在");
        }
        Long userId=blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //1.判断当前用户是否已经点赞
        UserDTO user = UserHolder.getUser();
        Long userId=user.getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(BLOG_LIKED_KEY + id, userId.toString());

        if(!isMember){
            //2.没有点赞
            //2.1 修改点赞数量
            update().setSql("liked = liked + 1").eq("id", id).update();
            //2.2 加入用户id到点赞列表
            stringRedisTemplate.opsForSet().add(BLOG_LIKED_KEY + id,userId.toString());
            //2.3 设置isLock属性
            Blog blog=getById(id);
            blog.setIsLike(true);
            return Result.ok("点赞成功！");
        }else{
            //3.点过赞
            //3.1 修改点赞数量
            update().setSql("liked = liked-1").eq("id", id).update();
            //3.2 设置isLock属性
            Blog blog=getById(id);
            blog.setIsLike(false);
            //3.3 删除用户列表里的id
            stringRedisTemplate.opsForSet().remove(BLOG_LIKED_KEY + id,userId.toString());
            return Result.fail("你已经点过赞了");
        }
    }

    private void isBlogLiked(Blog blog){
        UserDTO user = UserHolder.getUser();
        Long userId=user.getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(BLOG_LIKED_KEY + blog.getId(), userId.toString());
        blog.setIsLike(BooleanUtil.isTrue(isMember));
    }
}
