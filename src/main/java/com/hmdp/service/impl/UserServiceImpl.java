package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     *
     * @param phone
     * @param session
     * @return
     */

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.判断手机号是否符合，不符合则返回错误信息
            return Result.fail("请输入正确的手机号");
        }

        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4.保存验证码到redis中,并设置有效时间为2分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //4.保存验证码到session域中
        //session.setAttribute("code", code);

        //5.发送验证码
        //由于验证码需要调用第三方平台，流程复杂，这里使用日志输出验证码
        log.info("验证码为：{}", code);

        //6.返回结果
        return Result.ok();
    }

    /**
     * 登录功能
     *
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.判断手机号是否符合，不符合则返回错误信息
            return Result.fail("请输入正确的手机号");
        }

        //2.校验验证码
        //2.1从redis中获取验证码


        //2.1从session中取出验证码
        String sessionCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        //2.2获取用户输入验证码
        String code = loginForm.getCode();
        log.info("用户输入验证码：{}", code);
        //2.3判断验证码是否不存在或者验证码是否已经过期
        if (sessionCode == null || !code.equals(sessionCode)) {
            return Result.fail("输入验证码有误");
        }

        //3.根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();

        //4.判断用户是否存在
        if (user == null) {
            //用户不存在则创建新用户
            user = createUserWithPhone(phone);
        }

        //5保存用户信息到redis
        //5.1随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);

        //5.2将user对象转为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().
                        setIgnoreNullValue(true).
                        setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));


        //5.3将对象存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

        //5.4设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        //6.返回token
        return Result.ok(token);
    }

    /**
     * 创建新用户
     *
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        //创建新用户
        User user = new User();
        //设置新用户信息
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //2.保存用户
        this.save(user);
        return user;
    }


}
