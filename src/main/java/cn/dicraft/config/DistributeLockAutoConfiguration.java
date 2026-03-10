package cn.dicraft.config;

import cn.dicraft.aspect.DistributeLockAspect;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot autoconfiguration for distributed lock.
 * <p>
 * Activates automatically when {@link RedissonClient} is on the classpath
 * and a {@link RedissonClient} bean is available.
 *
 * @author 烛远
 */
@Configuration
@ConditionalOnClass(RedissonClient.class)
public class DistributeLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DistributeLockProperties distributeLockProperties(
            @Value("${dicraft.lock.lease-time:#{null}}") Long leaseTime,
            @Value("${dicraft.lock.wait-time:#{null}}") Long waitTime
    ) {
        return new DistributeLockProperties(leaseTime, waitTime);
    }

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean
    public DistributeLockAspect distributeLockAspect(RedissonClient redissonClient,
                                                     DistributeLockProperties properties) {
        return new DistributeLockAspect(redissonClient, properties);
    }
}
