package com.markethub.vendor.admin.api;
import com.markethub.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service public class AdminVendorService {
 private final JdbcTemplate jdbc;
 public AdminVendorService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record ApplicationView(UUID vendorId,UUID ownerUserId,String displayName,String status,Instant createdAt,String rejectionReason){}
 public record VendorItem(UUID vendorId,UUID ownerUserId,String ownerEmail,String displayName,String status,Instant createdAt,String storeName,String storeSlug,String supportEmail,String lastActorEmail,String lastReason,Instant lastActionAt){}
 public record VendorPage(List<VendorItem> items,long total,int limit,int offset){}
 public List<ApplicationView> pending(){return jdbc.query("select id,owner_user_id,display_name,status,created_at,rejection_reason from vendors where status='PENDING' order by created_at",(rs,n)->new ApplicationView(rs.getObject("id",UUID.class),rs.getObject("owner_user_id",UUID.class),rs.getString("display_name"),rs.getString("status"),rs.getTimestamp("created_at").toInstant(),rs.getString("rejection_reason")));}
 @Transactional public ApplicationView approve(UUID id,UUID actor){ApplicationView app=requirePending(id);int changed=jdbc.update("update vendors set status='APPROVED',reviewed_at=now(),reviewed_by=?,rejection_reason=null where id=? and status='PENDING'",actor,id);if(changed!=1)throw new IllegalStateException("Vendor application was concurrently reviewed");jdbc.update("update app_users set role='VENDOR',updated_at=now() where id=? and role in ('CUSTOMER','VENDOR')",app.ownerUserId());return findApplication(id);}
 @Transactional public ApplicationView reject(UUID id,UUID actor,String reason){requirePending(id);int changed=jdbc.update("update vendors set status='REJECTED',reviewed_at=now(),reviewed_by=?,rejection_reason=? where id=? and status='PENDING'",actor,reason.trim(),id);if(changed!=1)throw new IllegalStateException("Vendor application was concurrently reviewed");return findApplication(id);}
 public VendorPage vendors(String status,String search,int limit,int offset){
  if(limit<1||limit>100||offset<0)throw new IllegalArgumentException("Invalid pagination");
  if(status!=null&&!status.isBlank()&&!List.of("APPROVED","SUSPENDED").contains(status))throw new IllegalArgumentException("Unsupported vendor status");
  StringBuilder where=new StringBuilder(" WHERE v.status IN ('APPROVED','SUSPENDED') ");List<Object> args=new ArrayList<>();
  if(status!=null&&!status.isBlank()){where.append(" AND v.status=? ");args.add(status);}
  if(search!=null&&!search.isBlank()){where.append(" AND (v.display_name ILIKE ? OR u.email ILIKE ? OR sp.store_name ILIKE ?) ");String term="%"+search.trim()+"%";args.add(term);args.add(term);args.add(term);}
  Long total=jdbc.queryForObject("SELECT count(*) FROM vendors v JOIN app_users u ON u.id=v.owner_user_id LEFT JOIN store_profiles sp ON sp.vendor_id=v.id"+where,Long.class,args.toArray());
  String sql="""
   SELECT v.id,v.owner_user_id,u.email,v.display_name,v.status,v.created_at,sp.store_name,sp.slug,sp.support_email,
    actor.email,event.reason,event.created_at
   FROM vendors v JOIN app_users u ON u.id=v.owner_user_id LEFT JOIN store_profiles sp ON sp.vendor_id=v.id
   LEFT JOIN LATERAL (SELECT actor_user_id,reason,created_at FROM admin_vendor_status_events WHERE vendor_id=v.id ORDER BY created_at DESC,id DESC LIMIT 1) event ON TRUE
   LEFT JOIN app_users actor ON actor.id=event.actor_user_id
   """+where+" ORDER BY v.created_at DESC,v.id DESC LIMIT ? OFFSET ?";
  args.add(limit);args.add(offset);List<VendorItem> items=jdbc.query(sql,(rs,n)->vendorItem(rs),args.toArray());
  return new VendorPage(items,total==null?0:total,limit,offset);
 }
 @Transactional public VendorItem changeStatus(UUID id,UUID actor,String target,String reason){
  if(!List.of("APPROVED","SUSPENDED").contains(target))throw new IllegalArgumentException("Unsupported vendor status");
  return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<VendorItem>) connection->{
   String previous;
   try(var lock=connection.prepareStatement("SELECT status FROM vendors WHERE id=? FOR UPDATE")){lock.setObject(1,id);try(var rs=lock.executeQuery()){if(!rs.next())throw new ResourceNotFoundException("Vendor not found");previous=rs.getString(1);}}
   if(!List.of("APPROVED","SUSPENDED").contains(previous))throw new IllegalStateException("Only approved or suspended vendors can be managed");
   if(!previous.equals(target)){
    try(var update=connection.prepareStatement("UPDATE vendors SET status=?,reviewed_at=now(),reviewed_by=? WHERE id=?")){update.setString(1,target);update.setObject(2,actor);update.setObject(3,id);update.executeUpdate();}
    try(var audit=connection.prepareStatement("INSERT INTO admin_vendor_status_events(id,vendor_id,actor_user_id,previous_status,resulting_status,reason) VALUES (?,?,?,?,?,?)")){audit.setObject(1,UUID.randomUUID());audit.setObject(2,id);audit.setObject(3,actor);audit.setString(4,previous);audit.setString(5,target);audit.setString(6,reason.trim());audit.executeUpdate();}
   }
   return jdbc.queryForObject("""
    SELECT v.id,v.owner_user_id,u.email,v.display_name,v.status,v.created_at,sp.store_name,sp.slug,sp.support_email,actor.email,event.reason,event.created_at
    FROM vendors v JOIN app_users u ON u.id=v.owner_user_id LEFT JOIN store_profiles sp ON sp.vendor_id=v.id
    LEFT JOIN LATERAL (SELECT actor_user_id,reason,created_at FROM admin_vendor_status_events WHERE vendor_id=v.id ORDER BY created_at DESC,id DESC LIMIT 1) event ON TRUE
    LEFT JOIN app_users actor ON actor.id=event.actor_user_id WHERE v.id=?
    """,(rs,n)->vendorItem(rs),id);
  });
 }
 private VendorItem vendorItem(java.sql.ResultSet rs)throws java.sql.SQLException{return new VendorItem(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant(),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getTimestamp(12)==null?null:rs.getTimestamp(12).toInstant());}
 private ApplicationView requirePending(UUID id){ApplicationView a=findApplication(id);if(!"PENDING".equals(a.status()))throw new IllegalStateException("Only pending applications can be reviewed");return a;}
 private ApplicationView findApplication(UUID id){return jdbc.query("select id,owner_user_id,display_name,status,created_at,rejection_reason from vendors where id=?",(rs,n)->new ApplicationView(rs.getObject("id",UUID.class),rs.getObject("owner_user_id",UUID.class),rs.getString("display_name"),rs.getString("status"),rs.getTimestamp("created_at").toInstant(),rs.getString("rejection_reason")),id).stream().findFirst().orElseThrow(()->new ResourceNotFoundException("Vendor application not found"));}
}
