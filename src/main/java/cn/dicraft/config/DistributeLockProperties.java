package cn.dicraft.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Global configuration properties for distributed lock.
 * <p>
 * These values serve as fallback when the annotation does not specify
 * {@code leaseTime} or {@code waitTime} explicitly.
 * <p>
 * Priority: annotation value > global config > default value.
 *
 * @author 烛远
 * @see cn.dicraft.annotation.DistributeLock
 */
@Getter
@RequiredArgsConstructor
public class DistributeLockProperties {

    private final Long leaseTime;

    private final Long waitTime;
}
