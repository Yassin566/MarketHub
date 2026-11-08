package com.markethub.vendor.order.api;
import com.markethub.identity.domain.UserRole;
import com.markethub.identity.security.JwtService;
import com.markethub.identity.security.MarketHubPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class VendorShipmentHttpIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    private UUID vendorAOrder,parentOrder,vendorBOrder,customerA;
    private String vendorAToken,customerToken;

    @BeforeEach void setUp() {
        UUID vendorAUser=createUser("ship-vendor-a-"+UUID.randomUUID()+"@example.test","VENDOR");
        UUID vendorA=createVendor(vendorAUser,"Vendor A");
        UUID vendorB=createVendor(createUser("ship-vendor-b-"+UUID.randomUUID()+"@example.test","VENDOR"),"Vendor B");
        customerA=createUser("ship-customer-"+UUID.randomUUID()+"@example.test","CUSTOMER");
        parentOrder=createParentOrder(customerA);
        vendorAOrder=createVendorOrder(parentOrder,vendorA,"PROCESSING");
        UUID parentB=createParentOrder(createUser("ship-customer-b-"+UUID.randomUUID()+"@example.test","CUSTOMER"));
        vendorBOrder=createVendorOrder(parentB,vendorB,"PROCESSING");
        vendorAToken=token(vendorAUser,UserRole.VENDOR); customerToken=token(customerA,UserRole.CUSTOMER);
    }

    @Test void dispatchAndCustomerTrackingAreAvailable() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/orders/{id}/shipment",vendorAOrder)
            .header("Authorization","Bearer "+vendorAToken).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"carrier":"Example Express","trackingNumber":"ZX123456",
                 "trackingUrl":"https://carrier.example/track/ZX123456","estimatedDeliveryDate":"2026-11-12"}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("FULFILLED"))
            .andExpect(jsonPath("$.data.shipment.trackingNumber").value("ZX123456"));
        mockMvc.perform(get("/api/v1/customer/orders/{id}/fulfilments",parentOrder)
            .header("Authorization","Bearer "+customerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("FULFILLED"))
            .andExpect(jsonPath("$.data[0].shipment.trackingNumber").value("ZX123456"));
    }

    @Test void vendorCannotDispatchOtherVendorOrder() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/orders/{id}/shipment",vendorBOrder)
            .header("Authorization","Bearer "+vendorAToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"carrier\":\"Carrier\",\"trackingNumber\":\"OTHER\"}"))
            .andExpect(status().isNotFound());
    }

    @Test void dispatchIsOneTimeAndFulfilledCannotBeSetDirectly() throws Exception {
        mockMvc.perform(patch("/api/v1/vendor/orders/{id}/status",vendorAOrder)
            .header("Authorization","Bearer "+vendorAToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"FULFILLED\",\"note\":\"Skip shipment\"}"))
            .andExpect(status().isConflict());
        String body="{\"carrier\":\"Carrier\",\"trackingNumber\":\"ONCE123\"}";
        mockMvc.perform(post("/api/v1/vendor/orders/{id}/shipment",vendorAOrder)
            .header("Authorization","Bearer "+vendorAToken).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/vendor/orders/{id}/shipment",vendorAOrder)
            .header("Authorization","Bearer "+vendorAToken).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());
    }

    @Test void customerCannotReadOtherCustomersFulfilment() throws Exception {
        UUID other=createUser("ship-other-"+UUID.randomUUID()+"@example.test","CUSTOMER");
        mockMvc.perform(get("/api/v1/customer/orders/{id}/fulfilments",parentOrder)
            .header("Authorization","Bearer "+token(other,UserRole.CUSTOMER))).andExpect(status().isNotFound());
    }

    @Test void trackingUrlMustBeHttpOrHttps() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/orders/{id}/shipment",vendorAOrder)
            .header("Authorization","Bearer "+vendorAToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"carrier\":\"Carrier\",\"trackingNumber\":\"BAD\",\"trackingUrl\":\"javascript:alert(1)\"}"))
            .andExpect(status().isBadRequest());
    }

    private UUID createUser(String email,String role) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into app_users(id,email,password_hash,role,status) values (?,?,?,?,'ACTIVE')",id,email,"integration-test-hash",role);
        return id;
    }
    private UUID createVendor(UUID owner,String name) {
        UUID id=UUID.randomUUID(); jdbc.update("insert into vendors(id,owner_user_id,display_name,status) values (?,?,?,'APPROVED')",id,owner,name); return id;
    }
    private UUID createParentOrder(UUID customer) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into customer_orders(id,customer_user_id,idempotency_key,status,currency,subtotal,total) values (?,?,?,'PAID','USD',10,10)",id,customer,UUID.randomUUID());
        return id;
    }
    private UUID createVendorOrder(UUID parent,UUID vendor,String status) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into vendor_orders(id,order_id,vendor_id,status,currency,subtotal) values (?,?,?,?,'USD',10)",id,parent,vendor,status);
        return id;
    }
    private String token(UUID userId,UserRole role) {
        String email=jdbc.queryForObject("select email from app_users where id=?",String.class,userId);
        return jwtService.issueAccessToken(new MarketHubPrincipal(userId,email,"not-used",role,true));
    }
}
