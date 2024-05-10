package br.com.microservices.orchestrated.orchestratorservice.config.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExceptionDetails {
    private int status;
    private String message;
}
