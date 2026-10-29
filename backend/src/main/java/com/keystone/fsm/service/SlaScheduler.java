package com.keystone.fsm.service;

import com.keystone.fsm.domain.SlaPolicy;
import com.keystone.fsm.domain.enums.NotificationType;
import com.keystone.fsm.domain.enums.SlaStatus;
import com.keystone.fsm.domain.model.WorkOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Evaluates response-time commitments for every active work order.
 * Marks jobs AT_RISK once 80% of their SLA window has elapsed and BREACHED
 * once the due date passes; a breach is never downgraded back to at-risk.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaScheduler {

    private final WorkOrderService workOrderService;
    private final NotificationService notificationService;

    @Scheduled(
            initialDelayString = "${app.sla.check-initial-delay-ms:60000}",
            fixedDelayString = "${app.sla.check-interval-ms:300000}")
    public void checkSlaCompliance() {
        List<WorkOrder> active = workOrderService.findActiveWorkOrders();
        Instant now = Instant.now();
        int breached = 0;
        int atRisk = 0;

        for (WorkOrder wo : active) {
            if (wo.getSlaStatus() == SlaStatus.BREACHED) {
                continue;
            }
            SlaStatus computed = SlaPolicy.statusFor(wo, now);
            if (computed == wo.getSlaStatus()) {
                continue;
            }
            workOrderService.updateSlaStatus(wo, computed);
            if (computed == SlaStatus.BREACHED) {
                breached++;
                notificationService.notifyManagersOnce(wo.getId(), NotificationType.SLA_BREACH,
                        "SLA breached",
                        wo.getCode() + " — " + wo.getTitle() + " passed its " + wo.getPriority() + " deadline",
                        wo);
                log.warn("SLA BREACHED: {} ({}), due {}", wo.getCode(), wo.getTitle(), wo.getSlaDueDate());
            } else if (computed == SlaStatus.AT_RISK) {
                atRisk++;
                log.info("SLA AT RISK: {} ({}), due {}", wo.getCode(), wo.getTitle(), wo.getSlaDueDate());
            }
        }

        log.info("SLA check finished: {} active, {} newly at risk, {} newly breached", active.size(), atRisk, breached);
    }
}
