package com.example.coupon.infrastructure.messaging

import com.example.coupon.domain.CouponRepository
import com.example.coupon.domain.Issuance
import com.example.coupon.domain.IssuanceRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class IssuanceTransactionalWriter(
    private val issuanceRepository: IssuanceRepository,
    private val couponRepository: CouponRepository,
) {
    @Transactional
    fun insertAndIncrement(event: IssuanceRequested) {

        if(issuanceRepository.existsByUserIdAndCouponId(event.userId, event.couponId)) return

        issuanceRepository.save(
            Issuance(
                userId = event.userId,
                couponId = event.couponId,
                issuedAt = event.issuedAt,
                expiresAt = event.expiresAt,
            )
        )

        couponRepository.incrementIssuedQuantity(event.couponId)
    }

    fun isAlreadyApplied(event: IssuanceRequested): Boolean =
        issuanceRepository.existsByUserIdAndCouponId(event.userId, event.couponId)
}
