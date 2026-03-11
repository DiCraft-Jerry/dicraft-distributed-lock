package cn.dicraft.aspect;

import cn.dicraft.annotation.DistributeLock;
import cn.dicraft.config.DistributeLockProperties;
import cn.dicraft.exception.DistributeLockException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributeLockAspectTest {

    @Mock private RedissonClient redissonClient;
    @Mock private RLock rLock;
    @Mock private ProceedingJoinPoint pjp;
    @Mock private MethodSignature methodSignature;

    private DistributeLockProperties defaultProperties;

    @BeforeEach
    void setUp() {
        defaultProperties = new DistributeLockProperties(null, null, null);
        lenient().when(pjp.getSignature()).thenReturn(methodSignature);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
    }

    private void mockMethod(String methodName, Class<?>... paramTypes) throws Exception {
        Method method = StubMethods.class.getMethod(methodName, paramTypes);
        when(methodSignature.getMethod()).thenReturn(method);
    }

    private void mockLockHeld() {
        lenient().when(rLock.isLocked()).thenReturn(true);
        lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    private DistributeLockAspect createAspect(DistributeLockProperties properties) {
        return new DistributeLockAspect(redissonClient, properties);
    }

    // ===== Locking Strategy =====

    @Test
    void defaultWaitTime_defaultLeaseTime_callsLock() throws Throwable {
        mockMethod("defaultAll");
        mockLockHeld();
        when(pjp.proceed()).thenReturn("ok");

        createAspect(defaultProperties).process(pjp);

        verify(rLock).lock();
        verify(rLock, never()).lock(anyLong(), any());
    }

    @Test
    void defaultWaitTime_customLeaseTime_callsLockWithLease() throws Throwable {
        mockMethod("customLeaseTime");
        mockLockHeld();
        when(pjp.proceed()).thenReturn("ok");

        createAspect(defaultProperties).process(pjp);

        verify(rLock).lock(5000L, TimeUnit.MILLISECONDS);
        verify(rLock, never()).lock();
    }

    @Test
    void customWaitTime_defaultLeaseTime_callsTryLock() throws Throwable {
        mockMethod("customWaitTime");
        mockLockHeld();
        when(pjp.proceed()).thenReturn("ok");
        when(rLock.tryLock(3000L, TimeUnit.MILLISECONDS)).thenReturn(true);

        createAspect(defaultProperties).process(pjp);

        verify(rLock).tryLock(3000L, TimeUnit.MILLISECONDS);
        verify(rLock, never()).tryLock(anyLong(), anyLong(), any());
    }

    @Test
    void customWaitTime_customLeaseTime_callsTryLockWithLease() throws Throwable {
        mockMethod("customBoth");
        mockLockHeld();
        when(pjp.proceed()).thenReturn("ok");
        when(rLock.tryLock(3000L, 5000L, TimeUnit.MILLISECONDS)).thenReturn(true);

        createAspect(defaultProperties).process(pjp);

        verify(rLock).tryLock(3000L, 5000L, TimeUnit.MILLISECONDS);
    }

    // ===== Lock Acquisition Result =====

    @Test
    void tryLock_success_proceedsAndUnlocks() throws Throwable {
        mockMethod("customWaitTime");
        mockLockHeld();
        when(pjp.proceed()).thenReturn("result");
        when(rLock.tryLock(3000L, TimeUnit.MILLISECONDS)).thenReturn(true);

        Object result = createAspect(defaultProperties).process(pjp);

        assertEquals("result", result);
        verify(pjp).proceed();
        verify(rLock).unlock();
    }

    @Test
    void tryLock_failure_throwsDistributeLockException() throws Throwable {
        mockMethod("customWaitTime");
        when(rLock.tryLock(3000L, TimeUnit.MILLISECONDS)).thenReturn(false);

        assertThrows(DistributeLockException.class,
                () -> createAspect(defaultProperties).process(pjp));
        verify(pjp, never()).proceed();
    }

    @Test
    void tryLock_interrupted_throwsInterruptedException() throws Throwable {
        mockMethod("customWaitTime");
        when(rLock.tryLock(3000L, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException());

        assertThrows(InterruptedException.class,
                () -> createAspect(defaultProperties).process(pjp));
        verify(pjp, never()).proceed();
    }

    // ===== Lock Key Generation =====

    @Test
    void emptyKey_lockKeyEqualsScene() throws Throwable {
        mockMethod("defaultAll");
        mockLockHeld();
        when(pjp.proceed()).thenReturn("ok");

        createAspect(defaultProperties).process(pjp);

        verify(redissonClient).getLock("test-scene");
    }

    @Test
    void spelKey_lockKeyIncludesParsedValue() throws Throwable {
        mockMethod("withSpelKey", String.class);
        mockLockHeld();
        when(pjp.getArgs()).thenReturn(new Object[]{"order-123"});
        when(pjp.proceed()).thenReturn("ok");

        createAspect(defaultProperties).process(pjp);

        verify(redissonClient).getLock("order#order-123");
    }

    // ===== Key Prefix =====

    @Test
    void withKeyPrefix_lockKeyContainsPrefix() throws Throwable {
        mockMethod("defaultAll");
        mockLockHeld();
        when(pjp.proceed()).thenReturn("ok");

        DistributeLockProperties prefixProps = new DistributeLockProperties(null, null, "my-app");
        createAspect(prefixProps).process(pjp);

        verify(redissonClient).getLock("my-app:test-scene");
    }

    @Test
    void withKeyPrefix_spelKey_lockKeyContainsPrefixAndParsedValue() throws Throwable {
        mockMethod("withSpelKey", String.class);
        mockLockHeld();
        when(pjp.getArgs()).thenReturn(new Object[]{"order-123"});
        when(pjp.proceed()).thenReturn("ok");

        DistributeLockProperties prefixProps = new DistributeLockProperties(null, null, "my-app");
        createAspect(prefixProps).process(pjp);

        verify(redissonClient).getLock("my-app:order#order-123");
    }

    @Test
    void withNullKeyPrefix_lockKeyHasNoPrefix() throws Throwable {
        mockMethod("defaultAll");
        mockLockHeld();
        when(pjp.proceed()).thenReturn("ok");

        createAspect(defaultProperties).process(pjp);

        verify(redissonClient).getLock("test-scene");
    }

    @Test
    void withEmptyKeyPrefix_lockKeyHasNoPrefix() throws Throwable {
        mockMethod("defaultAll");
        mockLockHeld();
        when(pjp.proceed()).thenReturn("ok");

        DistributeLockProperties emptyPrefixProps = new DistributeLockProperties(null, null, "");
        createAspect(emptyPrefixProps).process(pjp);

        verify(redissonClient).getLock("test-scene");
    }

    @Test
    void withBlankKeyPrefix_lockKeyHasNoPrefix() throws Throwable {
        mockMethod("defaultAll");
        mockLockHeld();
        when(pjp.proceed()).thenReturn("ok");

        DistributeLockProperties blankPrefixProps = new DistributeLockProperties(null, null, "   ");
        createAspect(blankPrefixProps).process(pjp);

        verify(redissonClient).getLock("test-scene");
    }

    // ===== Config Priority =====

    @Test
    void resolveTime_annotationSet_usesAnnotationValue() throws Throwable {
        mockMethod("customBoth");
        mockLockHeld();
        when(pjp.proceed()).thenReturn("ok");
        when(rLock.tryLock(3000L, 5000L, TimeUnit.MILLISECONDS)).thenReturn(true);

        DistributeLockProperties globalProps = new DistributeLockProperties(9999L, 8888L, null);
        createAspect(globalProps).process(pjp);

        verify(rLock).tryLock(3000L, 5000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void resolveTime_annotationUnset_globalSet_usesGlobalValue() throws Throwable {
        mockMethod("defaultAll");
        mockLockHeld();
        when(pjp.proceed()).thenReturn("ok");
        when(rLock.tryLock(3000L, 5000L, TimeUnit.MILLISECONDS)).thenReturn(true);

        DistributeLockProperties globalProps = new DistributeLockProperties(5000L, 3000L, null);
        createAspect(globalProps).process(pjp);

        verify(rLock).tryLock(3000L, 5000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void resolveTime_annotationUnset_globalUnset_usesDefault() throws Throwable {
        mockMethod("defaultAll");
        mockLockHeld();
        when(pjp.proceed()).thenReturn("ok");

        createAspect(defaultProperties).process(pjp);

        verify(rLock).lock();
    }

    // ===== Lock Release =====

    @Test
    void unlock_calledOnSuccess() throws Throwable {
        mockMethod("defaultAll");
        mockLockHeld();
        when(pjp.proceed()).thenReturn("ok");

        createAspect(defaultProperties).process(pjp);

        verify(rLock).unlock();
    }

    @Test
    void unlock_calledOnBusinessException() throws Throwable {
        mockMethod("defaultAll");
        mockLockHeld();
        when(pjp.proceed()).thenThrow(new RuntimeException("business error"));

        assertThrows(RuntimeException.class,
                () -> createAspect(defaultProperties).process(pjp));

        verify(rLock).unlock();
    }

    // ===== Stub Methods for annotation scenarios =====

    @SuppressWarnings("unused")
    public static class StubMethods {

        @DistributeLock(scene = "test-scene")
        public void defaultAll() {}

        @DistributeLock(scene = "test-scene", leaseTime = 5000)
        public void customLeaseTime() {}

        @DistributeLock(scene = "test-scene", waitTime = 3000)
        public void customWaitTime() {}

        @DistributeLock(scene = "test-scene", waitTime = 3000, leaseTime = 5000)
        public void customBoth() {}

        @DistributeLock(scene = "order", key = "#orderId")
        public void withSpelKey(String orderId) {}
    }
}
