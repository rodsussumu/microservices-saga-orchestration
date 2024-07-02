package br.com.microservices.orchestrated.inventoryservice.core.repositories;

import br.com.microservices.orchestrated.inventoryservice.core.models.OrderInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderInventoryRepository extends JpaRepository<OrderInventory, Integer> {
    Boolean existsByOrderIdAndTransactionId(String orderId, String transactionId);
    List<OrderInventory> findByOrderIdAndTransactionId(String orderId, String transactionId);
}
