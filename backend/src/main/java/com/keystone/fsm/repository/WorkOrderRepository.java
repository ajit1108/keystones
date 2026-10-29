package com.keystone.fsm.repository;

import com.keystone.fsm.domain.enums.WorkOrderStatus;
import com.keystone.fsm.domain.model.WorkOrder;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkOrderRepository extends MongoRepository<WorkOrder, String> {

    Optional<WorkOrder> findByCode(String code);

    List<WorkOrder> findByStatusNotIn(Collection<WorkOrderStatus> excludedStatuses);
}
