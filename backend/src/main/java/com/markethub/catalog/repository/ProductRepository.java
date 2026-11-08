package com.markethub.catalog.repository;
import com.markethub.catalog.domain.Product; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.UUID;
public interface ProductRepository extends JpaRepository<Product,UUID>{ Page<Product> findByVendorIdAndStatus(UUID vendorId,String status,Pageable pageable); }