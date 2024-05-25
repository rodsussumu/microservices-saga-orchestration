package br.com.microservices.orchestrated.productvalidationservice.core.repositories;

import br.com.microservices.orchestrated.productvalidationservice.core.models.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Integer> {
    Boolean existsByCode(String code);
}
