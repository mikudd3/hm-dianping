package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据id查询店铺
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 互斥锁
     *
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1.从redis店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //2.1。 存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //3.判断命中是否为空值
        if (shopJson != null) {
            //返回错误信息
            return null;
        }
        //4.实现缓存重建
        //4.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = this.tryLock(lockKey);
            //4.2判断释放获取成功
            if (!isLock) {
                //4.3失败则休眠并重试
                Thread.sleep(100);
                this.queryWithMutex(id);
            }
            //4.4成功，根据id查询数据库
            shop = this.getById(id);
            if (shop == null) {
                //4.1 不存在，返回错误信息
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //5.存在，写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
            //6.更新缓存时间
            stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7.释放互斥锁
            this.unlock(lockKey);
        }

        //8.返回
        return shop;
    }

    /**
     * 缓存穿透
     *
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1.从redis店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //2.1。 存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //3.判断命中是否为空值
        if (shopJson != null) {
            //返回错误信息
            return null;
        }
        //4.不存在，根据id查询数据库
        Shop shop = this.getById(id);
        if (shop == null) {
            //4.1 不存在，返回错误信息
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5.存在，写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
        //6.更新缓存时间
        stringRedisTemplate.expire(key, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7.返回
        return shop;
    }


    /**
     * 上锁
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.MINUTES);

        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 更新商铺信息
     *
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        this.updateById(shop);
        //2。删除缓存
        //2.1获取店铺id
        String key = CACHE_SHOP_KEY + id;
        //2.2删除缓存
        stringRedisTemplate.delete(key);
        return Result.ok();
    }
}
