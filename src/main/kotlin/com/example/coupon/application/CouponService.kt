package com.example.coupon.application

import com.example.coupon.api.dto.CreateCouponRequest
import com.example.coupon.domain.Coupon
import com.example.coupon.domain.CouponRepository
import com.example.coupon.domain.Issuance
import com.example.coupon.infrastructure.messaging.IssuanceRequestProducer
import com.example.coupon.infrastructure.messaging.IssuanceRequested
import com.example.coupon.support.CouponNotFoundException
import com.example.coupon.support.NotStartedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CouponService(
    private val couponRepository: CouponRepository,
    private val couponIssuer: CouponIssuer,
    private val issuanceRequestProducer: IssuanceRequestProducer
) {

    @Transactional
    fun createCoupon(request: CreateCouponRequest): Coupon {
        val coupon = couponRepository.save(
            Coupon(
                name = request.name,
                totalQuantity = request.totalQuantity,
                validityDays = request.validityDays,
                startsAt = request.startsAt,
            )
        )

        couponIssuer.initStock(coupon.id!!, coupon.totalQuantity)
        return coupon
    }

    // v0: 동시성 방어 의도적 제외. v1(02-coupon-concurrency-design.md)에서 해결.
    @Transactional
    fun issue(couponId: Long, userId: Long): Issuance {
        val coupon = couponRepository.findById(couponId)
            .orElseThrow() { CouponNotFoundException() }

        val now = LocalDateTime.now()

        if (!coupon.isBookingOpen(now)) {
            throw NotStartedException()
        }

        couponIssuer.tryIssue(couponId, userId)

        val expiresAt = now.plusDays(coupon.validityDays.toLong())

        issuanceRequestProducer.publish(
            IssuanceRequested(
                couponId = couponId,
                userId = userId,
                issuedAt = now,
                expiresAt = expiresAt,
            )
        )

        return Issuance(
            userId = userId,
            couponId = couponId,
            issuedAt = now,
            expiresAt = expiresAt,
        )

//        couponRepository.incrementIssuedQuantity(couponId)
//
//        return issuanceRepository.save(
//            Issuance(
//                userId = userId,
//                couponId = couponId,
//                issuedAt = now,
//                expiresAt = now.plusDays(coupon.validityDays.toLong()),
//            )
//        )
    }
}
