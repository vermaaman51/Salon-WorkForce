package com.salon.service;

import com.salon.repository.ReceptionRepository;
import com.salon.dto.ReceptionRequests.*;
import com.salon.constant.*;
import com.salon.utility.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ReceptionService {
    private static final Set<String> SERVICES=Set.of("Hair","Nail","Facial","Massage","Makeup","Waxing","Skincare");
    private final ReceptionRepository repository;private final com.salon.repository.OrderPages pages;
    public ReceptionService(ReceptionRepository repository,com.salon.repository.OrderPages pages) {
        this.repository=repository;this.pages=pages;
    }
    private UUID scope(Authentication auth,UUID store,String role) {
        var rows=repository.findWorkflowTenants(CurrentUser.id(auth), store,role);
        if(rows.size()!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Assigned store not found.");
        return rows.get(0);
    }
    private Map<String,Object> order(UUID id,UUID store,UUID tenant,String role) {
        var rows=repository.lockOrder(id, store, tenant);
        if(rows.isEmpty()||(role.equals(Roles.STORE_MANAGER)&&!rows.get(0).get("order_type").equals(OrderType.APPOINTMENT.name())))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found.");
        return rows.get(0);
    }
    private void conflict(String message) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,message);
    }
    private Object session(Authentication auth,String role) {
        UUID user=CurrentUser.id(auth);
        var result=new LinkedHashMap<String,Object>(repository.findUser(user));
        result.put("stores",repository.findAssignedStores(user,role));
        return result;
    }
    private Object experts(Authentication auth,UUID storeId,String role) {
        UUID tenant=scope(auth,storeId,role);
        return repository.findExperts(storeId, tenant);
    }
    private Object orders(Authentication auth,UUID storeId,String role,com.salon.repository.OrderPages.Query query) {
        UUID tenant=scope(auth,storeId,role);
        return pages.list(tenant,storeId,query,role.equals(Roles.STORE_MANAGER),null,null,false);
    }
    private Object create(Authentication auth,UUID storeId,NewOrder input,String role) {
        UUID tenant=scope(auth,storeId,role);
        if(role.equals(Roles.STORE_MANAGER)&&input.orderType()!=OrderType.APPOINTMENT)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Managers can create appointments in this workspace.");
        String details=input.details()==null?"":input.details().trim();
        if(input.orderType()==OrderType.QUERY&&details.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Query details are required.");
        BigDecimal amount=BigDecimal.ZERO;
        String service=null;
        if(input.orderType()==OrderType.APPOINTMENT) {
            if(input.amount()==null||input.appointmentAt()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Appointments require an amount and appointment date/time.");
            amount=input.amount();
            if(input.service()==null||input.service().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select at least one service.");
            var selected=new LinkedHashSet<String>();
            for(String value:input.service().split(",",-1)) {
                String name=value.trim();
                if(!SERVICES.contains(name))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select services from the available list.");
                selected.add(name);
            }
            service=String.join(", ",selected);
        }
        else if(input.amount()!=null&&input.amount().compareTo(BigDecimal.ZERO)!=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Queries do not have an appointment amount.");
        UUID id=UUID.randomUUID();
        repository.insertOrder(id, tenant, storeId, input.orderType().name(), input.customerName().trim(), input.customerMobile(), details, service, amount, input.orderType()==OrderType.APPOINTMENT?input.appointmentAt():null);
        return Map.of("id",id);
    }
    private void assign(Authentication auth,UUID id,UUID storeId,Assignment input,String role) {
        UUID tenant=scope(auth,storeId,role);
        var order=order(id,storeId,tenant,role);
        if(!order.get("order_type").equals(OrderType.APPOINTMENT.name())||order.get("order_status").equals(OrderStatus.COMPLETED.name()))conflict("Only open appointments can be assigned.");
        var experts=repository.lockExpert(input.assignee(), storeId, tenant);
        if(experts.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Expert not found in this store.");
        repository.assignExpert(input.assignee(), id);
    }
    private void checkout(Authentication auth,UUID id,UUID storeId,Checkout input,String role) {
        UUID tenant=scope(auth,storeId,role);
        var order=order(id,storeId,tenant,role);
        if(order.get("order_status").equals(OrderStatus.COMPLETED.name()))conflict("This order is already completed. Use Mark paid for an outstanding payment.");
        boolean appointment=order.get("order_type").equals(OrderType.APPOINTMENT.name());
        if(appointment&&order.get("assignee")==null)conflict("Assign a expert before completing this appointment.");
        if(!appointment&&input.paymentStatus()!=PaymentStatus.PENDING)conflict("Queries have no payment to collect.");
        if(appointment&&input.paymentStatus()==PaymentStatus.COMPLETED&&input.paymentMode()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select a payment mode.");
        repository.completeOrder(input.paymentStatus().name(),appointment&&input.paymentMode()!=null?input.paymentMode().name():null, id);
    }
    private void paid(Authentication auth,UUID id,UUID storeId,Payment input,String role) {
        UUID tenant=scope(auth,storeId,role);
        var order=order(id,storeId,tenant,role);
        if(!order.get("order_type").equals(OrderType.APPOINTMENT.name())||!order.get("order_status").equals(OrderStatus.COMPLETED.name()))conflict("Only completed appointments can be marked paid.");
        if(order.get("payment_status").equals(PaymentStatus.COMPLETED.name()))conflict("Payment is already completed.");
        repository.completePayment(input.paymentMode().name(),id);
    }
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public Object session(Authentication auth) { return session(auth,Roles.RECEPTIONIST); }
    @PreAuthorize("hasRole('STORE_MANAGER')")
    public Object managerSession(Authentication auth) { return session(auth,Roles.STORE_MANAGER); }
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public Object experts(Authentication auth,UUID storeId) { return experts(auth,storeId,Roles.RECEPTIONIST); }
    @PreAuthorize("hasRole('STORE_MANAGER')")
    public Object managerExperts(Authentication auth,UUID storeId) { return experts(auth,storeId,Roles.STORE_MANAGER); }
    @PreAuthorize("hasRole('RECEPTIONIST')")
    public Object orders(Authentication auth,UUID storeId) { return orders(auth,storeId,Roles.RECEPTIONIST,com.salon.repository.OrderPages.Query.first()); }
    @PreAuthorize("hasRole('STORE_MANAGER')")
    public Object managerOrders(Authentication auth,UUID storeId) { return orders(auth,storeId,Roles.STORE_MANAGER,com.salon.repository.OrderPages.Query.first()); }
    @PreAuthorize("hasRole('RECEPTIONIST')") public Object orders(Authentication auth,UUID storeId,com.salon.repository.OrderPages.Query query){return orders(auth,storeId,Roles.RECEPTIONIST,query);}
    @PreAuthorize("hasRole('STORE_MANAGER')") public Object managerOrders(Authentication auth,UUID storeId,com.salon.repository.OrderPages.Query query){return orders(auth,storeId,Roles.STORE_MANAGER,query);}
    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional
    public Object create(Authentication auth,UUID storeId,NewOrder input) { return create(auth,storeId,input,Roles.RECEPTIONIST); }
    @PreAuthorize("hasRole('STORE_MANAGER')")
    @Transactional
    public Object managerCreate(Authentication auth,UUID storeId,NewOrder input) { return create(auth,storeId,input,Roles.STORE_MANAGER); }
    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional
    public void assign(Authentication auth,UUID id,UUID storeId,Assignment input) { assign(auth,id,storeId,input,Roles.RECEPTIONIST); }
    @PreAuthorize("hasRole('STORE_MANAGER')")
    @Transactional
    public void managerAssign(Authentication auth,UUID id,UUID storeId,Assignment input) { assign(auth,id,storeId,input,Roles.STORE_MANAGER); }
    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional
    public void checkout(Authentication auth,UUID id,UUID storeId,Checkout input) { checkout(auth,id,storeId,input,Roles.RECEPTIONIST); }
    @PreAuthorize("hasRole('STORE_MANAGER')")
    @Transactional
    public void managerCheckout(Authentication auth,UUID id,UUID storeId,Checkout input) { checkout(auth,id,storeId,input,Roles.STORE_MANAGER); }
    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional
    public void paid(Authentication auth,UUID id,UUID storeId,Payment input) { paid(auth,id,storeId,input,Roles.RECEPTIONIST); }
    @PreAuthorize("hasRole('STORE_MANAGER')")
    @Transactional
    public void managerPaid(Authentication auth,UUID id,UUID storeId,Payment input) { paid(auth,id,storeId,input,Roles.STORE_MANAGER); }
}
