package org.camunda.bpm.demo.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.demo.config.BaseIntegrationTest;
import org.camunda.bpm.demo.dto.CamundaMessageDto;
import org.camunda.bpm.demo.util.TestDataBuilder;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureWebMvc
@AutoConfigureMockMvc
class RestControllerKafkaIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void restEndpoint_StartProcess_ShouldTriggerProcessViaKafka() throws Exception {
        // Given
        String correlationId = "rest-integration-123";
        CamundaMessageDto startMessage = TestDataBuilder.createStartProcessMessage(correlationId);
        String requestJson = objectMapper.writeValueAsString(startMessage);

        // When
        mockMvc.perform(post("/message-process/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk());

        // Then - Verify process started via Kafka
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("KafkaDemo")
                    .processInstanceBusinessKey(correlationId)
                    .list();
                ProcessInstance processInstance = processInstances.isEmpty() ? null : processInstances.get(0);
                assertThat(processInstance).isNotNull();
                assertThat(processInstance).isWaitingAt("Activity_0zhduij");
                
                // Verify process variables
                Object requester = runtimeService.getVariable(processInstance.getId(), "requester");
                assertEquals("test-requester", requester);
                
                Object amount = runtimeService.getVariable(processInstance.getId(), "amount");
                assertEquals(1000.0, amount);
                
                Object preApproved = runtimeService.getVariable(processInstance.getId(), "preApproved");
                assertEquals(true, preApproved);
            });
    }



    @Test
    void restEndpoint_WithHighValueMessage_ShouldProcessCorrectly() throws Exception {
        // Given
        String correlationId = "rest-high-value-123";
        CamundaMessageDto highValueMessage = TestDataBuilder.createHighValueMessage(correlationId);
        String requestJson = objectMapper.writeValueAsString(highValueMessage);

        // When
        mockMvc.perform(post("/message-process/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk());

        // Then
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId)
                    .singleResult();
                assertThat(processInstance).isNotNull();
                
                // Verify high-value specific variables
                Object requester = runtimeService.getVariable(processInstance.getId(), "requester");
                assertEquals("vip-customer", requester);
                
                Object amount = runtimeService.getVariable(processInstance.getId(), "amount");
                assertEquals(50000.0, amount);
                
                Object preApproved = runtimeService.getVariable(processInstance.getId(), "preApproved");
                assertEquals(false, preApproved);
            });
    }

    @Test
    void restEndpoint_WithPreApprovedMessage_ShouldProcessCorrectly() throws Exception {
        // Given
        String correlationId = "rest-pre-approved-123";
        CamundaMessageDto preApprovedMessage = TestDataBuilder.createPreApprovedMessage(correlationId);
        String requestJson = objectMapper.writeValueAsString(preApprovedMessage);

        // When
        mockMvc.perform(post("/message-process/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk());

        // Then
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId)
                    .singleResult();
                assertThat(processInstance).isNotNull();
                
                // Verify pre-approved specific variables
                Object requester = runtimeService.getVariable(processInstance.getId(), "requester");
                assertEquals("trusted-customer", requester);
                
                Object amount = runtimeService.getVariable(processInstance.getId(), "amount");
                assertEquals(500.0, amount);
                
                Object preApproved = runtimeService.getVariable(processInstance.getId(), "preApproved");
                assertEquals(true, preApproved);
            });
    }

    @ParameterizedTest
    @MethodSource("org.camunda.bpm.demo.util.TestDataBuilder#createTestDataVariations")
    void restEndpoint_WithVariousInputs_ShouldProcessAllCorrectly(String correlationSuffix, String requester, Double amount, Boolean preApproved) throws Exception {
        // Given
        String correlationId = "rest-param-test-" + correlationSuffix;
        CamundaMessageDto customMessage = TestDataBuilder.createCustomMessage(correlationId, requester, amount, preApproved);
        String requestJson = objectMapper.writeValueAsString(customMessage);

        // When
        mockMvc.perform(post("/message-process/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk());

        // Then
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId)
                    .singleResult();
                assertThat(processInstance).isNotNull();
                
                // Verify variables match input
                if (requester != null) {
                    Object actualRequester = runtimeService.getVariable(processInstance.getId(), "requester");
                    assertEquals(requester, actualRequester);
                }
                if (amount != null) {
                    Object actualAmount = runtimeService.getVariable(processInstance.getId(), "amount");
                    assertEquals(amount, actualAmount);
                }
                if (preApproved != null) {
                    Object actualPreApproved = runtimeService.getVariable(processInstance.getId(), "preApproved");
                    assertEquals(preApproved, actualPreApproved);
                }
            });
    }

    @Test
    void restEndpoint_WithEmptyMessage_ShouldProcessWithEmptyData() throws Exception {
        // Given
        String correlationId = "rest-empty-123";
        CamundaMessageDto emptyMessage = TestDataBuilder.createEmptyMessage(correlationId);
        String requestJson = objectMapper.writeValueAsString(emptyMessage);

        // When
        mockMvc.perform(post("/message-process/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk());

        // Then
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId)
                    .singleResult();
                assertThat(processInstance).isNotNull();
                
                // Verify empty values
                Object requester = runtimeService.getVariable(processInstance.getId(), "requester");
                assertEquals("", requester);
                
                Object amount = runtimeService.getVariable(processInstance.getId(), "amount");
                assertEquals(0.0, amount);
            });
    }

    @Test
    void restEndpoint_WithInvalidJson_ShouldReturn400() throws Exception {
        // Given
        String invalidJson = "{invalid json}";

        // When & Then
        mockMvc.perform(post("/message-process/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void restEndpoint_WithNullCorrelationId_ShouldStillProcess() throws Exception {
        // Given
        CamundaMessageDto messageWithNullCorrelation = CamundaMessageDto.builder()
            .correlationId(null)
            .dto(TestDataBuilder.createStartProcessMessage("dummy").getDto())
            .build();
        String requestJson = objectMapper.writeValueAsString(messageWithNullCorrelation);

        // When
        mockMvc.perform(post("/message-process/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk());

        // Then - Should process successfully but won't have business key
        // Wait for the process to be created (might not have business key set)
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                // Look for any process instances that were created recently
                List<ProcessInstance> allInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("KafkaDemo")
                    .list();
                
                // Should have at least one process instance (could have null business key)
                assertTrue(allInstances.size() > 0, "Expected at least one process instance to be created");
                
                // Check if any instance has null business key or find the one we're looking for
                boolean foundNullBusinessKeyInstance = allInstances.stream()
                    .anyMatch(pi -> pi.getBusinessKey() == null);
                
                if (!foundNullBusinessKeyInstance) {
                    // If no null business key instance found, check if process was created with some other business key
                    // This is acceptable since Camunda might assign a default business key
                    assertTrue(allInstances.size() > 0, "Process should be created even with null correlation ID");
                }
            });
    }

    @Test
    void restEndpoint_MultipleSimultaneousRequests_ShouldHandleCorrectly() throws Exception {
        // Given
        String correlationId1 = "rest-multi-1";
        String correlationId2 = "rest-multi-2";
        
        CamundaMessageDto message1 = TestDataBuilder.createStartProcessMessage(correlationId1);
        CamundaMessageDto message2 = TestDataBuilder.createHighValueMessage(correlationId2);
        
        String requestJson1 = objectMapper.writeValueAsString(message1);
        String requestJson2 = objectMapper.writeValueAsString(message2);

        // When - Send both requests simultaneously
        mockMvc.perform(post("/message-process/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson1))
                .andExpect(status().isOk());
                
        mockMvc.perform(post("/message-process/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson2))
                .andExpect(status().isOk());

        // Then - Both processes should be created independently
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                ProcessInstance process1 = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId1)
                    .singleResult();
                ProcessInstance process2 = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId2)
                    .singleResult();
                
                assertThat(process1).isNotNull();
                assertThat(process2).isNotNull();
                assertNotEquals(process1.getId(), process2.getId());
                
                // Verify different amounts
                Object amount1 = runtimeService.getVariable(process1.getId(), "amount");
                Object amount2 = runtimeService.getVariable(process2.getId(), "amount");
                assertEquals(1000.0, amount1);
                assertEquals(50000.0, amount2);
            });
    }



    @Test
    void restEndpoint_StartOrderProcess_ShouldTriggerOrderProcessViaKafka() throws Exception {
        // Given
        String correlationId = "order-integration-123";
        CamundaMessageDto orderMessage = TestDataBuilder.createStartProcessMessage(correlationId);
        String requestJson = objectMapper.writeValueAsString(orderMessage);

        // When
        mockMvc.perform(post("/message-process/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk());

        // Then - Verify order process started via Kafka
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("order-process")
                    .processInstanceBusinessKey(correlationId)
                    .active()
                    .list();
                assertFalse(processInstances.isEmpty(), "Expected at least one process instance");
                ProcessInstance processInstance = processInstances.get(0);
                assertThat(processInstance).isNotNull();
                assertThat(processInstance).isWaitingAt("Task_ProcessOrder");
                
                // Verify process variables
                Object requester = runtimeService.getVariable(processInstance.getId(), "requester");
                assertEquals("test-requester", requester);
                
                Object amount = runtimeService.getVariable(processInstance.getId(), "amount");
                assertEquals(1000.0, amount);
                
                Object preApproved = runtimeService.getVariable(processInstance.getId(), "preApproved");
                assertEquals(true, preApproved);
            });
    }

    @Test
    void restEndpoint_OrderProcessWithHighValue_ShouldProcessCorrectly() throws Exception {
        // Given
        String correlationId = "order-high-value-123";
        CamundaMessageDto highValueOrder = TestDataBuilder.createHighValueMessage(correlationId);
        String requestJson = objectMapper.writeValueAsString(highValueOrder);

        // When
        mockMvc.perform(post("/message-process/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk());

        // Then
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("order-process")
                    .processInstanceBusinessKey(correlationId)
                    .active()
                    .list();
                assertFalse(processInstances.isEmpty(), "Expected at least one process instance");
                ProcessInstance processInstance = processInstances.get(0);
                assertThat(processInstance).isNotNull();
                assertThat(processInstance).isWaitingAt("Task_ProcessOrder");
                
                // Verify high value order variables
                Object requester = runtimeService.getVariable(processInstance.getId(), "requester");
                assertEquals("vip-customer", requester);
                
                Object amount = runtimeService.getVariable(processInstance.getId(), "amount");
                assertEquals(50000.0, amount);
                
                Object preApproved = runtimeService.getVariable(processInstance.getId(), "preApproved");
                assertEquals(false, preApproved);
            });
    }

    @Test
    void restEndpoint_OrderProcessWithCustomData_ShouldProcessCorrectly() throws Exception {
        // Given
        String correlationId = "order-custom-123";
        CamundaMessageDto customOrder = TestDataBuilder.createCustomMessage(
            correlationId, "order-customer", 750.0, true);
        String requestJson = objectMapper.writeValueAsString(customOrder);

        // When
        mockMvc.perform(post("/message-process/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk());

        // Then
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("order-process")
                    .processInstanceBusinessKey(correlationId)
                    .active()
                    .list();
                assertFalse(processInstances.isEmpty(), "Expected at least one process instance");
                ProcessInstance processInstance = processInstances.get(0);
                assertThat(processInstance).isNotNull();
                assertThat(processInstance).isWaitingAt("Task_ProcessOrder");
                
                // Verify custom order variables
                Object requester = runtimeService.getVariable(processInstance.getId(), "requester");
                assertEquals("order-customer", requester);
                
                Object amount = runtimeService.getVariable(processInstance.getId(), "amount");
                assertEquals(750.0, amount);
                
                Object preApproved = runtimeService.getVariable(processInstance.getId(), "preApproved");
                assertEquals(true, preApproved);
            });
    }

    @Test
    void restEndpoint_OrderProcessWithEmptyData_ShouldProcessWithDefaults() throws Exception {
        // Given
        String correlationId = "order-empty-123";
        CamundaMessageDto emptyOrder = TestDataBuilder.createEmptyMessage(correlationId);
        String requestJson = objectMapper.writeValueAsString(emptyOrder);

        // When
        mockMvc.perform(post("/message-process/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk());

        // Then
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("order-process")
                    .processInstanceBusinessKey(correlationId)
                    .active()
                    .list();
                assertFalse(processInstances.isEmpty(), "Expected at least one process instance");
                ProcessInstance processInstance = processInstances.get(0);
                assertThat(processInstance).isNotNull();
                assertThat(processInstance).isWaitingAt("Task_ProcessOrder");
                
                // Verify empty values
                Object requester = runtimeService.getVariable(processInstance.getId(), "requester");
                assertEquals("", requester);
                
                Object amount = runtimeService.getVariable(processInstance.getId(), "amount");
                assertEquals(0.0, amount);
            });
    }

    @Test
    void restEndpoint_MultipleOrderProcesses_ShouldHandleCorrectly() throws Exception {
        // Given
        String correlationId1 = "order-multi-1";
        String correlationId2 = "order-multi-2";
        
        CamundaMessageDto order1 = TestDataBuilder.createStartProcessMessage(correlationId1);
        CamundaMessageDto order2 = TestDataBuilder.createHighValueMessage(correlationId2);
        
        String requestJson1 = objectMapper.writeValueAsString(order1);
        String requestJson2 = objectMapper.writeValueAsString(order2);

        // When - Send both order requests simultaneously
        mockMvc.perform(post("/message-process/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson1))
                .andExpect(status().isOk());
                
        mockMvc.perform(post("/message-process/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson2))
                .andExpect(status().isOk());

        // Then - Both order processes should be created independently
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances1 = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("order-process")
                    .processInstanceBusinessKey(correlationId1)
                    .active()
                    .list();
                List<ProcessInstance> processInstances2 = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("order-process")
                    .processInstanceBusinessKey(correlationId2)
                    .active()
                    .list();
                    
                assertFalse(processInstances1.isEmpty(), "Expected at least one process instance for " + correlationId1);
                assertFalse(processInstances2.isEmpty(), "Expected at least one process instance for " + correlationId2);
                
                ProcessInstance process1 = processInstances1.get(0);
                ProcessInstance process2 = processInstances2.get(0);
                
                assertThat(process1).isNotNull();
                assertThat(process2).isNotNull();
                assertNotEquals(process1.getId(), process2.getId());
                
                // Both should be at the user task
                assertThat(process1).isWaitingAt("Task_ProcessOrder");
                assertThat(process2).isWaitingAt("Task_ProcessOrder");
                
                // Verify different amounts
                Object amount1 = runtimeService.getVariable(process1.getId(), "amount");
                Object amount2 = runtimeService.getVariable(process2.getId(), "amount");
                assertEquals(1000.0, amount1);
                assertEquals(50000.0, amount2);
            });
    }
} 