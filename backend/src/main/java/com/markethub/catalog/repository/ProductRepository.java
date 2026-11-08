package com.markethub.catalog.repository;
import com.markethub.catalog.domain.Product; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface ProductRepository extends JpaRepository<Product,UUID>{
 Page<Product> findByVendorIdAndStatus(UUID vendorId,String status,Pageable pageable);
 Page<Product> findByStatus(String status,Pageable pageable);
 Page<Product> findByStatusAndNameContainingIgnoreCase(String status,String name,Pageable pageable);
 Optional<Product> findBySlugAndStatus(String slug,String status);
}