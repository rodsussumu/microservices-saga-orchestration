package br.com.microservices.orchestrated.paymentservice.core.services;

import br.com.microservices.orchestrated.paymentservice.config.exceptions.ValidationException;
import br.com.microservices.orchestrated.paymentservice.core.dtos.Event;
import br.com.microservices.orchestrated.paymentservice.core.dtos.History;
import br.com.microservices.orchestrated.paymentservice.core.dtos.OrderProducts;
import br.com.microservices.orchestrated.paymentservice.core.enums.EPaymentStatus;
import br.com.microservices.orchestrated.paymentservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.paymentservice.core.models.Payment;
import br.com.microservices.orchestrated.paymentservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.paymentservice.core.repositories.PaymentRepository;
import br.com.microservices.orchestrated.paymentservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
@AllArgsConstructor
public class PaymentService {
    private static final String CURRENT_SOURCE = "PAYMENT_SERVICE";
    private static final Double REDUCE_DOUBLE = 0.0;
    private static final Double MIN_AMOUNT_VALUE = 0.1;


    private final JsonUtil jsonUtil;
    private final KafkaProducer producer;
    private final PaymentRepository paymentRepository;

    public void realizePayment(Event event) {
        try {
            checkCurrentValidation(event);
            createPendingPayment(event);
            Payment payment = findByOrderIdAndTransactionId(event);
            validateAmount(payment.getTotalAmount());
            changePaymentSuccess(payment);
            handleSuccess(event);
        } catch(Exception ex) {
            log.error("Error trying to make payment: " + ex);
            handleFailCurrentNotExecuted(event, ex.getMessage());
        }
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void checkCurrentValidation(Event event) {
        if (paymentRepository.existsByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())) {
            throw new ValidationException("There are another transactionID for this validation");
        }
    }

    private void createPendingPayment(Event event) {
        int totalItems = calculateTotalItems(event);

        double totalAmount = calculateAmount(event);

        Payment payment = Payment
                .builder()
                .orderId(event.getPayload().getId())
                .transactionId(event.getTransactionId())
                .totalAmount(totalAmount)
                .totalItems(totalItems)
                .build();
        paymentRepository.save(payment);
    }

    private double calculateAmount(Event event) {
        return event
                .getPayload()
                .getProducts()
                .stream()
                .map(product ->product.getQuantity() * product.getProduct().getUnitValue())
                .reduce(REDUCE_DOUBLE, Double::sum);
    }

    private int calculateTotalItems(Event event) {
        return event
                .getPayload()
                .getProducts()
                .stream()
                .map(OrderProducts::getQuantity)
                .reduce(REDUCE_DOUBLE.intValue(), Integer::sum);
    }

    private void validateAmount(double amount) {
        if(amount < MIN_AMOUNT_VALUE) {
            throw new ValidationException("The minimum amount available is".concat(MIN_AMOUNT_VALUE.toString()));
        }
    }

    private void changePaymentSuccess(Payment payment) {
        payment.setStatus(EPaymentStatus.SUCCESS);
        paymentRepository.save(payment);
    }

    private void handleSuccess(Event event) {
        event.setStatus(ESagaStatus.SUCCESS);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Payment realized successfully!");
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

    private void handleFailCurrentNotExecuted(Event event, String message) {
        event.setStatus(ESagaStatus.ROLLBACK_PENDING);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Failed to realize payment: ".concat(message));
    }

    public void realizeRefund(Event event) {
        event.setStatus(ESagaStatus.FAIL);
        event.setSource(CURRENT_SOURCE);
        try {
            changePaymentStatusToRefund(event);
            addHistory(event, "Rollback executed on payment!");
        } catch(Exception ex) {
            addHistory(event, "Rollback not executed for payment : ".concat(ex.getMessage()));
        }
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void changePaymentStatusToRefund(Event event) {
        Payment payment = findByOrderIdAndTransactionId(event);
        payment.setStatus(EPaymentStatus.REFUND);
        setEventAmountItems(event, payment);
        paymentRepository.save(payment);
    }

    private Payment findByOrderIdAndTransactionId(Event event) {
        return paymentRepository
                .findByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())
                .orElseThrow(() -> new ValidationException("Payment not found by Order and Transaction"));
    }

    private void setEventAmountItems(Event event, Payment payment) {
        event.getPayload().setTotalAmount(payment.getTotalAmount());
        event.getPayload().setTotalItems(payment.getTotalItems());
    }
}
