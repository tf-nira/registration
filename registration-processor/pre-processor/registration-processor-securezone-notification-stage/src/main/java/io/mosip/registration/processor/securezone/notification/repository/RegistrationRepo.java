package io.mosip.registration.processor.securezone.notification.repository;

import io.mosip.registration.processor.securezone.notification.entity.Registration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RegistrationRepo extends JpaRepository<Registration, String> {

    @Modifying
    @Transactional
    @Query("UPDATE Registration r SET r.statusCode = 'RESUMABLE' WHERE r.regId = :regid")
    int updateRegistration(@Param("regid") String regId);
}
