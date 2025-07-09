package org.camunda.bpm.demo.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryDelegateTest {

    @Mock
    private DelegateExecution execution;

    private EmailDeliveryDelegate emailDeliveryDelegate;

    @BeforeEach
    void setUp() {
        emailDeliveryDelegate = new EmailDeliveryDelegate();
    }

    @Test
    void execute_WithFullOrderInfo_ShouldSendEmailAndSetVariables() throws Exception {
        // Given
        when(execution.getBusinessKey()).thenReturn("ORDER-12345");
        when(execution.getVariable("customerEmail")).thenReturn("john.doe@example.com");
        when(execution.getVariable("orderId")).thenReturn("ORDER-12345");
        when(execution.getVariable("customerName")).thenReturn("John Doe");

        // When
        emailDeliveryDelegate.execute(execution);

        // Then
        verify(execution).setVariable("emailSent", true);
        verify(execution).setVariable(eq("emailSentTimestamp"), anyString());
        verify(execution).getVariable("customerEmail");
        verify(execution).getVariable("orderId");
        verify(execution).getVariable("customerName");
    }

    @Test
    void execute_WithMissingOrderInfo_ShouldUseDefaultsAndSendEmail() throws Exception {
        // Given
        when(execution.getBusinessKey()).thenReturn("ORDER-67890");
        when(execution.getVariable("customerEmail")).thenReturn(null);
        when(execution.getVariable("orderId")).thenReturn(null);
        when(execution.getVariable("customerName")).thenReturn(null);

        // When
        emailDeliveryDelegate.execute(execution);

        // Then
        verify(execution).setVariable("emailSent", true);
        verify(execution).setVariable(eq("emailSentTimestamp"), anyString());
        verify(execution, atLeastOnce()).getBusinessKey(); // Should use business key as fallback
    }

    @Test
    void execute_WithEmptyOrderInfo_ShouldUseDefaultsAndSendEmail() throws Exception {
        // Given
        when(execution.getBusinessKey()).thenReturn("ORDER-11111");
        when(execution.getVariable("customerEmail")).thenReturn("");
        when(execution.getVariable("orderId")).thenReturn("  ");
        when(execution.getVariable("customerName")).thenReturn("   ");

        // When
        emailDeliveryDelegate.execute(execution);

        // Then
        verify(execution).setVariable("emailSent", true);
        verify(execution).setVariable(eq("emailSentTimestamp"), anyString());
        verify(execution, atLeastOnce()).getBusinessKey(); // Should use business key as fallback for empty orderId
    }

    @Test
    void execute_WithNoBusinessKey_ShouldGenerateOrderIdFromProcessInstance() throws Exception {
        // Given
        when(execution.getBusinessKey()).thenReturn(null);
        when(execution.getProcessInstanceId()).thenReturn("process-instance-123");
        when(execution.getVariable("customerEmail")).thenReturn("test@example.com");
        when(execution.getVariable("orderId")).thenReturn(null);
        when(execution.getVariable("customerName")).thenReturn("Test User");

        // When
        emailDeliveryDelegate.execute(execution);

        // Then
        verify(execution).setVariable("emailSent", true);
        verify(execution).setVariable(eq("emailSentTimestamp"), anyString());
        verify(execution).getProcessInstanceId(); // Should use process instance ID for order generation
    }

    @Test
    void execute_WithPartialOrderInfo_ShouldCombineProvidedAndDefaultValues() throws Exception {
        // Given
        when(execution.getBusinessKey()).thenReturn("ORDER-22222");
        when(execution.getVariable("customerEmail")).thenReturn("jane.smith@example.com");
        when(execution.getVariable("orderId")).thenReturn("CUSTOM-ORDER-123");
        when(execution.getVariable("customerName")).thenReturn(null); // Missing name

        // When
        emailDeliveryDelegate.execute(execution);

        // Then
        verify(execution).setVariable("emailSent", true);
        verify(execution).setVariable(eq("emailSentTimestamp"), anyString());
        verify(execution).getVariable("customerEmail");
        verify(execution).getVariable("orderId");
        verify(execution).getVariable("customerName");
    }

    @Test
    void execute_ShouldAlwaysSetTimestamp() throws Exception {
        // Given
        when(execution.getBusinessKey()).thenReturn("ORDER-33333");

        // When
        emailDeliveryDelegate.execute(execution);

        // Then
        verify(execution).setVariable(eq("emailSentTimestamp"), argThat(timestamp -> 
            timestamp instanceof String && !((String) timestamp).isEmpty()
        ));
    }
} 