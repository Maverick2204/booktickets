package com.bookings.booktickets.service.lock;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "booking.lock.enabled", havingValue = "true", matchIfMissing = true)
public class RedissonLockService implements DistributedLockService {

    private final RedissonClient redissonClient;

    public RedissonLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public Optional<LockHandle> tryAcquire(String key, long waitSeconds, long leaseSeconds) throws InterruptedException {
        RLock lock = redissonClient.getLock(key);
        boolean acquired = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
        if (!acquired) {
            return Optional.empty();
        }
        return Optional.of(() -> {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        });
    }
}
