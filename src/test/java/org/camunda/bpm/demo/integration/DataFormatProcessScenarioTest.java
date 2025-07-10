package org.camunda.bpm.demo.integration;

import org.camunda.bpm.demo.config.BaseIntegrationTest;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.scenario.ProcessScenario;
import org.camunda.bpm.scenario.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.bpm.scenario.Scenario.run;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BDD-style scenario tests for the Data Format Process using Camunda Platform Scenario
 */
@ExtendWith(MockitoExtension.class)
@Deployment(resources = "data-format.bpmn")
public class DataFormatProcessScenarioTest extends BaseIntegrationTest {

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Mock(lenient = true)
    private ProcessScenario dataFormatProcess;

    private static final String PROCESS_KEY = "DataformatDemoProcess";
    private static final String START_EVENT_ID = "StartEvent_1";
    private static final String USER_TASK_SELECT_FORMAT = "Task_1vjuj1c";
    private static final String GATEWAY_DATAFORMAT = "ExclusiveGateway_06znf1b";
    private static final String TASK_CREATE_XML = "Task_03zh96w";
    private static final String TASK_CREATE_JSON = "Task_1x6a2xs";
    private static final String TASK_LOG_CUSTOMER = "Task_1p179ep";
    private static final String END_EVENT_ID = "EndEvent_1nrs79a";

    @BeforeEach
    public void defineDefaultScenario() {
        // Define default happy path behavior that can be overridden in specific tests
        when(dataFormatProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.complete(withVariables("dataFormat", "xml")));
        
        when(dataFormatProcess.waitsAtServiceTask(TASK_CREATE_XML))
            .thenReturn(task -> task.complete());
            
        when(dataFormatProcess.waitsAtServiceTask(TASK_CREATE_JSON))
            .thenReturn(task -> task.complete());
            
        when(dataFormatProcess.waitsAtServiceTask(TASK_LOG_CUSTOMER))
            .thenReturn(task -> task.complete());
    }

    @Test
    public void testHappyPath_XmlFormat_ShouldCompleteSuccessfully() {
        // Given: A customer with complete data choosing XML format
        Map<String, Object> customerData = createCompleteCustomerData();
        
        // When: Process is started and XML format is selected
        when(dataFormatProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> {
                task.complete(withVariables("dataFormat", "xml"));
            });

        // Then: Process should complete successfully through XML path
        run(dataFormatProcess)
            .startByKey(PROCESS_KEY, customerData)
            .execute();

        // Verify the process flow
        verify(dataFormatProcess).hasStarted(START_EVENT_ID);
        verify(dataFormatProcess).hasCompleted(USER_TASK_SELECT_FORMAT);
        verify(dataFormatProcess).hasCompleted(TASK_CREATE_XML);
        verify(dataFormatProcess).hasCompleted(TASK_LOG_CUSTOMER);
        verify(dataFormatProcess).hasFinished(END_EVENT_ID);
        
        // Verify XML path was taken, not JSON
        verify(dataFormatProcess, never()).hasCompleted(TASK_CREATE_JSON);
    }

    @Test
    public void testHappyPath_JsonFormat_ShouldCompleteSuccessfully() {
        // Given: A customer with complete data choosing JSON format
        Map<String, Object> customerData = createCompleteCustomerData();
        
        // When: Process is started and JSON format is selected
        when(dataFormatProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> {
                task.complete(withVariables("dataFormat", "json"));
            });

        // Then: Process should complete successfully through JSON path
        run(dataFormatProcess)
            .startByKey(PROCESS_KEY, customerData)
            .execute();

        // Verify the process flow
        verify(dataFormatProcess).hasStarted(START_EVENT_ID);
        verify(dataFormatProcess).hasCompleted(USER_TASK_SELECT_FORMAT);
        verify(dataFormatProcess).hasCompleted(TASK_CREATE_JSON);
        verify(dataFormatProcess).hasCompleted(TASK_LOG_CUSTOMER);
        verify(dataFormatProcess).hasFinished(END_EVENT_ID);
        
        // Verify JSON path was taken, not XML
        verify(dataFormatProcess, never()).hasCompleted(TASK_CREATE_XML);
    }

    @Test
    public void testScenario_FemaleCustomer_XmlFormat_ShouldHandleCorrectly() {
        // Given: A female customer with specific data
        Map<String, Object> customerData = createFemaleCustomerData();
        
        // When: Process is started and XML format is selected
        when(dataFormatProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.complete(withVariables("dataFormat", "xml")));

        // Then: Process should complete successfully
        run(dataFormatProcess)
            .startByKey(PROCESS_KEY, customerData)
            .execute();

        verify(dataFormatProcess).hasFinished(END_EVENT_ID);
        verify(dataFormatProcess).hasCompleted(TASK_CREATE_XML);
    }

    @Test
    public void testScenario_MaleCustomer_JsonFormat_ShouldHandleCorrectly() {
        // Given: A male customer with specific data
        Map<String, Object> customerData = createMaleCustomerData();
        
        // When: Process is started and JSON format is selected
        when(dataFormatProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.complete(withVariables("dataFormat", "json")));

        // Then: Process should complete successfully
        run(dataFormatProcess)
            .startByKey(PROCESS_KEY, customerData)
            .execute();

        verify(dataFormatProcess).hasFinished(END_EVENT_ID);
        verify(dataFormatProcess).hasCompleted(TASK_CREATE_JSON);
    }

    @Test
    public void testScenario_InvalidCustomer_ShouldStillProcess() {
        // Given: An invalid customer (isValid = false)
        Map<String, Object> customerData = createInvalidCustomerData();
        
        // When: Process is started and XML format is selected
        when(dataFormatProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.complete(withVariables("dataFormat", "xml")));

        // Then: Process should still complete (business rule: process even invalid customers)
        run(dataFormatProcess)
            .startByKey(PROCESS_KEY, customerData)
            .execute();

        verify(dataFormatProcess).hasFinished(END_EVENT_ID);
        verify(dataFormatProcess).hasCompleted(TASK_CREATE_XML);
        verify(dataFormatProcess).hasCompleted(TASK_LOG_CUSTOMER);
    }

    @Test
    public void testScenario_MinorCustomer_JsonFormat_ShouldHandleAppropriately() {
        // Given: A minor customer (age < 18)
        Map<String, Object> customerData = createMinorCustomerData();
        
        // When: Process is started and JSON format is selected
        when(dataFormatProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.complete(withVariables("dataFormat", "json")));

        // Then: Process should complete successfully
        run(dataFormatProcess)
            .startByKey(PROCESS_KEY, customerData)
            .execute();

        verify(dataFormatProcess).hasFinished(END_EVENT_ID);
        verify(dataFormatProcess).hasCompleted(TASK_CREATE_JSON);
    }

    @Test
    public void testScenario_SeniorCustomer_XmlFormat_ShouldHandleAppropriately() {
        // Given: A senior customer (age > 65)
        Map<String, Object> customerData = createSeniorCustomerData();
        
        // When: Process is started and XML format is selected
        when(dataFormatProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.complete(withVariables("dataFormat", "xml")));

        // Then: Process should complete successfully
        run(dataFormatProcess)
            .startByKey(PROCESS_KEY, customerData)
            .execute();

        verify(dataFormatProcess).hasFinished(END_EVENT_ID);
        verify(dataFormatProcess).hasCompleted(TASK_CREATE_XML);
    }

    @Test
    public void testScenario_MultipleCustomers_DifferentFormats_ShouldHandleParallel() {
        // Given: Multiple customers with different format preferences
        Map<String, Object> customer1 = createCustomerData("John", "Doe", "male", 30L, true);
        Map<String, Object> customer2 = createCustomerData("Jane", "Smith", "female", 25L, true);
        
        // When: Multiple processes are started in parallel
        ProcessScenario xmlProcess = mock(ProcessScenario.class);
        ProcessScenario jsonProcess = mock(ProcessScenario.class);
        
        // Configure XML process scenario (only user task, service tasks use Java delegates)
        when(xmlProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.complete(withVariables("dataFormat", "xml")));
        // Note: Service tasks use Java delegates and don't wait, so no stubbing needed
            
        // Configure JSON process scenario (only user task, service tasks use Java delegates)
        when(jsonProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.complete(withVariables("dataFormat", "json")));
        // Note: Service tasks use Java delegates and don't wait, so no stubbing needed

        // Then: Both processes should complete successfully
        run(xmlProcess)
            .startByKey(PROCESS_KEY, customer1)
            .execute();
            
        run(jsonProcess)
            .startByKey(PROCESS_KEY, customer2)
            .execute();

        // Verify both processes completed correctly
        verify(xmlProcess).hasFinished(END_EVENT_ID);
        verify(xmlProcess).hasCompleted(TASK_CREATE_XML);
        verify(jsonProcess).hasFinished(END_EVENT_ID);
        verify(jsonProcess).hasCompleted(TASK_CREATE_JSON);
    }

    @Test
    public void testScenario_EmptyCustomerData_ShouldHandleGracefully() {
        // Given: Minimal customer data (testing null/empty handling)
        Map<String, Object> customerData = createMinimalCustomerData();
        
        // When: Process is started and XML format is selected
        when(dataFormatProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.complete(withVariables("dataFormat", "xml")));

        // Then: Process should complete without errors
        run(dataFormatProcess)
            .startByKey(PROCESS_KEY, customerData)
            .execute();

        verify(dataFormatProcess).hasFinished(END_EVENT_ID);
        verify(dataFormatProcess).hasCompleted(TASK_CREATE_XML);
        verify(dataFormatProcess).hasCompleted(TASK_LOG_CUSTOMER);
    }

    // Helper methods for creating test data
    private Map<String, Object> createCompleteCustomerData() {
        return createCustomerData("John", "Doe", "male", 30L, true);
    }

    private Map<String, Object> createFemaleCustomerData() {
        return createCustomerData("Jane", "Smith", "female", 28L, true);
    }

    private Map<String, Object> createMaleCustomerData() {
        return createCustomerData("Bob", "Johnson", "male", 35L, true);
    }

    private Map<String, Object> createInvalidCustomerData() {
        return createCustomerData("Invalid", "Customer", "male", 25L, false);
    }

    private Map<String, Object> createMinorCustomerData() {
        return createCustomerData("Minor", "Customer", "female", 16L, true);
    }

    private Map<String, Object> createSeniorCustomerData() {
        return createCustomerData("Senior", "Customer", "male", 70L, true);
    }

    private Map<String, Object> createMinimalCustomerData() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("firstname", "");
        variables.put("lastname", "");
        variables.put("gender", "");
        variables.put("age", 0L);
        variables.put("isValid", false);
        variables.put("validationDate", "");
        return variables;
    }

    private Map<String, Object> createCustomerData(String firstname, String lastname, String gender, Long age, Boolean isValid) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("firstname", firstname);
        variables.put("lastname", lastname);
        variables.put("gender", gender);
        variables.put("age", age);
        variables.put("isValid", isValid);
        variables.put("validationDate", LocalDate.now().toString());
        return variables;
    }

    private Map<String, Object> withVariables(String key, Object value) {
        Map<String, Object> variables = new HashMap<>();
        variables.put(key, value);
        return variables;
    }
} 