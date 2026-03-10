package cn.dicraft.annotation;

import cn.dicraft.DistributeLockConfigConstant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative distributed lock annotation.
 * <p>
 * Methods annotated with {@code @DistributeLock} will automatically acquire
 * a Redisson-based distributed lock before execution and release it afterwards.
 *
 * @author 烛远
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributeLock {

    /**
     * The business scene identifier for the lock.
     */
    String scene();

    /**
     * The lock key parameter, evaluated as a SpEL expression.
     * <p>
     * The final lock key is {@code scene#parsedKey}. If left empty, only {@code scene} is used.
     */
    String key() default "";

    /**
     * Lock lease time in milliseconds.
     * <p>
     * Priority: annotation value > global config ({@code dicraft.lock.lease-time}) > default (-1).
     * <p>
     * Default value {@code -1} enables Redisson Watchdog for automatic lease renewal.
     */
    long leaseTime() default DistributeLockConfigConstant.UNSET;

    /**
     * Maximum wait time to acquire the lock in milliseconds.
     * <p>
     * Priority: annotation value > global config ({@code dicraft.lock.wait-time}) > default (-1).
     * <p>
     * Default value {@code -1} waits indefinitely until the lock is acquired.
     */
    long waitTime() default DistributeLockConfigConstant.UNSET;
}
