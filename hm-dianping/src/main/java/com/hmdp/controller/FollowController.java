package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Autowired
    private IFollowService followService;

    /**
     * 关注用户
     * @param id
     * @return
     */
    @PutMapping("/{id}/{isFollow}")
    public Result followUser(@PathVariable Long id, @PathVariable Boolean isFollow){
        return followService.followUser(id,isFollow);
    }

    /**
     * 查询是否关注了该用户
     * @param id
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result queryFollowUser(@PathVariable Long id){
        return followService.queryFollowUser(id);
    }

}
