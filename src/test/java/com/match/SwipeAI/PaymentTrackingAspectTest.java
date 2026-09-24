package com.match.SwipeAI;

import com.match.SwipeAI.annotation.TrackPaymentTransaction;
import com.match.SwipeAI.aspect.PaymentTrackingAspect;
import com.match.SwipeAI.model.PaymentExecutionLog;
import com.match.SwipeAI.repository.PaymentExecutionLogRepository;
import com.match.SwipeAI.service.integration.PaymentExecutionLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentTrackingAspectTest {

    private PaymentExecutionLogRepository executionLogRepository;
    private PaymentExecutionLogService executionLogService;
    private PaymentTrackingAspect trackingAspect;

    @BeforeEach
    void setUp() {
        executionLogRepository = mock(PaymentExecutionLogRepository.class);
        executionLogService = new PaymentExecutionLogService(executionLogRepository);
        trackingAspect = new PaymentTrackingAspect(executionLogService);
    }

    @Test
    @DisplayName("AOP Aspect logs concise 1-line SUCCESS and null error message on successful execution")
    void testAopSuccessfulExecution() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("createOrder");

        TrackPaymentTransaction annotation = mock(TrackPaymentTransaction.class);
        when(annotation.action()).thenReturn("CREATE_UPI_ORDER");

        String orderId = "order_sachet_9823471";
        UUID userId = UUID.randomUUID();
        when(pjp.getArgs()).thenReturn(new Object[]{userId, "rohan@okaxis"});
        when(pjp.proceed()).thenReturn(ResponseEntity.ok(Map.of("orderId", orderId, "status", "pending")));

        Object result = trackingAspect.trackTransaction(pjp, annotation);
        assertNotNull(result);

        ArgumentCaptor<PaymentExecutionLog> logCaptor = ArgumentCaptor.forClass(PaymentExecutionLog.class);
        verify(executionLogRepository).save(logCaptor.capture());

        PaymentExecutionLog saved = logCaptor.getValue();
        assertEquals("SUCCESS", saved.getStatus());
        assertEquals("CREATE_UPI_ORDER", saved.getAction());
        assertEquals(orderId, saved.getOrderId());
        assertEquals(userId, saved.getUserId());
        assertTrue(saved.getSummary().startsWith("SUCCESS: [CREATE_UPI_ORDER] orderId=order_sachet_9823471"));
        assertNull(saved.getErrorMessage(), "Success log must not contain an error message");
        assertTrue(saved.getExecutionTimeMs() >= 0);
    }

    @Test
    @DisplayName("AOP Aspect captures concise exception message only without stack trace on FAILURE")
    void testAopFailedExecutionOnlyShortException() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("verifyIapPurchase");

        TrackPaymentTransaction annotation = mock(TrackPaymentTransaction.class);
        when(annotation.action()).thenReturn("IAP_VERIFY");

        String orderId = "iap_order_883192";
        when(pjp.getArgs()).thenReturn(new Object[]{orderId});
        when(pjp.proceed()).thenThrow(new IllegalArgumentException("Invalid Google Play receipt signature"));

        // Must rethrow original exception
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                trackingAspect.trackTransaction(pjp, annotation));
        assertEquals("Invalid Google Play receipt signature", ex.getMessage());

        ArgumentCaptor<PaymentExecutionLog> logCaptor = ArgumentCaptor.forClass(PaymentExecutionLog.class);
        verify(executionLogRepository).save(logCaptor.capture());

        PaymentExecutionLog saved = logCaptor.getValue();
        assertEquals("FAILED", saved.getStatus());
        assertEquals("IAP_VERIFY", saved.getAction());
        assertEquals(orderId, saved.getOrderId());
        assertEquals("Invalid Google Play receipt signature", saved.getErrorMessage());
        assertFalse(saved.getErrorMessage().contains("at com.match.SwipeAI"), "Must NOT contain Java stack traces");
        assertTrue(saved.getSummary().contains("FAILED: [IAP_VERIFY]"));
    }
}
