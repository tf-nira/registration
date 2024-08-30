package io.mosip.registration.processor.citizenship.verification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.mosip.registration.processor.citizenship.verification.entity.NinUsageEntity;


@Repository
public interface NinUsageRepository extends JpaRepository<NinUsageEntity, String> {
    
	NinUsageEntity findByNin(String nin);
	
}
