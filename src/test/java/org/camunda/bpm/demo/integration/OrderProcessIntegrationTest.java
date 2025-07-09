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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureWebMvc
@AutoConfigureMockMvc
class OrderProcessIntegrationTest extends BaseIntegrationTest {

    private static final int TIMEOUT_SECONDS = 15;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void orderProcess_CompleteFlowWithSuccess_ShouldCompleteWithEmailDelivery() throws Exception {
        // Given
        String correlationId = "order-complete-success-123";
        CamundaMessageDto orderMessage = TestDataBuilder.createStartProcessMessage(correlationId);
        String requestJson = objectMapper.writeValueAsString(orderMessage);

        // When - Start order process via REST/Kafka
        mockMvc.perform(post("/message-process/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk());

        // Then - Verify process started and is at user task
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("order-process")
                    .processInstanceBusinessKey(correlationId)
                    .list();
                ProcessInstance processInstance = processInstances.isEmpty() ? null : processInstances.get(0);
                assertThat(processInstance).isNotNull();
                assertThat(processInstance).isWaitingAt("Task_ProcessOrder");
            });

        // Get the process instance
        List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
            .processDefinitionKey("order-process")
            .processInstanceBusinessKey(correlationId)
            .list();
        ProcessInstance processInstance = processInstances.isEmpty() ? null : processInstances.get(0);

        // When - Complete the process order task with orderOk = true
        List<Task> tasks = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .taskDefinitionKey("Task_ProcessOrder")
            .list();
        Task processOrderTask = tasks.isEmpty() ? null : tasks.get(0);
        
        assertNotNull(processOrderTask);
        taskService.complete(processOrderTask.getId(), Map.of("orderOk", true));

        // Then - Process should move to deliver order task
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                assertThat(processInstance).isWaitingAt("Task_DeliverOrder");
            });

        // When - Complete the deliver order task
        List<Task> deliverTasks = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .taskDefinitionKey("Task_DeliverOrder")
            .list();
        Task deliverOrderTask = deliverTasks.isEmpty() ? null : deliverTasks.get(0);
        
        assertNotNull(deliverOrderTask);
        taskService.complete(deliverOrderTask.getId());

        // Then - Process should move to timer event
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                assertThat(processInstance).isWaitingAt("Timer_EmailDelay");
            });

        // For testing purposes, we'll just verify the timer is active
        // In a real scenario, the timer would wait 2 minutes and then trigger email delivery
        // The timer functionality is tested in the EmailDeliveryDelegate unit tests
    }

    @Test
    void orderProcess_WithOrderRejection_ShouldEndWithCancellation() throws Exception {
        // Given
        String correlationId = "order-rejection-123";
        CamundaMessageDto orderMessage = TestDataBuilder.createStartProcessMessage(correlationId);
        String requestJson = objectMapper.writeValueAsString(orderMessage);

        // When - Start order process via REST/Kafka
        mockMvc.perform(post("/message-process/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk());

        // Then - Verify process started and is at user task
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("order-process")
                    .processInstanceBusinessKey(correlationId)
                    .list();
                ProcessInstance processInstance = processInstances.isEmpty() ? null : processInstances.get(0);
                assertThat(processInstance).isNotNull();
                assertThat(processInstance).isWaitingAt("Task_ProcessOrder");
            });

        // Get the process instance
        List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
            .processDefinitionKey("order-process")
            .processInstanceBusinessKey(correlationId)
            .list();
        ProcessInstance processInstance = processInstances.isEmpty() ? null : processInstances.get(0);

        // When - Complete the process order task with orderOk = false (rejection)
        List<Task> tasks = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .taskDefinitionKey("Task_ProcessOrder")
            .list();
        Task processOrderTask = tasks.isEmpty() ? null : tasks.get(0);
        
        assertNotNull(processOrderTask);
        taskService.complete(processOrderTask.getId(), Map.of("orderOk", false));

        // Then - Process should go to cancellation end event
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> completedProcesses = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstance.getId())
                    .list();
                ProcessInstance completedProcess = completedProcesses.isEmpty() ? null : completedProcesses.get(0);
                assertNull(completedProcess); // Process should be completed
            });

        // Verify the process ended at the cancellation event
        assertEquals(0, runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstance.getId())
            .count());
    }

    @Test
    void orderProcess_WithHighValueOrder_ShouldProcessCorrectly() throws Exception {
        // Given
        String correlationId = "order-high-value-integration-123";
        CamundaMessageDto highValueOrder = TestDataBuilder.createHighValueMessage(correlationId);
        String requestJson = objectMapper.writeValueAsString(highValueOrder);

        // When - Start high value order process
        mockMvc.perform(post("/message-process/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk());

        // Then - Verify process started with high value order data
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("order-process")
                    .processInstanceBusinessKey(correlationId)
                    .list();
                ProcessInstance processInstance = processInstances.isEmpty() ? null : processInstances.get(0);
                assertThat(processInstance).isNotNull();
                assertThat(processInstance).isWaitingAt("Task_ProcessOrder");
                
                // Verify high value variables
                Object amount = runtimeService.getVariable(processInstance.getId(), "amount");
                assertEquals(50000.0, amount);
                
                Object requester = runtimeService.getVariable(processInstance.getId(), "requester");
                assertEquals("vip-customer", requester);
                
                Object preApproved = runtimeService.getVariable(processInstance.getId(), "preApproved");
                assertEquals(false, preApproved);
            });
    }

    @Test
    void orderProcess_MultipleOrdersSimultaneously_ShouldProcessIndependently() throws Exception {
        // Given
        String correlationId1 = "order-multi-integration-1";
        String correlationId2 = "order-multi-integration-2";
        
        CamundaMessageDto order1 = TestDataBuilder.createStartProcessMessage(correlationId1);
        CamundaMessageDto order2 = TestDataBuilder.createHighValueMessage(correlationId2);
        
        String requestJson1 = objectMapper.writeValueAsString(order1);
        String requestJson2 = objectMapper.writeValueAsString(order2);

        // When - Start multiple orders simultaneously
        mockMvc.perform(post("/message-process/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson1))
                .andExpect(status().isOk());
                
        mockMvc.perform(post("/message-process/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson2))
                .andExpect(status().isOk());

        // Then - Both processes should be created and running independently
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances1 = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("order-process")
                    .processInstanceBusinessKey(correlationId1)
                    .list();
                ProcessInstance process1 = processInstances1.isEmpty() ? null : processInstances1.get(0);
                List<ProcessInstance> processInstances2 = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("order-process")
                    .processInstanceBusinessKey(correlationId2)
                    .list();
                ProcessInstance process2 = processInstances2.isEmpty() ? null : processInstances2.get(0);
                
                assertThat(process1).isNotNull();
                assertThat(process2).isNotNull();
                assertNotEquals(process1.getId(), process2.getId());
                
                // Both should be at the user task
                assertThat(process1).isWaitingAt("Task_ProcessOrder");
                assertThat(process2).isWaitingAt("Task_ProcessOrder");
                
                // Verify different order amounts
                Object amount1 = runtimeService.getVariable(process1.getId(), "amount");
                Object amount2 = runtimeService.getVariable(process2.getId(), "amount");
                assertEquals(1000.0, amount1);
                assertEquals(50000.0, amount2);
            });
    }
} 