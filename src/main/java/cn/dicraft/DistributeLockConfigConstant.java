package cn.dicraft;

/**
 * Constants for distributed lock configuration.
 *
 * @author 烛远
 */
public class DistributeLockConfigConstant {

    private DistributeLockConfigConstant() {
    }

    /**
     * Sentinel value indicating the parameter is not explicitly set.
     * Used to distinguish "not configured" from an intentional {@code -1}.
     */
    public static final long UNSET = Long.MIN_VALUE;

    /**
     * Default lease time ({@code -1}).
     * Enables Redisson Watchdog for automatic lease renewal.
     * The watchdog renewal interval defaults to {@code lockWatchdogTimeout} (30s).
     */
    public static final long DEFAULT_LEASE_TIME = -1;

    /**
     * Default wait time ({@code -1}).
     * Waits indefinitely until the lock is acquired.
     */
    public static final long DEFAULT_WAIT_TIME = -1;
}
