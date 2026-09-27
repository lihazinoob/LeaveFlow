package com.misl.leaveflow.auth.refresh;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Deletes expired refresh tokens once a day so the table does not grow forever.
 * Revoked but unexpired tokens are kept on purpose: they are needed to detect token reuse.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "${app.jwt.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void deleteExpiredTokens() {
        int deleted = refreshTokenRepository.deleteAllExpiredBefore(Instant.now());
        log.info("Refresh token cleanup removed {} expired token(s)", deleted);
    }
}
