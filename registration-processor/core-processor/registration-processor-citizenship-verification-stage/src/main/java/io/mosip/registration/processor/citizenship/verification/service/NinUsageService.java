package io.mosip.registration.processor.citizenship.verification.service;


import io.mosip.kernel.core.util.HMACUtils2;
import io.mosip.registration.processor.citizenship.verification.constants.FamilyNINUsageLimitConstant;
import io.mosip.registration.processor.citizenship.verification.constants.Relationship;
import io.mosip.registration.processor.citizenship.verification.entity.NinUsageEntity;
import io.mosip.registration.processor.citizenship.verification.repository.NinUsageRepository;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NinUsageService {

    @Autowired
    private NinUsageRepository ninUsageRepository;

    public boolean isNinUsedMorethanNtimes(String nin, String relation) throws NoSuchAlgorithmException {
    	//int limit = FamilyNINUsageLimitConstant.fromString(relation).getLimit();
		//int limit = FamilyNINUsageLimitConstant.valueOf(relation).getLimit();
    	Relationship relationshipEnum = Relationship.fromString(relation);
        int limit = FamilyNINUsageLimitConstant.fromRelationship(relationshipEnum).getLimit();
		String hashSequence = HMACUtils2.digestAsPlainText(nin.getBytes(StandardCharsets.UTF_8));
        NinUsageEntity ninUsageEntity = ninUsageRepository.findByNin(hashSequence);

        if (ninUsageEntity != null){
        	if(ninUsageEntity.getUsageCount() >= limit) {
        		return true;
        	} else {
        		ninUsageEntity.setUsageCount(ninUsageEntity.getUsageCount() + 1);
        		ninUsageEntity.setLastUsed(LocalDateTime.now());
        		ninUsageRepository.save(ninUsageEntity);
        		return false;
        	}
        }
        else {
        	NinUsageEntity newNinUsageEntity = new NinUsageEntity();
        	newNinUsageEntity.setNin(hashSequence);
        	newNinUsageEntity.setUsageCount(1);
        	newNinUsageEntity.setLastUsed(LocalDateTime.now());
        	ninUsageRepository.save(newNinUsageEntity);
        	return false;
        }
    }
}

