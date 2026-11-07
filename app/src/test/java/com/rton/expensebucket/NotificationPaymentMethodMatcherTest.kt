package com.rton.expensebucket

import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.ocr.NotificationPaymentMethodMatcher
import org.junit.Assert.*
import org.junit.Test

class NotificationPaymentMethodMatcherTest {
    private val sports = PaymentMethod(id = 7, name = "永豐 Sports", icon = "sinopac", type = "credit")

    @Test
    fun matchesSportCardDespiteSpacesCaseAndPlural() {
        listOf("**", "••").forEach { mask ->
            assertEquals(7L, NotificationPaymentMethodMatcher.findPaymentMethodId(
                "TEST $413.00 (使用 永豐SPORT卡 ${mask}1234 付款)", listOf(sports)
            ))
        }
    }

    @Test
    fun keepsUnmatchedOrAmbiguousCardsUnset() {
        val text = "TEST $413.00 (使用 永豐SPORT卡 **1234 付款)"
        assertNull(NotificationPaymentMethodMatcher.findPaymentMethodId(text, emptyList()))
        assertNull(NotificationPaymentMethodMatcher.findPaymentMethodId(text, listOf(sports.copy(name = "台新 Sports"))))
        assertNull(NotificationPaymentMethodMatcher.findPaymentMethodId(text, listOf(sports, sports.copy(id = 8))))
        assertEquals(7L, NotificationPaymentMethodMatcher.findPaymentMethodId(text, listOf(sports.copy(type = "epay"))))
    }

    @Test
    fun doesNotInferCardFromGenericMentionInNotification() {
        assertNull(NotificationPaymentMethodMatcher.findPaymentMethodId("永豐SPORT卡消費通知 $413", listOf(sports)))
    }
}
