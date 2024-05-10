package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
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
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * 根据id查询博客
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(String id){
        // 1.查询blog
        Blog blog=getById(id);
        if(blog==null){
            return Result.fail("笔记不存在");
        }
        blogInfo(blog);


        return Result.ok(blog);
    }


    /**
     * 访问热门博客
     * @param current
     * @return
     */
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
            blogInfo(blog);

        });
        return Result.ok(records);
    }

    /**
     * 点赞功能实现
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        //1.判断当前用户是否已经点赞
        UserDTO user = UserHolder.getUser();
        Long userId=user.getId();
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());

        if(score == null){
            //2.没有点赞
            //2.1 修改点赞数量
            update().setSql("liked = liked + 1").eq("id", id).update();
            //2.2 加入用户id到点赞列表
            stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id,userId.toString(),System.currentTimeMillis());
            return Result.ok("");
        }else{
            //3.点过赞
            //3.1 修改点赞数量
            update().setSql("liked = liked-1").eq("id", id).update();
            //3.3 删除用户列表里的id
            stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id,userId.toString());
            return Result.fail("");
        }
    }

    /**
     * 查询点赞排行榜
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        //1.查询top5的点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(BLOG_LIKED_KEY + id, 0, 4);
        if(top5==null||top5.isEmpty()){
            return Result.fail("");
        }
        //TODO:这里是什么意思
        //2. 解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",",ids);
        //3. 根据用户id查询用户 WHERE id IN (5,1) ORDER BY FIELD(id,5,1)
        List<UserDTO> userDTOS = userService.query()
                .in("id",ids).last("ORDER BY FIELD(id,"+idStr+")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //4.返回
        return Result.ok(userDTOS);
    }

    /**
     * 查看blog的其它属性
     * @param blog
     */
    private void blogInfo(Blog blog) {
        Long userId = blog.getUserId();
        if(userId==null){
            return;
        }
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        //查看点赞情况，设置isLike属性
        isBlogLiked(blog);
    }

    /**
     * 判断是否点赞
     * @param blog
     */
    private void isBlogLiked(Blog blog){
        UserDTO user = UserHolder.getUser();
        if(user==null){
            return;
        }
        Long userId=user.getId();
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), userId.toString());
        blog.setIsLike(BooleanUtil.isTrue(score!=null));
    }


}
