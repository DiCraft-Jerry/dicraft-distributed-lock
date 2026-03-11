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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AOP aspect for {@link DistributeLock} annotation.
 * Provides declarative distributed locking via Redisson.
 *
 * @author 烛远
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
@Order(Integer.MIN_VALUE)
public class DistributeLockAspect {

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
        String keyPrefix = resolveKeyPrefix(properties.getKeyPrefix());
        long leaseTime = resolveTime(distributeLock.leaseTime(), properties.getLeaseTime(), DistributeLockConfigConstant.DEFAULT_LEASE_TIME),
                waitTime = resolveTime(distributeLock.waitTime(), properties.getWaitTime(), DistributeLockConfigConstant.DEFAULT_WAIT_TIME);

        String lockKey;
        if (StringUtils.isNotBlank(key)) {
            String parsedKey = parseKeyExpression(key, buildArgMap(getParameterNames(method), pjp.getArgs()));
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
     * Evaluates a SpEL expression against the method arguments.
     */
    private String parseKeyExpression(String keyExpression, Map<String, Object> variables) {
        EvaluationContext context = new StandardEvaluationContext();
        variables.forEach(context::setVariable);

        SpelExpressionParser parser = new SpelExpressionParser();
        Expression expression = parser.parseExpression(keyExpression);
        return String.valueOf(expression.getValue(context));
    }

    private String[] getParameterNames(Method method) {
        return new DefaultParameterNameDiscoverer().getParameterNames(method);
    }

    private Map<String, Object> buildArgMap(String[] parameterNames, Object[] args) {
        Map<String, Object> map = new HashMap<>();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                map.put(parameterNames[i], args[i]);
            }
        }
        return map;
    }
}
