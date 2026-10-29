package com.keystone.fsm;

import com.fasterxml.jackson.databind.JsonNode;
import com.keystone.fsm.domain.model.Customer;
import com.keystone.fsm.domain.model.Part;
import com.keystone.fsm.domain.model.Site;
import com.keystone.fsm.domain.model.User;
import com.keystone.fsm.repository.CustomerRepository;
import com.keystone.fsm.repository.PartRepository;
import com.keystone.fsm.repository.SiteRepository;
import com.keystone.fsm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the governed lifecycle, role scoping, inventory integrity,
 * notifications and attachment access through the real HTTP stack.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WorkflowSecurityIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private PartRepository partRepository;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Test
    void newWorkOrderCannotJumpStraightToInProgress() {
        Session manager = login("manager@keystone.com", "Manager1!");
        JsonNode wo = createWorkOrder(manager.token(), "Jump test", acme().getId(), acmeSite().getId());

        ResponseEntity<JsonNode> response = transition(manager.token(), wo.get("id").asText(), "IN_PROGRESS", "skip");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(getWorkOrder(manager.token(), wo.get("id").asText()).get("status").asText())
                .isEqualTo("NEW");
    }

    @Test
    void onlyAssignedTechnicianCanStartAndOnlyManagerCanClose() {
        Session manager = login("manager@keystone.com", "Manager1!");
        Session charlie = login("tech1@keystone.com", "Technic1!");
        Session diana = login("tech2@keystone.com", "Technic2!");

        JsonNode wo = createWorkOrder(manager.token(), "Close guard", acme().getId(), acmeSite().getId());
        String id = wo.get("id").asText();
        assertThat(assign(manager.token(), id, charlie.id()).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(transition(diana.token(), id, "IN_PROGRESS", "not mine").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(transition(charlie.token(), id, "IN_PROGRESS", "on site").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(transition(charlie.token(), id, "COMPLETED", "done").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(transition(charlie.token(), id, "CLOSED", "sign off").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(transition(manager.token(), id, "CLOSED", "signed off").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void technicianCannotRaiseWorkOrders() {
        Session charlie = login("tech1@keystone.com", "Technic1!");

        ResponseEntity<JsonNode> response = createWorkOrderResponse(
                charlie.token(), "Should fail", acme().getId(), acmeSite().getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void customerRequestsAreScopedAndCannotBeAssignedByCustomer() {
        Session customer = login("customer@acmecorp.com", "Customer1!");

        JsonNode created = createWorkOrder(customer.token(), "Leaking tap", null, acmeSite().getId());
        assertThat(created.get("customerId").asText()).isEqualTo(acme().getId());

        ResponseEntity<JsonNode> foreign = createWorkOrderResponse(
                customer.token(), "Other tenant", techVenture().getId(), techVentureSite().getId());
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        User charlie = userRepository.findByEmail("tech1@keystone.com").orElseThrow();
        Map<String, Object> withTech = workOrderPayload(
                "Self assign", acme().getId(), acmeSite().getId());
        withTech.put("assignedTechnicianId", charlie.getId());
        assertThat(post(customer.token(), "/api/work-orders", withTech).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void customerCannotChangeStatusOrReadAnotherOrganisation() {
        Session manager = login("manager@keystone.com", "Manager1!");
        Session acmeCustomer = login("customer@acmecorp.com", "Customer1!");
        Session ventureCustomer = login("customer@techventure.com", "Customer2!");

        JsonNode acmeWo = createWorkOrder(manager.token(), "Status guard", acme().getId(), acmeSite().getId());
        String id = acmeWo.get("id").asText();

        assertThat(transition(acmeCustomer.token(), id, "IN_PROGRESS", "self serve").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(ventureCustomer.token(), "/api/work-orders/" + id).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(acmeCustomer.token(), "/api/work-orders/" + id).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void technicianReadsAndListsOnlyOwnAssignments() {
        Session manager = login("manager@keystone.com", "Manager1!");
        Session charlie = login("tech1@keystone.com", "Technic1!");
        Session diana = login("tech2@keystone.com", "Technic2!");

        JsonNode wo = createWorkOrder(manager.token(), "Diana only", acme().getId(), acmeSite().getId());
        String id = wo.get("id").asText();
        assertThat(assign(manager.token(), id, diana.id()).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(get(charlie.token(), "/api/work-orders/" + id).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(diana.token(), "/api/work-orders/" + id).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode page = get(charlie.token(), "/api/work-orders?size=100").getBody();
        assertThat(page).isNotNull();
        page.get("content").forEach(node ->
                assertThat(node.get("assignedTechnicianId").asText()).isEqualTo(charlie.id()));
    }

    // ── Inventory ────────────────────────────────────────────────────────────

    @Test
    void partUsageDecrementsStockAndRejectsOverdraw() {
        Session manager = login("manager@keystone.com", "Manager1!");
        Session charlie = login("tech1@keystone.com", "Technic1!");

        JsonNode wo = createWorkOrder(manager.token(), "Stock integrity", acme().getId(), acmeSite().getId());
        String id = wo.get("id").asText();
        assertThat(assign(manager.token(), id, charlie.id()).getStatusCode()).isEqualTo(HttpStatus.OK);

        Part filter = partRepository.findByPartNumber("FILT-001").orElseThrow();
        int before = filter.getQuantityInStock();

        Map<String, Object> use = Map.of("partId", filter.getId(), "quantityUsed", 1, "notes", "test");
        ResponseEntity<JsonNode> used = post(charlie.token(), "/api/work-orders/" + id + "/parts", use);
        assertThat(used.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(used.getBody().get("partUsages").size()).isPositive();
        assertThat(used.getBody().get("partsCostTotal").decimalValue()).isPositive();
        assertThat(partRepository.findByPartNumber("FILT-001").orElseThrow().getQuantityInStock())
                .isEqualTo(before - 1);

        Map<String, Object> overdraw = Map.of("partId", filter.getId(), "quantityUsed", 1_000_000);
        assertThat(post(charlie.token(), "/api/work-orders/" + id + "/parts", overdraw).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(partRepository.findByPartNumber("FILT-001").orElseThrow().getQuantityInStock())
                .isEqualTo(before - 1);
    }

    // ── Notifications ────────────────────────────────────────────────────────

    @Test
    void assignmentNotifiesTechnician() {
        Session manager = login("manager@keystone.com", "Manager1!");
        Session diana = login("tech2@keystone.com", "Technic2!");

        JsonNode wo = createWorkOrder(manager.token(), "Notify me", acme().getId(), acmeSite().getId());
        String code = wo.get("code").asText();
        assertThat(assign(manager.token(), wo.get("id").asText(), diana.id()).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> page = get(diana.token(), "/api/notifications?size=100");
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        boolean found = false;
        for (JsonNode n : page.getBody().get("content")) {
            if (code.equals(n.get("workOrderCode").asText())) {
                found = true;
                assertThat(n.get("type").asText()).isEqualTo("ASSIGNMENT");
            }
        }
        assertThat(found).as("assignment notification for %s", code).isTrue();

        ResponseEntity<JsonNode> unread = get(diana.token(), "/api/notifications/unread-count");
        assertThat(unread.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unread.getBody().get("count").asLong()).isPositive();

        assertThat(post(diana.token(), "/api/notifications/read-all", null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get(diana.token(), "/api/notifications/unread-count").getBody().get("count").asLong())
                .isZero();
    }

    // ── Attachments ──────────────────────────────────────────────────────────

    @Test
    void photosAreUploadedDownloadedAndTenantScoped() {
        Session manager = login("manager@keystone.com", "Manager1!");
        Session charlie = login("tech1@keystone.com", "Technic1!");
        Session acmeCustomer = login("customer@acmecorp.com", "Customer1!");
        Session ventureCustomer = login("customer@techventure.com", "Customer2!");

        JsonNode wo = createWorkOrder(manager.token(), "Photo evidence", acme().getId(), acmeSite().getId());
        String id = wo.get("id").asText();
        assertThat(assign(manager.token(), id, charlie.id()).getStatusCode()).isEqualTo(HttpStatus.OK);

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        ResponseEntity<JsonNode> uploaded = upload(charlie.token(), id, png);
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode attachments = uploaded.getBody().get("attachments");
        assertThat(attachments.size()).isEqualTo(1);
        String attachmentId = attachments.get(0).get("id").asText();
        assertThat(attachments.get(0).get("fileName").asText()).isEqualTo("photo.png");

        String url = "/api/work-orders/" + id + "/attachments/" + attachmentId;
        ResponseEntity<byte[]> downloaded = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(bearer(acmeCustomer.token())), byte[].class);
        assertThat(downloaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(downloaded.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(downloaded.getBody()).isEqualTo(png);

        assertThat(restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(bearer(ventureCustomer.token())), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<JsonNode> deleted = restTemplate.exchange(
                "/api/work-orders/" + id + "/attachments/" + attachmentId, HttpMethod.DELETE,
                new HttpEntity<>(bearer(manager.token())), JsonNode.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleted.getBody().get("attachments").isEmpty()).isTrue();
    }

    // ── Reporting ────────────────────────────────────────────────────────────

    @Test
    void dashboardIsAvailableToStaffAndIncludesSiteBreakdown() {
        Session manager = login("manager@keystone.com", "Manager1!");
        Session charlie = login("tech1@keystone.com", "Technic1!");

        ResponseEntity<JsonNode> summary = get(manager.token(), "/api/reports/summary");
        assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summary.getBody().has("bySite")).isTrue();
        assertThat(summary.getBody().get("bySite").size()).isPositive();
        assertThat(summary.getBody().get("slaOnTrackCount").asLong()).isGreaterThanOrEqualTo(0);

        assertThat(get(charlie.token(), "/api/reports/summary").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private record Session(String token, String id, String customerId) {}

    private Session login(String email, String password) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "/api/auth/login", Map.of("email", email, "password", password), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        return new Session(body.get("token").asText(), body.get("id").asText(),
                body.hasNonNull("customerId") ? body.get("customerId").asText() : null);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private ResponseEntity<JsonNode> post(String token, String url, Object body) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String token, String url) {
        return restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(bearer(token)), JsonNode.class);
    }

    private Map<String, Object> workOrderPayload(String title, String customerId, String siteId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("description", "Created by integration test");
        payload.put("priority", "MEDIUM");
        payload.put("siteId", siteId);
        if (customerId != null) {
            payload.put("customerId", customerId);
        }
        return payload;
    }

    private ResponseEntity<JsonNode> createWorkOrderResponse(
            String token, String title, String customerId, String siteId) {
        return post(token, "/api/work-orders", workOrderPayload(title, customerId, siteId));
    }

    private JsonNode createWorkOrder(String token, String title, String customerId, String siteId) {
        ResponseEntity<JsonNode> response = createWorkOrderResponse(token, title, customerId, siteId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private ResponseEntity<JsonNode> assign(String token, String id, String technicianId) {
        return post(token, "/api/work-orders/" + id + "/assign", Map.of("technicianId", technicianId));
    }

    private ResponseEntity<JsonNode> transition(String token, String id, String status, String note) {
        Map<String, Object> body = new HashMap<>();
        body.put("newStatus", status);
        body.put("note", note);
        return post(token, "/api/work-orders/" + id + "/status", body);
    }

    private JsonNode getWorkOrder(String token, String id) {
        ResponseEntity<JsonNode> response = get(token, "/api/work-orders/" + id);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<JsonNode> upload(String token, String workOrderId, byte[] bytes) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.IMAGE_PNG);
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "photo.png";
            }
        };
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new HttpEntity<>(resource, partHeaders));

        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange("/api/work-orders/" + workOrderId + "/attachments",
                HttpMethod.POST, new HttpEntity<>(form, headers), JsonNode.class);
    }

    private Customer acme() {
        return customerRepository.findByName("Acme Corp").orElseThrow();
    }

    private Customer techVenture() {
        return customerRepository.findByName("TechVenture Ltd").orElseThrow();
    }

    private Site acmeSite() {
        return siteRepository.findByCustomerIdAndName(acme().getId(), "Main HQ").orElseThrow();
    }

    private Site techVentureSite() {
        return siteRepository.findByCustomerIdAndName(techVenture().getId(), "Austin Campus").orElseThrow();
    }
}
