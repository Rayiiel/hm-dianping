package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

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
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发布博客
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        save(blog);
        // 推送博文到粉丝的收件箱里面
        String keyPix=FEED_KEY;
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        for (Follow follow:follows) {
            Long fansId=follow.getUserId();
            stringRedisTemplate.opsForZSet()
                    .add(FEED_KEY+fansId,blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

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


    /**
     * 滚动式显示关注发布的最新博客
     * @param lastId
     * @param offSet
     * @return
     */
    @Override
    public Result queryFollowBlog(Long lastId, Integer offSet) {

        //1.获取当前用户收件箱中的博客id
        Long userId=UserHolder.getUser().getId();
        String key=FEED_KEY+userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, lastId, offSet, 2);

        if(typedTuples==null||typedTuples.isEmpty()){
            return Result.ok();
        }
        //2.根据id获得返回列表，并记录要返回的lastId和offSet
        Long minTime=0L;
        Integer offset=1;
        ArrayList<Blog> blogs = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> typedTuple:typedTuples) {
            String value = typedTuple.getValue();
            Long score = typedTuple.getScore().longValue();
            Blog blog = getById(Long.valueOf(value));
            blogs.add(blog);
            if(score.equals(minTime)){
                offset++;
            }else{
                minTime=score;
                offset=1;
            }
        }
        //3.封装返回结果
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(offset);
        r.setMinTime(minTime);
        return Result.ok(r);
    }
}
