package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker idWorker;

    /**
     * 秒杀下单
     *
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //返回错误信息
            return Result.fail("秒杀尚未开始");
        }
        //3.判断秒杀是否结果
        if (voucher.getBeginTime().isBefore(LocalDateTime.now())) {
            //返回错误信息
            return Result.fail("秒杀已经结束");
        }
        //4.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("优惠卷已经被抢空");
        }

        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            //获取代理对象对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //5.一人一单
        Long userId = UserHolder.getUser().getId();
        //5.1查询订单
        Integer count = this.query().eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        //5.2 判断是否存在
        if (count > 0) {
            //用户已经购买过
            return Result.fail("不能重复购买");
        }
        //6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("优惠卷已经被抢空");
        }
        //7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //7.1 订单id
        long orderId = idWorker.nextId("order");
        voucherOrder.setVoucherId(orderId);
        //7.2 用户id
        voucherOrder.setUserId(userId);
        //7.3代金卷id
        voucherOrder.setVoucherId(voucherId);
        //7.4将订单写入数据库
        this.save(voucherOrder);
        //返回订单id
        return Result.ok(orderId);
    }
}
