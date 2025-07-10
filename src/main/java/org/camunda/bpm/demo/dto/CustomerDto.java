package org.camunda.bpm.demo.dto;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomerDto implements Serializable {

    private String firstname;
    private String lastname;
    private String gender; // enum: "female" or "male"
    private Long age;
    private Boolean isValid;
    private LocalDate validationDate;
    private String dataFormat; // enum: "xml" or "json" (from user task)
} 