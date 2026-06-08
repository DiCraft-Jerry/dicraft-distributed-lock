package cn.dicraft.aspect;

import cn.dicraft.DistributeLockConfigConstant;
import cn.dicraft.annotation.DistributeLock;
import cn.dicraft.config.DistributeLockProperties;
import cn.dicraft.exception.DistributeLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * AOP aspect for {@link DistributeLock} annotation.
 * Provides declarative distributed locking via Redisson.
 *
 * <p><b>Deadlock considerations:</b>
 * Each invocation holds exactly one lock and releases it in a {@code finally} block,
 * so lock leaks within a single call are not possible. However, if a call chain nests
 * multiple {@code @DistributeLock} annotations with <em>different</em> keys (e.g. thread A
 * locks {@code order#1} then {@code order#2}, while thread B locks {@code order#2} then
 * {@code order#1}), a circular-wait deadlock can occur.
 *
 * <p><b>Recommendations:</b>
 * <ul>
 *   <li>Prefer acquiring a single lock per business operation whenever possible.</li>
 *   <li>If multiple locks are unavoidable, establish a project-wide convention for
 *       a consistent lock ordering (e.g. lexicographic order of lock keys).</li>
 * </ul>
 *
 * <p>When multiple {@link DistributeLock#keys()} are specified, this aspect sorts the
 * resolved key segments lexicographically before joining them. This ensures that the
 * same set of resources always produces the same lock key regardless of the order in
 * which they appear in the annotation, reducing the risk of ordering-related deadlocks.
 *
 * @author 烛远
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
@Order(Integer.MIN_VALUE)
public class DistributeLockAspect {

    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();

    private final RedissonClient redissonClient;
    private final DistributeLockProperties properties;

    /**
     * Around advice that acquires a distributed lock before method execution
     * and releases it afterward.
     *
     * @param pjp the proceeding join point
     * @return the result of the target method
     * @throws InterruptedException    if the thread is interrupted while waiting for the lock
     * @throws DistributeLockException if the lock cannot be acquired within the wait time
     * @throws Throwable               if the target method throws an exception
     */
    @Around("@annotation(cn.dicraft.annotation.DistributeLock)")
    public Object process(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        DistributeLock distributeLock = method.getAnnotation(DistributeLock.class);

        String scene = distributeLock.scene(),
                key = distributeLock.key();
        String[] keys = distributeLock.keys();
        String keyPrefix = resolveKeyPrefix(properties.getKeyPrefix());
        long leaseTime = resolveTime(distributeLock.leaseTime(), properties.getLeaseTime(), DistributeLockConfigConstant.DEFAULT_LEASE_TIME),
                waitTime = resolveTime(distributeLock.waitTime(), properties.getWaitTime(), DistributeLockConfigConstant.DEFAULT_WAIT_TIME);

        String lockKey;
        if (keys != null && keys.length > 0) {
            Map<String, Object> args = combineArgs(getParameterNames(method), pjp.getArgs());
            String parsedKey = Arrays.stream(keys)
                    .filter(StringUtils::isNotBlank)
                    .map(keyExpression -> analyseKeyExpression(keyExpression, args))
                    .sorted()
                    .collect(Collectors.joining("."));
            lockKey = StringUtils.isNotBlank(parsedKey) ? keyPrefix + scene + "#" + parsedKey : keyPrefix + scene;
        } else if (StringUtils.isNotBlank(key)) {
            String parsedKey = analyseKeyExpression(key, combineArgs(getParameterNames(method), pjp.getArgs()));
            lockKey = keyPrefix + scene + "#" + parsedKey;
        } else {
            lockKey = keyPrefix + scene;
        }

        String threadName = Thread.currentThread().getName();
        long threadId = Thread.currentThread().getId();

        boolean lockSuccess;
        long acquireStartNanos = System.nanoTime();

        RLock rLock = redissonClient.getLock(lockKey);

        if (waitTime == DistributeLockConfigConstant.DEFAULT_WAIT_TIME) {
            log.debug("[DistributeLock] Acquiring lock via lock(), lockKey={}, leaseTime={}ms", lockKey, leaseTime);
            if (leaseTime == DistributeLockConfigConstant.DEFAULT_LEASE_TIME) {
                rLock.lock();
            } else {
                rLock.lock(leaseTime, TimeUnit.MILLISECONDS);
            }
            lockSuccess = true;
        } else {
            log.debug("[DistributeLock] Acquiring lock via tryLock(), lockKey={}, waitTime={}ms, leaseTime={}ms", lockKey, waitTime, leaseTime);
            try {
                if (leaseTime == DistributeLockConfigConstant.DEFAULT_LEASE_TIME) {
                    lockSuccess = rLock.tryLock(waitTime, TimeUnit.MILLISECONDS);
                } else {
                    lockSuccess = rLock.tryLock(waitTime, leaseTime, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                log.error("[DistributeLock] Thread interrupted while acquiring lock, lockKey={}, thread={}#{}", lockKey, threadName, threadId);
                throw e;
            }
        }

        long acquireCostMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - acquireStartNanos);

        if (lockSuccess) {
            log.info("[DistributeLock] Lock acquired, lockKey={}, cost={}ms, thread={}#{}", lockKey, acquireCostMs, threadName, threadId);
        } else {
            log.warn("[DistributeLock] Failed to acquire lock, lockKey={}, waitTime={}ms, cost={}ms, thread={}#{}", lockKey, waitTime, acquireCostMs, threadName, threadId);
            throw new DistributeLockException(String.format(
                    "Failed to acquire lock [%s] within %dms (thread=%s#%d)", lockKey, waitTime, threadName, threadId));
        }

        try {
            return pjp.proceed();
        } finally {
            long holdTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - acquireStartNanos) - acquireCostMs;
            if (rLock.isLocked() && rLock.isHeldByCurrentThread()) {
                rLock.unlock();
                log.info("[DistributeLock] Lock released, lockKey={}, holdTime={}ms, thread={}#{}", lockKey, holdTimeMs, threadName, threadId);
            } else {
                log.warn("[DistributeLock] Lock not held by current thread on release, lockKey={}, thread={}#{}", lockKey, threadName, threadId);
            }
        }
    }

    /**
     * Resolves the effective key prefix.
     * Returns the prefix with a trailing colon separator, or an empty string if no prefix is configured.
     */
    private String resolveKeyPrefix(String globalKeyPrefix) {
        if (StringUtils.isNotBlank(globalKeyPrefix)) {
            return globalKeyPrefix + ":";
        }
        return DistributeLockConfigConstant.DEFAULT_KEY_PREFIX;
    }

    /**
     * Resolves the effective time value with priority: annotation > global config > default.
     */
    private long resolveTime(long annotationValue, Long globalValue, long defaultValue) {
        if (annotationValue != DistributeLockConfigConstant.UNSET) {
            return annotationValue;
        }
        if (globalValue != null) {
            return globalValue;
        }
        return defaultValue;
    }

    /**
     * Evaluates a SpEL expression against the method arguments and converts the result
     * into a lock key segment. If the expression evaluates to a {@link Collection} or
     * an array, the elements are joined with {@code "."} as the separator.
     *
     * @param keyExpression the SpEL expression string (e.g. {@code "#orderId"})
     * @param variables     a map of parameter names to their runtime values
     * @return the resolved key segment string
     */
    private String analyseKeyExpression(String keyExpression, Map<String, Object> variables) {
        EvaluationContext context = new StandardEvaluationContext();
        variables.forEach(context::setVariable);

        Expression expression = SPEL_PARSER.parseExpression(keyExpression);
        Object value = expression.getValue(context);
        return toKeySegment(value);
    }

    /**
     * Converts a SpEL evaluation result into a key segment string.
     * <ul>
     *   <li>{@link Collection}: elements joined with {@code "."}</li>
     *   <li>Array: elements joined with {@code "."}</li>
     *   <li>{@code null}: empty string</li>
     *   <li>Other types: {@link String#valueOf(Object)}</li>
     * </ul>
     *
     * @param value the SpEL evaluation result
     * @return the key segment string
     */
    private String toKeySegment(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining("."));
        }
        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) {
                if (i > 0) {
                    sb.append('.');
                }
                sb.append(Array.get(value, i));
            }
            return sb.toString();
        }
        return String.valueOf(value);
    }

    private String[] getParameterNames(Method method) {
        return new DefaultParameterNameDiscoverer().getParameterNames(method);
    }

    private Map<String, Object> combineArgs(String[] parameterNames, Object[] args) {
        Map<String, Object> map = new HashMap<>();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                map.put(parameterNames[i], args[i]);
            }
        }
        return map;
    }
}
