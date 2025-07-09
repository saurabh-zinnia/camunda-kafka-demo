package org.camunda.bpm.demo.delegate;

import org.camunda.bpm.demo.dto.CamundaMessageDto;
import org.camunda.bpm.demo.dto.MessageProcessDto;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageDelegateTest {

    @Mock
    private KafkaTemplate<String, CamundaMessageDto> kafkaTemplate;

    @Mock
    private DelegateExecution delegateExecution;

    @InjectMocks
    private MessageDelegate messageDelegate;

    private Map<String, Object> processVariables;

    @BeforeEach
    void setUp() {
        processVariables = new HashMap<>();
        processVariables.put("requester", "test-requester");
        processVariables.put("amount", 1000.0);
        processVariables.put("preApproved", true);
        processVariables.put("processed", false);

        when(delegateExecution.getProcessBusinessKey()).thenReturn("test-business-key");
        when(delegateExecution.getVariables()).thenReturn(processVariables);
        when(delegateExecution.getCurrentActivityId()).thenReturn("Activity_0tcd2jw");
    }

    @Test
    void execute_ShouldPublishMessageToKafka() throws Exception {
        // Given
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageDelegate.execute(delegateExecution);

        // Then
        verify(kafkaTemplate).send(eq("service-task-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        assertNotNull(capturedMessage);
        assertEquals("test-business-key", capturedMessage.getCorrelationId());
        
        MessageProcessDto dto = capturedMessage.getDto();
        assertNotNull(dto);
        assertEquals("test-requester", dto.getRequester());
        assertEquals(1000.0, dto.getAmount());
        assertTrue(dto.getPreApproved());
        assertFalse(dto.getProcessed());
    }

    @Test
    void execute_WithHighValueTransaction_ShouldPublishCorrectMessage() throws Exception {
        // Given
        processVariables.put("requester", "vip-customer");
        processVariables.put("amount", 50000.0);
        processVariables.put("preApproved", false);
        when(delegateExecution.getProcessBusinessKey()).thenReturn("high-value-key");
        when(delegateExecution.getVariables()).thenReturn(processVariables);
        
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageDelegate.execute(delegateExecution);

        // Then
        verify(kafkaTemplate).send(eq("service-task-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        assertEquals("high-value-key", capturedMessage.getCorrelationId());
        
        MessageProcessDto dto = capturedMessage.getDto();
        assertEquals("vip-customer", dto.getRequester());
        assertEquals(50000.0, dto.getAmount());
        assertFalse(dto.getPreApproved());
    }

    @Test
    void execute_WithProcessedTransaction_ShouldPublishProcessedMessage() throws Exception {
        // Given
        processVariables.put("processed", true);
        when(delegateExecution.getProcessBusinessKey()).thenReturn("processed-key");
        when(delegateExecution.getVariables()).thenReturn(processVariables);
        
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageDelegate.execute(delegateExecution);

        // Then
        verify(kafkaTemplate).send(eq("service-task-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        MessageProcessDto dto = capturedMessage.getDto();
        assertTrue(dto.getProcessed());
    }

    @Test
    void execute_WithEmptyVariables_ShouldHandleGracefully() throws Exception {
        // Given
        Map<String, Object> emptyVariables = new HashMap<>();
        when(delegateExecution.getVariables()).thenReturn(emptyVariables);
        when(delegateExecution.getProcessBusinessKey()).thenReturn("empty-key");
        
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageDelegate.execute(delegateExecution);

        // Then
        verify(kafkaTemplate).send(eq("service-task-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        assertEquals("empty-key", capturedMessage.getCorrelationId());
        
        MessageProcessDto dto = capturedMessage.getDto();
        assertNull(dto.getRequester());
        assertNull(dto.getAmount());
        assertNull(dto.getPreApproved());
        assertNull(dto.getProcessed());
    }

    @Test
    void execute_WithNullBusinessKey_ShouldHandleGracefully() throws Exception {
        // Given
        when(delegateExecution.getProcessBusinessKey()).thenReturn(null);
        
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageDelegate.execute(delegateExecution);

        // Then
        verify(kafkaTemplate).send(eq("service-task-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        assertNull(capturedMessage.getCorrelationId());
    }

    @Test
    void execute_WithPartialVariables_ShouldPublishPartialMessage() throws Exception {
        // Given
        Map<String, Object> partialVariables = new HashMap<>();
        partialVariables.put("requester", "partial-requester");
        partialVariables.put("amount", 500.0);
        // Missing preApproved and processed
        
        when(delegateExecution.getVariables()).thenReturn(partialVariables);
        when(delegateExecution.getProcessBusinessKey()).thenReturn("partial-key");
        
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageDelegate.execute(delegateExecution);

        // Then
        verify(kafkaTemplate).send(eq("service-task-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        MessageProcessDto dto = capturedMessage.getDto();
        assertEquals("partial-requester", dto.getRequester());
        assertEquals(500.0, dto.getAmount());
        assertNull(dto.getPreApproved());
        assertNull(dto.getProcessed());
    }

    @Test
    void execute_ShouldLogCurrentActivityId() throws Exception {
        // Given
        when(delegateExecution.getCurrentActivityId()).thenReturn("testActivityId");

        // When
        messageDelegate.execute(delegateExecution);

        // Then
        verify(delegateExecution).getCurrentActivityId();
        verify(kafkaTemplate).send(eq("service-task-message-topic"), any(CamundaMessageDto.class));
    }

    @Test
    void execute_WithDifferentActivityId_ShouldStillPublish() throws Exception {
        // Given
        when(delegateExecution.getCurrentActivityId()).thenReturn("Activity_0auuzip");
        
        // When
        messageDelegate.execute(delegateExecution);

        // Then
        verify(kafkaTemplate).send(eq("service-task-message-topic"), any(CamundaMessageDto.class));
    }
} 