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
     *
     * @return the scene name
     */
    String scene();

    /**
     * The lock key parameter, evaluated as a SpEL expression.
     * <p>
     * The final lock key is {@code scene#parsedKey}. If left empty, only {@code scene} is used.
     *
     * @return the SpEL key expression
     */
    String key() default "";

    /**
     * Lock lease time in milliseconds.
     * <p>
     * Priority: annotation value &gt; global config ({@code dicraft.lock.lease-time}) &gt; default (-1).
     * <p>
     * Default value {@code -1} enables Redisson Watchdog for automatic lease renewal.
     *
     * @return the lease time in milliseconds
     */
    long leaseTime() default DistributeLockConfigConstant.UNSET;

    /**
     * Maximum wait time to acquire the lock in milliseconds.
     * <p>
     * Priority: annotation value &gt; global config ({@code dicraft.lock.wait-time}) &gt; default (-1).
     * <p>
     * Default value {@code -1} waits indefinitely until the lock is acquired.
     *
     * @return the wait time in milliseconds
     */
    long waitTime() default DistributeLockConfigConstant.UNSET;
}
