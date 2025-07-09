package org.camunda.bpm.demo.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderProcessingDelegateTest {

    @Mock
    private DelegateExecution execution;

    private OrderProcessingDelegate delegate;

    @BeforeEach
    void setUp() {
        delegate = new OrderProcessingDelegate();
    }

    @Test
    void execute_WithValidOrderData_ShouldSetAllVariables() throws Exception {
        // Given
        Map<String, Object> dto = new HashMap<>();
        dto.put("customerId", "customer-123");
        dto.put("orderValue", 500L);
        dto.put("customerEmail", "test@example.com");

        when(execution.getVariable("dto")).thenReturn(dto);
        when(execution.getProcessInstanceId()).thenReturn("process-123");

        // When
        delegate.execute(execution);

        // Then
        verify(execution).setVariable("customerId", "customer-123");
        verify(execution).setVariable("orderValue", 500L);
        verify(execution).setVariable("customerEmail", "test@example.com");
        verify(execution).setVariable("orderOk", true); // Should be true for orders < 1000
        verify(execution).setVariable(eq("orderProcessedAt"), any(String.class));
    }

    @Test
    void execute_WithHighValueOrder_ShouldSetOrderOkToFalse() throws Exception {
        // Given
        Map<String, Object> dto = new HashMap<>();
        dto.put("customerId", "customer-456");
        dto.put("orderValue", 1500L);
        dto.put("customerEmail", "highvalue@example.com");

        when(execution.getVariable("dto")).thenReturn(dto);
        when(execution.getProcessInstanceId()).thenReturn("process-456");

        // When
        delegate.execute(execution);

        // Then
        verify(execution).setVariable("customerId", "customer-456");
        verify(execution).setVariable("orderValue", 1500L);
        verify(execution).setVariable("customerEmail", "highvalue@example.com");
        verify(execution).setVariable("orderOk", false); // Should be false for orders >= 1000
    }

    @Test
    void execute_WithOrderValueAsInteger_ShouldConvertToLong() throws Exception {
        // Given
        Map<String, Object> dto = new HashMap<>();
        dto.put("customerId", "customer-789");
        dto.put("orderValue", 250); // Integer instead of Long
        dto.put("customerEmail", "int@example.com");

        when(execution.getVariable("dto")).thenReturn(dto);
        when(execution.getProcessInstanceId()).thenReturn("process-789");

        // When
        delegate.execute(execution);

        // Then
        verify(execution).setVariable("orderValue", 250L);
        verify(execution).setVariable("orderOk", true);
    }

    @Test
    void execute_WithOrderValueAsDouble_ShouldConvertToLong() throws Exception {
        // Given
        Map<String, Object> dto = new HashMap<>();
        dto.put("customerId", "customer-101");
        dto.put("orderValue", 299.99); // Double
        dto.put("customerEmail", "double@example.com");

        when(execution.getVariable("dto")).thenReturn(dto);
        when(execution.getProcessInstanceId()).thenReturn("process-101");

        // When
        delegate.execute(execution);

        // Then
        verify(execution).setVariable("orderValue", 299L);
        verify(execution).setVariable("orderOk", true);
    }

    @Test
    void execute_WithOrderValueAsString_ShouldParseToLong() throws Exception {
        // Given
        Map<String, Object> dto = new HashMap<>();
        dto.put("customerId", "customer-202");
        dto.put("orderValue", "750"); // String
        dto.put("customerEmail", "string@example.com");

        when(execution.getVariable("dto")).thenReturn(dto);
        when(execution.getProcessInstanceId()).thenReturn("process-202");

        // When
        delegate.execute(execution);

        // Then
        verify(execution).setVariable("orderValue", 750L);
        verify(execution).setVariable("orderOk", true);
    }

    @Test
    void execute_WithInvalidOrderValueString_ShouldNotSetOrderValue() throws Exception {
        // Given
        Map<String, Object> dto = new HashMap<>();
        dto.put("customerId", "customer-303");
        dto.put("orderValue", "invalid-number");
        dto.put("customerEmail", "invalid@example.com");

        when(execution.getVariable("dto")).thenReturn(dto);
        when(execution.getProcessInstanceId()).thenReturn("process-303");

        // When
        delegate.execute(execution);

        // Then
        verify(execution).setVariable("customerId", "customer-303");
        verify(execution).setVariable("customerEmail", "invalid@example.com");
        verify(execution, never()).setVariable(eq("orderValue"), any());
        verify(execution, never()).setVariable(eq("orderOk"), any());
    }

    @Test
    void execute_WithNullDto_ShouldSetDefaultValues() throws Exception {
        // Given
        when(execution.getVariable("dto")).thenReturn(null);
        when(execution.getProcessInstanceId()).thenReturn("process-null");

        // When
        delegate.execute(execution);

        // Then
        verify(execution).setVariable("orderOk", true);
        verify(execution).setVariable("customerId", "unknown");
        verify(execution).setVariable("orderValue", 0L);
        verify(execution).setVariable("customerEmail", "unknown@example.com");
    }

    @Test
    void execute_WithNonMapDto_ShouldSetDefaultValues() throws Exception {
        // Given
        when(execution.getVariable("dto")).thenReturn("not-a-map");
        when(execution.getProcessInstanceId()).thenReturn("process-notmap");

        // When
        delegate.execute(execution);

        // Then
        verify(execution).setVariable("orderOk", true);
        verify(execution).setVariable("customerId", "unknown");
        verify(execution).setVariable("orderValue", 0L);
        verify(execution).setVariable("customerEmail", "unknown@example.com");
    }

    @Test
    void execute_WithPartialData_ShouldSetOnlyAvailableFields() throws Exception {
        // Given
        Map<String, Object> dto = new HashMap<>();
        dto.put("customerId", "customer-partial");
        // Missing orderValue and customerEmail

        when(execution.getVariable("dto")).thenReturn(dto);
        when(execution.getProcessInstanceId()).thenReturn("process-partial");

        // When
        delegate.execute(execution);

        // Then
        verify(execution).setVariable("customerId", "customer-partial");
        verify(execution, never()).setVariable(eq("orderValue"), any());
        verify(execution, never()).setVariable(eq("customerEmail"), any());
        verify(execution, never()).setVariable(eq("orderOk"), any());
        verify(execution).setVariable(eq("orderProcessedAt"), any(String.class));
    }

    @Test
    void execute_WithNullValues_ShouldNotSetNullFields() throws Exception {
        // Given
        Map<String, Object> dto = new HashMap<>();
        dto.put("customerId", null);
        dto.put("orderValue", null);
        dto.put("customerEmail", null);

        when(execution.getVariable("dto")).thenReturn(dto);
        when(execution.getProcessInstanceId()).thenReturn("process-nullvalues");

        // When
        delegate.execute(execution);

        // Then
        verify(execution, never()).setVariable(eq("customerId"), any());
        verify(execution, never()).setVariable(eq("orderValue"), any());
        verify(execution, never()).setVariable(eq("customerEmail"), any());
        verify(execution, never()).setVariable(eq("orderOk"), any());
        verify(execution).setVariable(eq("orderProcessedAt"), any(String.class));
    }

    @Test
    void execute_ShouldAlwaysSetProcessingTimestamp() throws Exception {
        // Given
        Map<String, Object> dto = new HashMap<>();
        when(execution.getVariable("dto")).thenReturn(dto);
        when(execution.getProcessInstanceId()).thenReturn("process-timestamp");

        // When
        delegate.execute(execution);

        // Then
        ArgumentCaptor<String> timestampCaptor = ArgumentCaptor.forClass(String.class);
        verify(execution).setVariable(eq("orderProcessedAt"), timestampCaptor.capture());
        
        String timestamp = timestampCaptor.getValue();
        assertNotNull(timestamp);
        assertTrue(timestamp.contains("T")); // Should be LocalDateTime format
    }
} 