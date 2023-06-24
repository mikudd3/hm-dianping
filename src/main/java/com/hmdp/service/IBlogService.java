package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 根据blogid返回blog
     *
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    Result queryHotBlog(Integer current);

    /**
     * 点赞blog
     *
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);
}
