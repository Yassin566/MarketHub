package com.markethub.vendor.application;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.security.access.AccessDeniedException; import org.springframework.stereotype.Component; import java.util.UUID;
@Component public class ApprovedVendorAccess {
 private final JdbcTemplate jdbc; public ApprovedVendorAccess(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public UUID requireApprovedVendor(UUID userId){return jdbc.query("select id from vendors where owner_user_id=? and status='APPROVED'",(rs,n)->rs.getObject(1,UUID.class),userId).stream().findFirst().orElseThrow(()->new AccessDeniedException("An approved vendor account is required"));}
}