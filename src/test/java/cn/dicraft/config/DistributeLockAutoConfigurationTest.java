package cn.dicraft.config;

import cn.dicraft.aspect.DistributeLockAspect;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DistributeLockAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DistributeLockAutoConfiguration.class));

    @Test
    void withRedissonClientBean_allBeansRegistered() {
        contextRunner
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributeLockProperties.class);
                    assertThat(context).hasSingleBean(DistributeLockAspect.class);
                });
    }

    @Test
    void withoutRedissonClientBean_aspectNotRegistered() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributeLockProperties.class);
                    assertThat(context).doesNotHaveBean(DistributeLockAspect.class);
                });
    }

    @Test
    void withProperties_valuesInjected() {
        contextRunner
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .withPropertyValues(
                        "dicraft.lock.lease-time=30000",
                        "dicraft.lock.wait-time=5000"
                )
                .run(context -> {
                    DistributeLockProperties props = context.getBean(DistributeLockProperties.class);
                    assertThat(props.getLeaseTime()).isEqualTo(30000L);
                    assertThat(props.getWaitTime()).isEqualTo(5000L);
                });
    }

    @Test
    void withoutProperties_valuesAreNull() {
        contextRunner
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .run(context -> {
                    DistributeLockProperties props = context.getBean(DistributeLockProperties.class);
                    assertThat(props.getLeaseTime()).isNull();
                    assertThat(props.getWaitTime()).isNull();
                });
    }
}
