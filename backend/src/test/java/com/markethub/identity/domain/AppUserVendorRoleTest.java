package com.markethub.identity.domain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AppUserVendorRoleTest {
 @Test void approvedCustomerCanBePromotedToVendor(){AppUser user=AppUser.register("vendor@example.com","hashed-password");user.activateVendorRole();assertEquals(UserRole.VENDOR,user.getRole());}
 @Test void lockedAccountCannotBePromoted(){AppUser user=AppUser.register("locked@example.com","hashed-password");assertThrows(IllegalStateException.class,()->{try{var f=AppUser.class.getDeclaredField("status");f.setAccessible(true);f.set(user,UserStatus.LOCKED);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}user.activateVendorRole();});}
}