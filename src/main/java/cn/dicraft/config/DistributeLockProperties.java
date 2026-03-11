package cn.dicraft.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Global configuration properties for distributed lock.
 * <p>
 * These values serve as fallback when the annotation does not specify
 * {@code leaseTime} or {@code waitTime} explicitly.
 * <p>
 * Priority: annotation value &gt; global config &gt; default value.
 * <p>
 * Configurable via {@code application.yml}:
 * <pre>
 * dicraft:
 *   lock:
 *     lease-time: 30000
 *     wait-time: 5000
 *     key-prefix: my-app
 * </pre>
 *
 * @author 烛远
 * @see cn.dicraft.annotation.DistributeLock
 */
@Getter
@RequiredArgsConstructor
public class DistributeLockProperties {

    /**
     * Global lock lease time in milliseconds.
     * Falls back to {@link cn.dicraft.DistributeLockConfigConstant#DEFAULT_LEASE_TIME} when {@code null}.
     */
    private final Long leaseTime;

    /**
     * Global lock wait time in milliseconds.
     * Falls back to {@link cn.dicraft.DistributeLockConfigConstant#DEFAULT_WAIT_TIME} when {@code null}.
     */
    private final Long waitTime;

    /**
     * Global lock key prefix.
     * <p>
     * When configured, the prefix is prepended to all lock keys with a colon separator.
     * For example, if prefix is {@code "my-app"} and the lock key is {@code "order#123"},
     * the final key becomes {@code "my-app:order#123"}.
     * <p>
     * When {@code null} or empty, no prefix is added, preserving the original key format.
     *
     * @see cn.dicraft.DistributeLockConfigConstant#DEFAULT_KEY_PREFIX
     */
    private final String keyPrefix;
}
