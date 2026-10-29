package com.keystone.fsm.repository;

import com.keystone.fsm.domain.enums.Role;
import com.keystone.fsm.domain.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRoleAndEnabledTrue(Role role);
}
