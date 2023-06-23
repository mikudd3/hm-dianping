package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_LIST_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询服务缓存
     *
     * @return
     */
    @Override
    public Result queryList() {
        //1.先从redis中查询是否存在该用户
        String shopTypeListJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_LIST_KEY);
        //2.判断是否为空
        if (StrUtil.isNotBlank(shopTypeListJson)) {
            //不为空则返回
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeListJson, ShopType.class);
            return Result.ok(shopTypes);
        }
        //3.如果在redis中找不到则从数据库中找
        List<ShopType> typeList = this.query().orderByAsc("sort").list();
        //4.如果数据库中也不存在，则返回错误信息
        if (typeList == null) {
            return Result.fail("该分类不存在");
        }
        //5.将查询到的信息添加到redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_LIST_KEY, JSONUtil.toJsonStr(typeList));
        //6.返回信息
        return Result.ok(typeList);
    }
}
