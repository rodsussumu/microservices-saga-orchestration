package br.com.microservices.orchestrated.orchestratorservice.core.saga;

import br.com.microservices.orchestrated.orchestratorservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.orchestratorservice.core.dtos.Event;
import br.com.microservices.orchestrated.orchestratorservice.core.enums.EEventSource;
import br.com.microservices.orchestrated.orchestratorservice.core.enums.ETopics;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;

import static org.springframework.util.ObjectUtils.isEmpty;

@Slf4j
@Component
@AllArgsConstructor
public class SagaExecutionController {

    public ETopics getNextTopic(Event event) {
        if(isEmpty(event.getSource()) || isEmpty(event.getStatus())) {
            throw new ValidationException("Source and status must be informed");
        }
        ETopics topic = findTopicsBySourceAndStatus(event);
        logCurrentSaga(event, topic);
        return topic;
    }

    private ETopics findTopicsBySourceAndStatus(Event event) {
        return (ETopics) (Arrays.stream(SagaHandler.SAGA_HANDLER)
                .filter(row -> isEventSourceAndStatusValid(event, row))
                .map(i -> i[SagaHandler.TOPIC_INDEX])
                .findFirst()
                .orElseThrow(() -> new ValidationException("Topic not found")));
    }

    private boolean isEventSourceAndStatusValid(Event event, Object[] row) {
        Object source = row[SagaHandler.EVENT_SOURCE_INDEX];
        Object status = row[SagaHandler.SAGA_STATUS_INDEX];
        return event.getSource().equals(source) && event.getStatus().equals(status);

    }

    private void logCurrentSaga(Event event, ETopics topic) {
        String sagaId = createSagaId(event);
        EEventSource source = event.getSource();
        switch(event.getStatus()) {
            case SUCCESS : log.info("### CURRENT SAGA: {} | SUCCESS | NEXT TOPIC {} | {}", source, topic, sagaId);
            case ROLLBACK_PENDING : log.info("### CURRENT SAGA: {} | SENDING TO ROLLBACK CURRENT SERVICE | NEXT TOPIC {} | {}", source, topic, sagaId);
            case FAIL : log.info("### CURRENT SAGA: {} | SENDING TO ROLLBACK PREVIOUS SERVICE | NEXT TOPIC {} | {}", source, topic, sagaId);



        }
    }

    private String createSagaId(Event event) {
        return String.format("ORDER ID: %s | TRANSACTION ID %s | EVENT ID %s", event.getPayload().getId(), event.getTransactionId(), event.getId());
    }

}
