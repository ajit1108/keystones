package com.keystone.fsm.config;

import com.keystone.fsm.domain.SlaPolicy;
import com.keystone.fsm.domain.enums.Priority;
import com.keystone.fsm.domain.enums.Role;
import com.keystone.fsm.domain.enums.WorkOrderStatus;
import com.keystone.fsm.domain.model.*;
import com.keystone.fsm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Idempotent demo seed: four role logins (plus a second customer), customers,
 * sites, parts and a handful of work orders across every lifecycle state.
 * Existing records are never overwritten.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "demo-enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final SiteRepository siteRepository;
    private final PartRepository partRepository;
    private final WorkOrderRepository workOrderRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        log.info("Running DataSeeder...");

        Customer acme = seedCustomer("Acme Corp", "contact@acmecorp.com", "+1-555-0101",
                "123 Industrial Ave, Springfield");
        Customer techVenture = seedCustomer("TechVenture Ltd", "ops@techventure.com", "+1-555-0202",
                "789 Innovation Park, Austin, TX");

        Site hq = seedSite(acme, "Main HQ", "123 Industrial Ave", "Springfield", "IL", "62701");
        Site warehouse = seedSite(acme, "Warehouse A", "456 Warehouse Blvd", "Springfield", "IL", "62702");
        Site austin = seedSite(techVenture, "Austin Campus", "789 Innovation Park", "Austin", "TX", "78701");

        seedUser("dispatcher@keystone.com", "Dispatch1!", "Bob", "Dispatcher", Role.DISPATCHER, null);
        User charlie = seedUser("tech1@keystone.com", "Technic1!", "Charlie", "Tech", Role.TECHNICIAN, null);
        User diana = seedUser("tech2@keystone.com", "Technic2!", "Diana", "Tech", Role.TECHNICIAN, null);
        User manager = seedUser("manager@keystone.com", "Manager1!", "Alice", "Manager", Role.MANAGER, null);
        seedUser("customer@acmecorp.com", "Customer1!", "Eve", "Acme", Role.CUSTOMER, acme.getId());
        seedUser("customer@techventure.com", "Customer2!", "Frank", "Venture", Role.CUSTOMER, techVenture.getId());

        seedPart("FILT-001", "Air Filter", "HVAC air filter 16x25x1", "EACH", 50, 10, new BigDecimal("12.99"));
        seedPart("PUMP-002", "Water Pump", "Centrifugal water pump 1HP", "EACH", 15, 3, new BigDecimal("189.00"));
        seedPart("BELT-003", "Drive Belt V-type", "V-belt A54", "EACH", 100, 20, new BigDecimal("8.50"));
        seedPart("OIL-004", "Lubricating Oil 5L", "ISO VG 46 hydraulic oil", "LITRE", 200, 40, new BigDecimal("22.00"));
        seedPart("CTRL-005", "Control Board", "PLC control board rev.3", "EACH", 8, 2, new BigDecimal("349.00"));

        seedDemoWorkOrders(acme, techVenture, hq, warehouse, austin, manager, charlie, diana);
        log.info("DataSeeder complete.");
    }

    private Customer seedCustomer(String name, String email, String phone, String address) {
        return customerRepository.findByName(name).orElseGet(() -> {
            Customer saved = customerRepository.save(Customer.builder()
                    .name(name).contactEmail(email).contactPhone(phone).address(address)
                    .active(true).build());
            log.info("Seeded customer: {}", name);
            return saved;
        });
    }

    private Site seedSite(Customer customer, String name, String address, String city, String state, String postal) {
        return siteRepository.findByCustomerIdAndName(customer.getId(), name).orElseGet(() -> {
            Site saved = siteRepository.save(Site.builder()
                    .customerId(customer.getId()).name(name).address(address)
                    .city(city).state(state).country("USA").postalCode(postal)
                    .active(true).build());
            log.info("Seeded site: {} / {}", customer.getName(), name);
            return saved;
        });
    }

    private User seedUser(String email, String password, String first, String last, Role role, String customerId) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User saved = userRepository.save(User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode(password))
                    .firstName(first)
                    .lastName(last)
                    .role(role)
                    .customerId(customerId)
                    .enabled(true)
                    .build());
            log.info("Seeded user: {}", email);
            return saved;
        });
    }

    private void seedPart(String pn, String name, String desc, String uom, int qty, int reorder, BigDecimal cost) {
        if (partRepository.findByPartNumber(pn).isEmpty()) {
            partRepository.save(Part.builder()
                    .partNumber(pn).name(name).description(desc)
                    .unitOfMeasure(uom).quantityInStock(qty).reorderLevel(reorder)
                    .unitCost(cost).build());
            log.info("Seeded part: {}", pn);
        }
    }

    private void seedDemoWorkOrders(Customer acme, Customer techVenture, Site hq, Site warehouse, Site austin,
                                    User manager, User charlie, User diana) {
        Instant now = Instant.now();

        WorkOrder w1 = baseWorkOrder("WO-DEMO-0001", "Chiller unit tripping on high pressure",
                "Rooftop chiller shuts down every 20 minutes. Tenant reporting warm floors.",
                Priority.CRITICAL, acme, hq, null, now.plus(30, ChronoUnit.MINUTES));
        saveIfNew(w1, history(w1, null, WorkOrderStatus.NEW, manager, now.minus(3, ChronoUnit.HOURS), "Customer reported issue"));

        WorkOrder w2 = baseWorkOrder("WO-DEMO-0002", "Quarterly HVAC filter replacement",
                "Replace all supply-air filters on floors 1 and 2.",
                Priority.HIGH, acme, warehouse, charlie, now.plus(3, ChronoUnit.HOURS));
        w2.setStatus(WorkOrderStatus.ASSIGNED);
        saveIfNew(w2,
                history(w2, null, WorkOrderStatus.NEW, manager, now.minus(20, ChronoUnit.HOURS), "Scheduled maintenance"),
                history(w2, WorkOrderStatus.NEW, WorkOrderStatus.ASSIGNED, manager, now.minus(18, ChronoUnit.HOURS), "Assigned to Charlie Tech"));

        WorkOrder w3 = baseWorkOrder("WO-DEMO-0003", "Server room cooling failure",
                "Precision AC is blowing warm air. Server room at 29C.",
                Priority.MEDIUM, techVenture, austin, diana, now.plus(2, ChronoUnit.DAYS));
        w3.setStatus(WorkOrderStatus.IN_PROGRESS);
        w3.getTimeLogs().add(TimeLog.builder()
                .technicianId(diana.getId()).technicianName(diana.getFullName())
                .minutesLogged(45).loggedAt(now.minus(4, ChronoUnit.HOURS)).notes("Diagnosed blocked condenser coil").build());
        partRepository.findByPartNumber("FILT-001").ifPresent(filter -> {
            w3.getPartUsages().add(PartUsage.builder()
                    .partId(filter.getId()).partNumber(filter.getPartNumber()).partName(filter.getName())
                    .quantityUsed(2).unitCostAtTime(filter.getUnitCost())
                    .addedByUserId(diana.getId()).addedAt(now.minus(3, ChronoUnit.HOURS)).notes("Replaced clogged filters")
                    .build());
            filter.setQuantityInStock(filter.getQuantityInStock() - 2);
            partRepository.save(filter);
        });
        saveIfNew(w3,
                history(w3, null, WorkOrderStatus.NEW, manager, now.minus(30, ChronoUnit.HOURS), "Request from customer portal"),
                history(w3, WorkOrderStatus.NEW, WorkOrderStatus.ASSIGNED, manager, now.minus(28, ChronoUnit.HOURS), "Assigned to Diana Tech"),
                history(w3, WorkOrderStatus.ASSIGNED, WorkOrderStatus.IN_PROGRESS, diana, now.minus(5, ChronoUnit.HOURS), "On site, diagnosing"));

        WorkOrder w4 = baseWorkOrder("WO-DEMO-0004", "Loading dock door not closing",
                "Roller door stalls halfway; awaiting replacement motor.",
                Priority.MEDIUM, acme, hq, charlie, now.plus(5, ChronoUnit.DAYS));
        w4.setStatus(WorkOrderStatus.ON_HOLD);
        saveIfNew(w4,
                history(w4, null, WorkOrderStatus.NEW, manager, now.minus(2, ChronoUnit.DAYS), "Reported by site manager"),
                history(w4, WorkOrderStatus.NEW, WorkOrderStatus.ASSIGNED, manager, now.minus(2, ChronoUnit.DAYS), "Assigned to Charlie Tech"),
                history(w4, WorkOrderStatus.ASSIGNED, WorkOrderStatus.IN_PROGRESS, charlie, now.minus(30, ChronoUnit.HOURS), "Inspected motor"),
                history(w4, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD, charlie, now.minus(28, ChronoUnit.HOURS), "Waiting on parts"));

        WorkOrder w5 = baseWorkOrder("WO-DEMO-0005", "Emergency lighting inspection",
                "Monthly inspection of emergency lights in stairwells.",
                Priority.LOW, acme, hq, diana, now.plus(2, ChronoUnit.DAYS));
        w5.setStatus(WorkOrderStatus.COMPLETED);
        w5.setCompletedAt(now.minus(1, ChronoUnit.HOURS));
        saveIfNew(w5,
                history(w5, null, WorkOrderStatus.NEW, manager, now.minus(3, ChronoUnit.DAYS), "Routine inspection"),
                history(w5, WorkOrderStatus.NEW, WorkOrderStatus.ASSIGNED, manager, now.minus(3, ChronoUnit.DAYS), "Assigned to Diana Tech"),
                history(w5, WorkOrderStatus.ASSIGNED, WorkOrderStatus.IN_PROGRESS, diana, now.minus(5, ChronoUnit.HOURS), "Started inspection"),
                history(w5, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED, diana, now.minus(1, ChronoUnit.HOURS), "All units pass"));

        WorkOrder w6 = baseWorkOrder("WO-DEMO-0006", "Repair water leak in basement",
                "Pipe joint leaking near pump room.",
                Priority.HIGH, acme, warehouse, charlie, now.minus(3, ChronoUnit.DAYS));
        w6.setStatus(WorkOrderStatus.CLOSED);
        w6.setCompletedAt(now.minus(4, ChronoUnit.DAYS));
        w6.setClosedAt(now.minus(3, ChronoUnit.DAYS));
        w6.setSlaStatus(com.keystone.fsm.domain.enums.SlaStatus.ON_TRACK);
        saveIfNew(w6,
                history(w6, null, WorkOrderStatus.NEW, manager, now.minus(5, ChronoUnit.DAYS), "Leak reported"),
                history(w6, WorkOrderStatus.NEW, WorkOrderStatus.ASSIGNED, manager, now.minus(5, ChronoUnit.DAYS), "Assigned to Charlie Tech"),
                history(w6, WorkOrderStatus.ASSIGNED, WorkOrderStatus.IN_PROGRESS, charlie, now.minus(5, ChronoUnit.DAYS), "On site"),
                history(w6, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED, charlie, now.minus(4, ChronoUnit.DAYS), "Joint replaced"),
                history(w6, WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED, manager, now.minus(3, ChronoUnit.DAYS), "Signed off by customer"));

        WorkOrder w7 = baseWorkOrder("WO-DEMO-0007", "Elevator machine room overheating",
                "Temperature alarm active; ventilation fan not running.",
                Priority.CRITICAL, acme, hq, charlie, now.minus(1, ChronoUnit.HOURS));
        w7.setStatus(WorkOrderStatus.IN_PROGRESS);
        w7.getTimeLogs().add(TimeLog.builder()
                .technicianId(charlie.getId()).technicianName(charlie.getFullName())
                .minutesLogged(120).loggedAt(now.minus(30, ChronoUnit.MINUTES)).notes("Replaced fan capacitor").build());
        saveIfNew(w7,
                history(w7, null, WorkOrderStatus.NEW, manager, now.minus(6, ChronoUnit.HOURS), "Alarm raised"),
                history(w7, WorkOrderStatus.NEW, WorkOrderStatus.ASSIGNED, manager, now.minus(6, ChronoUnit.HOURS), "Assigned to Charlie Tech"),
                history(w7, WorkOrderStatus.ASSIGNED, WorkOrderStatus.IN_PROGRESS, charlie, now.minus(5, ChronoUnit.HOURS), "Attending site"));

        WorkOrder w8 = baseWorkOrder("WO-DEMO-0008", "Request additional parking signage",
                "Tenant asked for two extra visitor parking signs.",
                Priority.LOW, techVenture, austin, null, now.plus(4, ChronoUnit.DAYS));
        w8.setStatus(WorkOrderStatus.CANCELLED);
        w8.setSlaStatus(com.keystone.fsm.domain.enums.SlaStatus.ON_TRACK);
        saveIfNew(w8,
                history(w8, null, WorkOrderStatus.NEW, manager, now.minus(2, ChronoUnit.DAYS), "Raised from portal"),
                history(w8, WorkOrderStatus.NEW, WorkOrderStatus.CANCELLED, manager, now.minus(1, ChronoUnit.DAYS), "Out of scope — not a maintenance task"));
    }

    private WorkOrder baseWorkOrder(String code, String title, String description, Priority priority,
                                    Customer customer, Site site, User technician, Instant dueDate) {
        Instant now = Instant.now();
        return WorkOrder.builder()
                .code(code)
                .title(title)
                .description(description)
                .priority(priority)
                .status(WorkOrderStatus.NEW)
                .slaStatus(SlaPolicy.statusFor(priority, dueDate, now))
                .customerId(customer.getId())
                .customerName(customer.getName())
                .siteId(site.getId())
                .siteName(site.getName())
                .assignedTechnicianId(technician == null ? null : technician.getId())
                .assignedTechnicianName(technician == null ? null : technician.getFullName())
                .slaDueDate(dueDate)
                .build();
    }

    private StatusHistory history(WorkOrder wo, WorkOrderStatus from, WorkOrderStatus to,
                                  User by, Instant when, String note) {
        return StatusHistory.builder()
                .previousStatus(from)
                .newStatus(to)
                .changedByUserId(by.getId())
                .changedByUserName(by.getEmail())
                .changedAt(when)
                .note(note)
                .build();
    }

    private void saveIfNew(WorkOrder wo, StatusHistory... entries) {
        if (workOrderRepository.findByCode(wo.getCode()).isPresent()) {
            return;
        }
        List.of(entries).forEach(h -> wo.getStatusHistory().add(h));
        workOrderRepository.save(wo);
        log.info("Seeded work order: {}", wo.getCode());
    }
}
