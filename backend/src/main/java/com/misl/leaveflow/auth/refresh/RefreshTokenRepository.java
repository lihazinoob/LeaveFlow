package com.misl.leaveflow.auth.refresh;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revokes every still-active token of one employee (logs them out on all devices). */
    @Modifying
    @Query("""
            update RefreshToken t set t.revokedAt = :now, t.revokedReason = :reason
            where t.employee.id = :employeeId and t.revokedAt is null
            """)
    int revokeAllActiveByEmployeeId(@Param("employeeId") Long employeeId, @Param("now") Instant now,
                                    @Param("reason") RevokedReason reason);

    /** Housekeeping: expired tokens can never be used again, so their rows can go. */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    int deleteAllExpiredBefore(@Param("now") Instant now);
}
