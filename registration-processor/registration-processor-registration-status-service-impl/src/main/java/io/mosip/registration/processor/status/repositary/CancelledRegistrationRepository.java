package io.mosip.registration.processor.status.repositary;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.mosip.registration.processor.status.entity.CancelledRegistrationsEntity;

import java.util.List;

@Repository
public interface CancelledRegistrationRepository
        extends JpaRepository<CancelledRegistrationsEntity, String> {

    List<CancelledRegistrationsEntity> findByRidIn(List<String> rids);
}