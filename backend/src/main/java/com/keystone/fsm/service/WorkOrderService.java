package com.keystone.fsm.service;

import com.keystone.fsm.domain.SlaPolicy;
import com.keystone.fsm.domain.enums.*;
import com.keystone.fsm.domain.model.*;
import com.keystone.fsm.dto.request.*;
import com.keystone.fsm.dto.response.WorkOrderResponse;
import com.keystone.fsm.exception.*;
import com.keystone.fsm.repository.*;
import com.keystone.fsm.security.KeystonePrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final CustomerService customerService;
    private final SiteService siteService;
    private final UserService userService;
    private final PartRepository partRepository;
    private final MongoTemplate mongoTemplate;
    private final NotificationService notificationService;
    private final AttachmentService attachmentService;

    private static final Set<WorkOrderStatus> TERMINAL = EnumSet.of(
            WorkOrderStatus.CLOSED, WorkOrderStatus.CANCELLED);

    private static final Set<WorkOrderStatus> CLOSED_FOR_SLA = EnumSet.of(
            WorkOrderStatus.CLOSED, WorkOrderStatus.CANCELLED, WorkOrderStatus.COMPLETED);

    /**
     * Governed lifecycle. NEW only leaves via assignment (POST /assign) or cancellation,
     * so a job can never jump straight onto the board as ASSIGNED without an owner.
     */
    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> TRANSITIONS = Map.of(
            WorkOrderStatus.NEW, EnumSet.of(WorkOrderStatus.CANCELLED),
            WorkOrderStatus.ASSIGNED, EnumSet.of(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.CANCELLED),
            WorkOrderStatus.IN_PROGRESS, EnumSet.of(WorkOrderStatus.ON_HOLD, WorkOrderStatus.COMPLETED, WorkOrderStatus.CANCELLED),
            WorkOrderStatus.ON_HOLD, EnumSet.of(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.CANCELLED),
            WorkOrderStatus.COMPLETED, EnumSet.of(WorkOrderStatus.CLOSED, WorkOrderStatus.IN_PROGRESS),
            WorkOrderStatus.CLOSED, EnumSet.noneOf(WorkOrderStatus.class),
            WorkOrderStatus.CANCELLED, EnumSet.noneOf(WorkOrderStatus.class)
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // CREATION
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public WorkOrderResponse createWorkOrder(CreateWorkOrderRequest request, KeystonePrincipal actor) {
        Role role = extractRole(actor);
        if (role == Role.TECHNICIAN) {
            throw new AccessDeniedException("Technicians cannot raise work orders");
        }

        String customerId = resolveCustomerForCreation(request, actor, role);
        Customer customer = customerService.getCustomerEntity(customerId);
        Site site = siteService.getSiteEntity(request.siteId());
        if (!site.getCustomerId().equals(customer.getId())) {
            throw new BusinessRuleException("Site does not belong to the specified customer");
        }

        Instant now = Instant.now();
        WorkOrder.WorkOrderBuilder builder = WorkOrder.builder()
                .code(nextWorkOrderCode())
                .title(request.title().trim())
                .description(request.description())
                .priority(request.priority())
                .status(WorkOrderStatus.NEW)
                .slaStatus(SlaStatus.ON_TRACK)
                .customerId(customer.getId())
                .customerName(customer.getName())
                .siteId(site.getId())
                .siteName(site.getName())
                .slaDueDate(SlaPolicy.dueAt(request.priority(), now));

        if (role == Role.CUSTOMER) {
            if (request.assignedTechnicianId() != null && !request.assignedTechnicianId().isBlank()) {
                throw new AccessDeniedException("Customers cannot assign technicians");
            }
        } else if (request.assignedTechnicianId() != null && !request.assignedTechnicianId().isBlank()) {
            User tech = requireTechnician(request.assignedTechnicianId());
            builder.assignedTechnicianId(tech.getId())
                    .assignedTechnicianName(tech.getFullName())
                    .status(WorkOrderStatus.ASSIGNED);
        }

        WorkOrder wo = builder.build();
        wo.getStatusHistory().add(historyEntry(null, wo.getStatus(), actor, "Work order created"));

        WorkOrder saved = workOrderRepository.save(wo);

        if (role == Role.CUSTOMER) {
            notificationService.notifyRole(Role.DISPATCHER, NotificationType.CUSTOMER_REQUEST,
                    "New customer request", saved.getCode() + " — " + saved.getTitle(), saved);
            notificationService.notifyRole(Role.MANAGER, NotificationType.CUSTOMER_REQUEST,
                    "New customer request", saved.getCode() + " — " + saved.getTitle(), saved);
        }
        log.info("Work order {} created by {} (customer={})", saved.getCode(), actor.getUsername(), customerId);
        return toResponse(saved, actor);
    }

    private String resolveCustomerForCreation(CreateWorkOrderRequest request, KeystonePrincipal actor, Role role) {
        if (role == Role.CUSTOMER) {
            String own = actor.getCustomerId();
            if (own == null || own.isBlank()) {
                throw new AccessDeniedException("Customer account is not linked to an organisation");
            }
            if (request.customerId() != null && !request.customerId().isBlank()
                    && !own.equals(request.customerId())) {
                throw new AccessDeniedException("Customers may only raise requests for their own sites");
            }
            return own;
        }
        if (request.customerId() == null || request.customerId().isBlank()) {
            throw new BusinessRuleException("customerId is required");
        }
        return request.customerId();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RETRIEVAL
    // ═══════════════════════════════════════════════════════════════════════════

    public WorkOrderResponse getWorkOrderById(String id, KeystonePrincipal actor) {
        return toResponse(findAndAuthorizeRead(id, actor), actor);
    }

    public Page<WorkOrderResponse> getWorkOrders(
            String search, WorkOrderStatus status, Priority priority,
            String customerId, String technicianId,
            KeystonePrincipal actor, Pageable pageable) {

        Role role = extractRole(actor);
        List<Criteria> filters = new ArrayList<>();

        switch (role) {
            case CUSTOMER -> {
                if (actor.getCustomerId() == null) {
                    return Page.empty(pageable);
                }
                filters.add(Criteria.where("customerId").is(actor.getCustomerId()));
            }
            case TECHNICIAN -> filters.add(Criteria.where("assignedTechnicianId").is(actor.getId()));
            default -> {
                if (customerId != null && !customerId.isBlank()) {
                    filters.add(Criteria.where("customerId").is(customerId));
                }
                if (technicianId != null && !technicianId.isBlank()) {
                    filters.add(Criteria.where("assignedTechnicianId").is(technicianId));
                }
            }
        }

        if (status != null) {
            filters.add(Criteria.where("status").is(status));
        }
        if (priority != null) {
            filters.add(Criteria.where("priority").is(priority));
        }
        if (search != null && !search.isBlank()) {
            String quoted = Pattern.quote(search.trim());
            filters.add(new Criteria().orOperator(
                    Criteria.where("title").regex(quoted, "i"),
                    Criteria.where("description").regex(quoted, "i"),
                    Criteria.where("code").regex(quoted, "i")));
        }

        Criteria criteria = filters.isEmpty()
                ? new Criteria()
                : new Criteria().andOperator(filters.toArray(Criteria[]::new));

        Query countQuery = new Query(criteria);
        long total = mongoTemplate.count(countQuery, WorkOrder.class);

        Query query = new Query(criteria).with(pageable);
        List<WorkOrderResponse> content = mongoTemplate.find(query, WorkOrder.class).stream()
                .map(wo -> toResponse(wo, actor))
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public WorkOrderResponse updateWorkOrder(String id, UpdateWorkOrderRequest request, KeystonePrincipal actor) {
        requireRole(actor, Role.DISPATCHER, Role.MANAGER);
        WorkOrder wo = findWorkOrder(id);
        assertNotTerminal(wo);

        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new BusinessRuleException("Title cannot be blank");
            }
            wo.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            wo.setDescription(request.description());
        }
        if (request.priority() != null) {
            wo.setPriority(request.priority());
            wo.setSlaDueDate(SlaPolicy.dueAt(request.priority(), Instant.now()));
            if (wo.getSlaStatus() != SlaStatus.BREACHED) {
                wo.setSlaStatus(SlaPolicy.statusFor(wo, Instant.now()));
            }
        }
        if (request.siteId() != null && !request.siteId().isBlank()) {
            Site site = siteService.getSiteEntity(request.siteId());
            if (!site.getCustomerId().equals(wo.getCustomerId())) {
                throw new BusinessRuleException("Site does not belong to work order's customer");
            }
            wo.setSiteId(site.getId());
            wo.setSiteName(site.getName());
        }

        WorkOrder saved = workOrderRepository.save(wo);
        log.info("Work order {} updated by {}", saved.getCode(), actor.getUsername());
        return toResponse(saved, actor);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ASSIGN
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public WorkOrderResponse assignTechnician(String id, AssignTechnicianRequest request, KeystonePrincipal actor) {
        requireRole(actor, Role.DISPATCHER, Role.MANAGER);
        WorkOrder wo = findWorkOrder(id);
        assertNotTerminal(wo);

        User technician = requireTechnician(request.technicianId());
        if (technician.getId().equals(wo.getAssignedTechnicianId())) {
            throw new ConflictException("Technician " + technician.getFullName() + " is already assigned to " + wo.getCode());
        }

        WorkOrderStatus oldStatus = wo.getStatus();
        wo.setAssignedTechnicianId(technician.getId());
        wo.setAssignedTechnicianName(technician.getFullName());

        if (oldStatus == WorkOrderStatus.NEW) {
            wo.setStatus(WorkOrderStatus.ASSIGNED);
            wo.getStatusHistory().add(historyEntry(oldStatus, WorkOrderStatus.ASSIGNED, actor,
                    "Assigned to " + technician.getFullName()));
        } else {
            log.info("Work order {} reassigned to {} by {}", wo.getCode(), technician.getFullName(), actor.getUsername());
        }

        WorkOrder saved = workOrderRepository.save(wo);
        notificationService.notifyUser(technician.getId(), NotificationType.ASSIGNMENT,
                "New work order assigned",
                saved.getCode() + " — " + saved.getTitle() + " (" + saved.getPriority() + ")",
                saved);
        log.info("Work order {} assigned to {} by {}", saved.getCode(), technician.getEmail(), actor.getUsername());
        return toResponse(saved, actor);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATUS TRANSITION
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public WorkOrderResponse transitionStatus(String id, StatusTransitionRequest request, KeystonePrincipal actor) {
        WorkOrder wo = findWorkOrder(id);
        WorkOrderStatus from = wo.getStatus();
        WorkOrderStatus to = request.newStatus();

        validateTransition(wo, from, to, actor);

        Instant now = Instant.now();
        wo.setStatus(to);
        switch (to) {
            case COMPLETED -> wo.setCompletedAt(now);
            case CLOSED -> wo.setClosedAt(now);
            case IN_PROGRESS -> {
                if (from == WorkOrderStatus.COMPLETED) {
                    wo.setCompletedAt(null);
                }
            }
            default -> { }
        }
        if (to == WorkOrderStatus.COMPLETED || to == WorkOrderStatus.CLOSED) {
            // Freeze SLA evaluation outcomes once the job is done or signed off.
            if (wo.getSlaStatus() != SlaStatus.BREACHED) {
                wo.setSlaStatus(wo.getSlaDueDate() != null && now.isAfter(wo.getSlaDueDate())
                        ? SlaStatus.BREACHED
                        : wo.getSlaStatus());
            }
        }

        wo.getStatusHistory().add(historyEntry(from, to, actor, request.note()));
        WorkOrder saved = workOrderRepository.save(wo);
        log.info("Work order {} transitioned {} -> {} by {}", saved.getCode(), from, to, actor.getUsername());
        return toResponse(saved, actor);
    }

    private void validateTransition(WorkOrder wo, WorkOrderStatus from, WorkOrderStatus to, KeystonePrincipal actor) {
        Role role = extractRole(actor);
        if (role == Role.CUSTOMER) {
            throw new AccessDeniedException("Customers cannot change work-order status");
        }
        if (TERMINAL.contains(from)) {
            throw new IllegalTransitionException("Work order " + wo.getCode() + " is " + from + " and cannot change status");
        }

        Set<WorkOrderStatus> allowed = TRANSITIONS.getOrDefault(from, EnumSet.noneOf(WorkOrderStatus.class));
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(
                    "Cannot move work order from " + from + " to " + to);
        }

        switch (to) {
            case CANCELLED -> requireRole(actor, Role.DISPATCHER, Role.MANAGER);
            case CLOSED -> requireRole(actor, Role.MANAGER);
            case IN_PROGRESS -> {
                if (from == WorkOrderStatus.COMPLETED) {
                    requireRole(actor, Role.DISPATCHER, Role.MANAGER);
                } else {
                    assertAssignedTechnician(wo, actor);
                }
            }
            default -> assertAssignedTechnician(wo, actor);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PARTS USAGE  (atomic inventory decrement)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public WorkOrderResponse addPartUsage(String id, AddPartUsageRequest request, KeystonePrincipal actor) {
        WorkOrder wo = findWorkOrder(id);
        assertNotTerminal(wo);
        assertCanWorkOn(wo, actor);

        Part part = partRepository.findById(request.partId())
                .orElseThrow(() -> new ResourceNotFoundException("Part", request.partId()));

        Query q = new Query(Criteria.where("_id").is(part.getId())
                .and("quantityInStock").gte(request.quantityUsed()));
        Update u = new Update().inc("quantityInStock", -request.quantityUsed());
        Part updated = mongoTemplate.findAndModify(q, u, FindAndModifyOptions.options().returnNew(true), Part.class);
        if (updated == null) {
            throw new ConflictException("Insufficient stock for part " + part.getPartNumber()
                    + ". Available: " + part.getQuantityInStock());
        }

        PartUsage usage = PartUsage.builder()
                .partId(part.getId())
                .partNumber(part.getPartNumber())
                .partName(part.getName())
                .quantityUsed(request.quantityUsed())
                .unitCostAtTime(part.getUnitCost())
                .addedByUserId(actor.getId())
                .addedAt(Instant.now())
                .notes(request.notes())
                .build();

        wo.getPartUsages().add(usage);
        WorkOrder saved = workOrderRepository.save(wo);
        log.info("{} x {} logged on {} by {} (stock now {})",
                request.quantityUsed(), part.getPartNumber(), saved.getCode(), actor.getUsername(), updated.getQuantityInStock());
        return toResponse(saved, actor);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TIME LOGGING
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public WorkOrderResponse logTime(String id, LogTimeRequest request, KeystonePrincipal actor) {
        WorkOrder wo = findWorkOrder(id);
        assertNotTerminal(wo);
        assertCanWorkOn(wo, actor);

        User actorUser = userService.getUserEntityById(actor.getId());
        TimeLog tl = TimeLog.builder()
                .technicianId(actor.getId())
                .technicianName(actorUser.getFullName())
                .minutesLogged(request.minutesLogged())
                .loggedAt(Instant.now())
                .notes(request.notes())
                .build();

        wo.getTimeLogs().add(tl);
        WorkOrder saved = workOrderRepository.save(wo);
        log.info("{} minutes logged on {} by {}", request.minutesLogged(), saved.getCode(), actor.getUsername());
        return toResponse(saved, actor);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ATTACHMENTS
    // ═══════════════════════════════════════════════════════════════════════════

    public record AttachmentFile(Attachment attachment, Resource resource) {}

    @Transactional
    public WorkOrderResponse addAttachment(String id, MultipartFile file, KeystonePrincipal actor) {
        WorkOrder wo = findWorkOrder(id);
        assertNotTerminal(wo);
        assertCanWorkOn(wo, actor);

        User actorUser = userService.getUserEntityById(actor.getId());
        Attachment attachment = attachmentService.store(file, wo.getId(), actor.getId(), actorUser.getFullName());
        wo.getAttachments().add(attachment);
        WorkOrder saved = workOrderRepository.save(wo);
        log.info("Attachment {} added to {} by {}", attachment.getFileName(), saved.getCode(), actor.getUsername());
        return toResponse(saved, actor);
    }

    public AttachmentFile getAttachment(String id, String attachmentId, KeystonePrincipal actor) {
        WorkOrder wo = findAndAuthorizeRead(id, actor);
        Attachment attachment = wo.getAttachments().stream()
                .filter(a -> a.getId().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));
        return new AttachmentFile(attachment, attachmentService.load(attachment));
    }

    @Transactional
    public WorkOrderResponse deleteAttachment(String id, String attachmentId, KeystonePrincipal actor) {
        WorkOrder wo = findWorkOrder(id);
        assertNotTerminal(wo);
        Attachment attachment = wo.getAttachments().stream()
                .filter(a -> a.getId().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", attachmentId));

        Role role = extractRole(actor);
        boolean isUploader = actor.getId().equals(attachment.getUploadedByUserId());
        boolean isManager = role == Role.MANAGER;
        if (!isManager && !isUploader) {
            throw new AccessDeniedException("Only a manager or the uploader can delete this photo");
        }
        if (role == Role.TECHNICIAN) {
            assertAssignedTechnician(wo, actor);
        }

        wo.getAttachments().remove(attachment);
        WorkOrder saved = workOrderRepository.save(wo);
        attachmentService.delete(attachment);
        log.info("Attachment {} removed from {} by {}", attachmentId, saved.getCode(), actor.getUsername());
        return toResponse(saved, actor);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SLA SCHEDULER SUPPORT
    // ═══════════════════════════════════════════════════════════════════════════

    public List<WorkOrder> findActiveWorkOrders() {
        return workOrderRepository.findByStatusNotIn(CLOSED_FOR_SLA);
    }

    public void updateSlaStatus(WorkOrder wo, SlaStatus status) {
        wo.setSlaStatus(status);
        workOrderRepository.save(wo);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private WorkOrder findWorkOrder(String id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrder", id));
    }

    private String nextWorkOrderCode() {
        String day = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        DailySequence seq = mongoTemplate.findAndModify(
                new Query(Criteria.where("_id").is(day)),
                new Update().inc("seq", 1),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                DailySequence.class);
        long next = seq == null ? 1 : seq.getSeq();
        return String.format("WO-%s-%04d", day, next);
    }

    private User requireTechnician(String technicianId) {
        User tech = userService.getUserEntityById(technicianId);
        if (tech.getRole() != Role.TECHNICIAN) {
            throw new BusinessRuleException("Assigned user is not a TECHNICIAN");
        }
        if (!tech.isEnabled()) {
            throw new BusinessRuleException("Assigned technician is disabled");
        }
        return tech;
    }

    private WorkOrder findAndAuthorizeRead(String id, KeystonePrincipal actor) {
        WorkOrder wo = findWorkOrder(id);
        Role role = extractRole(actor);
        if (role == Role.CUSTOMER && !actor.getCustomerId().equals(wo.getCustomerId())) {
            throw new AccessDeniedException("Access denied");
        }
        if (role == Role.TECHNICIAN && !actor.getId().equals(wo.getAssignedTechnicianId())) {
            throw new AccessDeniedException("Access denied");
        }
        return wo;
    }

    private WorkOrderResponse toResponse(WorkOrder wo, KeystonePrincipal actor) {
        return extractRole(actor) == Role.CUSTOMER
                ? WorkOrderResponse.forCustomer(wo)
                : WorkOrderResponse.from(wo);
    }

    private void assertAssignedTechnician(WorkOrder wo, KeystonePrincipal actor) {
        if (extractRole(actor) == Role.TECHNICIAN
                && !actor.getId().equals(wo.getAssignedTechnicianId())) {
            throw new AccessDeniedException("Technicians can only act on work orders assigned to them");
        }
    }

    private void assertCanWorkOn(WorkOrder wo, KeystonePrincipal actor) {
        Role role = extractRole(actor);
        if (role == Role.CUSTOMER) {
            throw new AccessDeniedException("Customers cannot perform this action");
        }
        if (role == Role.TECHNICIAN) {
            assertAssignedTechnician(wo, actor);
        }
    }

    private void assertNotTerminal(WorkOrder wo) {
        if (TERMINAL.contains(wo.getStatus())) {
            throw new IllegalTransitionException(
                    "Work order " + wo.getCode() + " is " + wo.getStatus() + " and can no longer be modified");
        }
    }

    private void requireRole(KeystonePrincipal actor, Role... roles) {
        Role actual = extractRole(actor);
        for (Role r : roles) {
            if (actual == r) {
                return;
            }
        }
        throw new AccessDeniedException("Insufficient role for this operation");
    }

    private Role extractRole(KeystonePrincipal actor) {
        try {
            return actor.getRole();
        } catch (RuntimeException e) {
            throw new AccessDeniedException("No role assigned to the current user");
        }
    }

    private StatusHistory historyEntry(WorkOrderStatus from, WorkOrderStatus to,
                                       KeystonePrincipal actor, String note) {
        return StatusHistory.builder()
                .previousStatus(from)
                .newStatus(to)
                .changedByUserId(actor.getId())
                .changedByUserName(actor.getUsername())
                .changedAt(Instant.now())
                .note(note)
                .build();
    }
}
