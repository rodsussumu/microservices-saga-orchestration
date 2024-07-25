package br.com.microservices.orchestrated.orchestratorservice.core.services;

import br.com.microservices.orchestrated.orchestratorservice.core.dtos.Event;
import br.com.microservices.orchestrated.orchestratorservice.core.dtos.History;
import br.com.microservices.orchestrated.orchestratorservice.core.enums.EEventSource;
import br.com.microservices.orchestrated.orchestratorservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.orchestratorservice.core.enums.ETopics;
import br.com.microservices.orchestrated.orchestratorservice.core.producer.SagaOrchestratorProducer;
import br.com.microservices.orchestrated.orchestratorservice.core.saga.SagaExecutionController;
import br.com.microservices.orchestrated.orchestratorservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@AllArgsConstructor
public class OrchestratorService {

    private final JsonUtil jsonUtil;
    private final SagaOrchestratorProducer sagaOrchestratorProducer;
    private final SagaExecutionController sagaExecutionController;
    public void startSaga(Event event) {
        event.setSource(EEventSource.ORCHESTRATOR);
        event.setStatus(ESagaStatus.SUCCESS);
        ETopics topic = getTopic(event);
        log.info("SAGA STARTED");
        addHistory(event, "Saga started");
        sagaOrchestratorProducer.sendEvent(jsonUtil.toJson(event), topic.getTopic());
    }

    public void finishSagaSuccess(Event event) {
        event.setSource(EEventSource.ORCHESTRATOR);
        event.setStatus(ESagaStatus.SUCCESS);
        log.info("SAGA FINISHED SUCCESSFULLY FOR EVENT {}", event.getId());
        addHistory(event, "Saga finished successfully!");
        notifyEndingSaga(event);
    }

    public void finishSagaFail(Event event) {
        event.setSource(EEventSource.ORCHESTRATOR);
        event.setStatus(ESagaStatus.FAIL);
        log.info("SAGA FINISHED WITH ERRORS FOR EVENT {}", event.getId());
        addHistory(event, "Saga finished with errors!");
        notifyEndingSaga(event);
    }

    public void continueSaga(Event event) {
        ETopics topic = getTopic(event);
        log.info("SAGA CONTINUE FOR EVENT {}", event.getId());
        sagaOrchestratorProducer.sendEvent(jsonUtil.toJson(event), topic.getTopic());
    }

    private ETopics getTopic(Event event) {
        return sagaExecutionController.getNextTopic(event);
    }

    private void addHistory(Event event, String message) {
        History history = History
                .builder()
                .source(event.getSource())
                .status(event.getStatus())
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
        event.addToHistory(history);
    }

    private void notifyEndingSaga(Event event) {
        sagaOrchestratorProducer.sendEvent(jsonUtil.toJson(event), ETopics.NOTIFY_ENDING.getTopic());
    }
}
