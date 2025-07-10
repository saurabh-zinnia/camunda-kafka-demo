package org.camunda.bpm.demo.coverage;

import org.camunda.bpm.demo.config.BaseIntegrationTest;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.community.process_test_coverage.spring_test.platform7.ProcessEngineCoverageConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive process coverage test for all BPMN processes
 * This test generates HTML reports showing which parts of the BPMN processes are covered
 */
@Import(ProcessEngineCoverageConfiguration.class)
@Deployment(resources = {"process.bpmn", "order-process.bpmn", "data-format.bpmn"})
public class DataFormatProcessCoverageTest extends BaseIntegrationTest {

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Test
    public void testDataFormatProcess_XmlPath_ShouldGenerateCoverageReport() {
        // Given: Customer data for XML processing
        Map<String, Object> customerData = createCustomerData("John", "Doe", "male", 30L, true);
        
        // When: Process is started
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("DataformatDemoProcess", customerData);
        
        // Then: Process should be at user task
        assertThat(processInstance).isWaitingAt("Task_1vjuj1c");
        
        // And: Complete user task with XML format selection
        Task userTask = taskService.createTaskQuery().singleResult();
        taskService.complete(userTask.getId(), withVariables("dataFormat", "xml"));
        
        // Then: Process should complete successfully
        assertThat(processInstance).isEnded();
        assertThat(processInstance).hasPassed("Task_03zh96w", "Task_1p179ep", "EndEvent_1nrs79a");
    }

    @Test
    public void testDataFormatProcess_JsonPath_ShouldGenerateCoverageReport() {
        // Given: Customer data for JSON processing
        Map<String, Object> customerData = createCustomerData("Jane", "Smith", "female", 25L, true);
        
        // When: Process is started
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("DataformatDemoProcess", customerData);
        
        // Then: Process should be at user task
        assertThat(processInstance).isWaitingAt("Task_1vjuj1c");
        
        // And: Complete user task with JSON format selection
        Task userTask = taskService.createTaskQuery().singleResult();
        taskService.complete(userTask.getId(), withVariables("dataFormat", "json"));
        
        // Then: Process should complete successfully
        assertThat(processInstance).isEnded();
        assertThat(processInstance).hasPassed("Task_1x6a2xs", "Task_1p179ep", "EndEvent_1nrs79a");
    }

    @Test
    public void testDataFormatProcess_InvalidFormat_ShouldGenerateCoverageReport() {
        // Given: Customer data with invalid format
        Map<String, Object> customerData = createCustomerData("Bob", "Johnson", "male", 40L, true);
        
        // When: Process is started
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("DataformatDemoProcess", customerData);
        
        // Then: Process should be at user task
        assertThat(processInstance).isWaitingAt("Task_1vjuj1c");
        
        // And: Complete user task with invalid format selection
        Task userTask = taskService.createTaskQuery().singleResult();
        taskService.complete(userTask.getId(), withVariables("dataFormat", "invalid"));
        
        // Then: Process should end at the default end event
        assertThat(processInstance).isEnded();
        assertThat(processInstance).hasPassed("EndEvent_1nrs79a");
    }

    @Test
    public void testOrderProcess_ShouldGenerateCoverageReport() {
        // Given: Order data
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("orderId", "ORD-001");
        orderData.put("customerId", "CUST-001");
        orderData.put("amount", 100.0);
        orderData.put("product", "Widget");
        
        // When: Process is started
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("order-process", orderData);
        
        // Then: Process should complete (assuming it's a simple process)
        assertThat(processInstance).isEnded();
    }

    @Test
    public void testGenericProcess_ShouldGenerateCoverageReport() {
        // Given: Generic process data
        Map<String, Object> processData = new HashMap<>();
        processData.put("messageContent", "Test message");
        processData.put("processId", "PROC-001");
        
        // When: Process is started
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("KafkaDemo", processData);
        
        // Then: Process should complete
        assertThat(processInstance).isEnded();
    }

    @Test
    public void testCompleteProcessCoverage_AllPaths_ShouldGenerateComprehensiveReport() {
        // Test multiple scenarios to achieve maximum coverage
        
        // Scenario 1: XML format with young customer
        Map<String, Object> youngCustomer = createCustomerData("Alice", "Young", "female", 18L, true);
        ProcessInstance proc1 = runtimeService.startProcessInstanceByKey("DataformatDemoProcess", youngCustomer);
        Task task1 = taskService.createTaskQuery().processInstanceId(proc1.getId()).singleResult();
        taskService.complete(task1.getId(), withVariables("dataFormat", "xml"));
        
        // Scenario 2: JSON format with older customer
        Map<String, Object> olderCustomer = createCustomerData("Bob", "Older", "male", 65L, true);
        ProcessInstance proc2 = runtimeService.startProcessInstanceByKey("DataformatDemoProcess", olderCustomer);
        Task task2 = taskService.createTaskQuery().processInstanceId(proc2.getId()).singleResult();
        taskService.complete(task2.getId(), withVariables("dataFormat", "json"));
        
        // Scenario 3: Invalid customer data
        Map<String, Object> invalidCustomer = createCustomerData("", "", "unknown", 0L, false);
        ProcessInstance proc3 = runtimeService.startProcessInstanceByKey("DataformatDemoProcess", invalidCustomer);
        Task task3 = taskService.createTaskQuery().processInstanceId(proc3.getId()).singleResult();
        taskService.complete(task3.getId(), withVariables("dataFormat", "xml"));
        
        // Verify all processes completed
        assertThat(proc1).isEnded();
        assertThat(proc2).isEnded();
        assertThat(proc3).isEnded();
    }

    private Map<String, Object> createCustomerData(String firstName, String lastName, String gender, Long age, boolean isValid) {
        Map<String, Object> customerData = new HashMap<>();
        customerData.put("firstname", firstName);
        customerData.put("lastname", lastName);
        customerData.put("gender", gender);
        customerData.put("age", age);
        customerData.put("isValid", isValid);
        customerData.put("validationDate", LocalDate.now().toString());
        return customerData;
    }
} 