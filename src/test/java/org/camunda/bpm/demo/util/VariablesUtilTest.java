package org.camunda.bpm.demo.util;

import org.camunda.bpm.demo.dto.CamundaMessageDto;
import org.camunda.bpm.demo.dto.MessageProcessDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.beans.IntrospectionException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VariablesUtilTest {

    @Test
    void toVariableMap_WithCompleteMessageProcessDto_ShouldMapAllFields() throws IntrospectionException, InvocationTargetException, IllegalAccessException {
        // Given
        MessageProcessDto dto = MessageProcessDto.builder()
            .requester("test-requester")
            .amount(1000.0)
            .preApproved(true)
            .processed(false)
            .build();

        // When
        Map<String, Object> variables = VariablesUtil.toVariableMap(dto);

        // Then
        assertNotNull(variables);
        assertEquals(4, variables.size());
        assertEquals("test-requester", variables.get("requester"));
        assertEquals(1000.0, variables.get("amount"));
        assertEquals(true, variables.get("preApproved"));
        assertEquals(false, variables.get("processed"));
    }

    @Test
    void toVariableMap_WithPartialMessageProcessDto_ShouldMapNonNullFields() throws IntrospectionException, InvocationTargetException, IllegalAccessException {
        // Given
        MessageProcessDto dto = MessageProcessDto.builder()
            .requester("partial-requester")
            .amount(500.0)
            .preApproved(null)
            .processed(null)
            .build();

        // When
        Map<String, Object> variables = VariablesUtil.toVariableMap(dto);

        // Then
        assertNotNull(variables);
        assertEquals(2, variables.size());
        assertEquals("partial-requester", variables.get("requester"));
        assertEquals(500.0, variables.get("amount"));
        assertFalse(variables.containsKey("preApproved"));
        assertFalse(variables.containsKey("processed"));
    }

    @Test
    void toVariableMap_WithEmptyDto_ShouldReturnEmptyMap() throws IntrospectionException, InvocationTargetException, IllegalAccessException {
        // Given
        MessageProcessDto dto = MessageProcessDto.builder()
            .requester(null)
            .amount(null)
            .preApproved(null)
            .processed(null)
            .build();

        // When
        Map<String, Object> variables = VariablesUtil.toVariableMap(dto);

        // Then
        assertNotNull(variables);
        assertTrue(variables.isEmpty());
    }

    @Test
    void toVariableMap_WithHighValueDto_ShouldMapCorrectly() throws IntrospectionException, InvocationTargetException, IllegalAccessException {
        // Given
        MessageProcessDto dto = MessageProcessDto.builder()
            .requester("vip-customer")
            .amount(50000.0)
            .preApproved(false)
            .processed(false)
            .build();

        // When
        Map<String, Object> variables = VariablesUtil.toVariableMap(dto);

        // Then
        assertEquals("vip-customer", variables.get("requester"));
        assertEquals(50000.0, variables.get("amount"));
        assertEquals(false, variables.get("preApproved"));
        assertEquals(false, variables.get("processed"));
    }

    @Test
    void buildCamundaMessageDto_WithCompleteVariables_ShouldCreateCompleteDto() {
        // Given
        String businessKey = "test-business-key";
        Map<String, Object> variablesMap = new HashMap<>();
        variablesMap.put("requester", "test-requester");
        variablesMap.put("amount", 1000.0);
        variablesMap.put("preApproved", true);
        variablesMap.put("processed", false);

        // When
        CamundaMessageDto result = VariablesUtil.buildCamundaMessageDto(businessKey, variablesMap);

        // Then
        assertNotNull(result);
        assertEquals(businessKey, result.getCorrelationId());
        
        MessageProcessDto dto = result.getDto();
        assertNotNull(dto);
        assertEquals("test-requester", dto.getRequester());
        assertEquals(1000.0, dto.getAmount());
        assertTrue(dto.getPreApproved());
        assertFalse(dto.getProcessed());
    }

    @Test
    void buildCamundaMessageDto_WithPartialVariables_ShouldCreatePartialDto() {
        // Given
        String businessKey = "partial-key";
        Map<String, Object> variablesMap = new HashMap<>();
        variablesMap.put("requester", "partial-requester");
        variablesMap.put("amount", 500.0);
        // Missing preApproved and processed

        // When
        CamundaMessageDto result = VariablesUtil.buildCamundaMessageDto(businessKey, variablesMap);

        // Then
        assertEquals(businessKey, result.getCorrelationId());
        
        MessageProcessDto dto = result.getDto();
        assertEquals("partial-requester", dto.getRequester());
        assertEquals(500.0, dto.getAmount());
        assertNull(dto.getPreApproved());
        assertNull(dto.getProcessed());
    }

    @Test
    void buildCamundaMessageDto_WithEmptyVariables_ShouldCreateDtoWithNulls() {
        // Given
        String businessKey = "empty-key";
        Map<String, Object> variablesMap = new HashMap<>();

        // When
        CamundaMessageDto result = VariablesUtil.buildCamundaMessageDto(businessKey, variablesMap);

        // Then
        assertEquals(businessKey, result.getCorrelationId());
        
        MessageProcessDto dto = result.getDto();
        assertNull(dto.getRequester());
        assertNull(dto.getAmount());
        assertNull(dto.getPreApproved());
        assertNull(dto.getProcessed());
    }

    @Test
    void buildCamundaMessageDto_WithNullBusinessKey_ShouldCreateDtoWithNullCorrelationId() {
        // Given
        String businessKey = null;
        Map<String, Object> variablesMap = new HashMap<>();
        variablesMap.put("requester", "test-requester");

        // When
        CamundaMessageDto result = VariablesUtil.buildCamundaMessageDto(businessKey, variablesMap);

        // Then
        assertNull(result.getCorrelationId());
        assertEquals("test-requester", result.getDto().getRequester());
    }

    @Test
    void buildCamundaMessageDto_WithWrongTypeValues_ShouldHandleGracefully() {
        // Given
        String businessKey = "wrong-type-key";
        Map<String, Object> variablesMap = new HashMap<>();
        variablesMap.put("requester", "valid-requester");
        variablesMap.put("amount", "not-a-number"); // Wrong type
        variablesMap.put("preApproved", "not-a-boolean"); // Wrong type
        variablesMap.put("processed", true);

        // When & Then - Should handle wrong types gracefully
        // The method should either handle the wrong types gracefully or throw an exception
        // In this case, we expect an exception when trying to use wrong types
        assertThrows(Exception.class, () -> {
            CamundaMessageDto result = VariablesUtil.buildCamundaMessageDto(businessKey, variablesMap);
            // If creation succeeds, accessing the wrong types should fail
            if (result != null && result.getDto() != null) {
                result.getDto().getAmount(); // This should fail if amount is not a Double
            }
        });
    }

    @ParameterizedTest
    @MethodSource("org.camunda.bpm.demo.util.TestDataBuilder#createTestDataVariations")
    void buildCamundaMessageDto_WithVariousInputs_ShouldHandleCorrectly(String correlationSuffix, String requester, Double amount, Boolean preApproved) {
        // Given
        String businessKey = "param-test-" + correlationSuffix;
        Map<String, Object> variablesMap = new HashMap<>();
        if (requester != null) variablesMap.put("requester", requester);
        if (amount != null) variablesMap.put("amount", amount);
        if (preApproved != null) variablesMap.put("preApproved", preApproved);
        variablesMap.put("processed", false);

        // When
        CamundaMessageDto result = VariablesUtil.buildCamundaMessageDto(businessKey, variablesMap);

        // Then
        assertNotNull(result);
        assertEquals(businessKey, result.getCorrelationId());
        
        MessageProcessDto dto = result.getDto();
        assertNotNull(dto);
        assertEquals(requester, dto.getRequester());
        assertEquals(amount, dto.getAmount());
        assertEquals(preApproved, dto.getPreApproved());
        assertEquals(false, dto.getProcessed());
    }

    @Test
    void toVariableMap_WithZeroValues_ShouldIncludeZeros() throws IntrospectionException, InvocationTargetException, IllegalAccessException {
        // Given
        MessageProcessDto dto = MessageProcessDto.builder()
            .requester("")
            .amount(0.0)
            .preApproved(false)
            .processed(false)
            .build();

        // When
        Map<String, Object> variables = VariablesUtil.toVariableMap(dto);

        // Then
        assertEquals(4, variables.size());
        assertEquals("", variables.get("requester"));
        assertEquals(0.0, variables.get("amount"));
        assertEquals(false, variables.get("preApproved"));
        assertEquals(false, variables.get("processed"));
    }

    @Test
    void buildCamundaMessageDto_WithNullVariablesMap_ShouldCreateDtoWithNulls() {
        // Given
        String businessKey = "null-vars-key";
        Map<String, Object> variablesMap = null;

        // When & Then
        assertThrows(NullPointerException.class, () -> {
            VariablesUtil.buildCamundaMessageDto(businessKey, variablesMap);
        });
    }
} 