package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @project:
 * @author: mikudd3
 * @version: 1.0
 */
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissionClient() {
        // 配置类
        Config config = new Config();

        // 添加 Redis 地址，此处添加了单点的地址，也可以使用 config.useClusterServers() 添加集群地址
        config.useSingleServer().setAddress("redis://localhost:6379");

        // 创建客户端
        return Redisson.create(config);
    }
}

