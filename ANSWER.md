# SQL Query Location - Direct Answer

## Problem Statement
Find where the following SQL query is firing in this repository:

```sql
select registrati0_.workflow_instance_id as workflow1_16_, 
       registrati0_.applicant_type as applican2_16_, 
       ... (32 columns total) ...
from regprc.registration registrati0_ 
where registrati0_.workflow_instance_id=$1 
  and registrati0_.is_deleted=false 
  and registrati0_.is_active=true
```

## Direct Answer

### Origin of the Query

**File Location:**
```
registration-processor/registration-processor-registration-status-service-impl/
src/main/java/io/mosip/registration/processor/status/repositary/
RegistrationRepositary.java
```

**Line Numbers:** 56-57

**Java Code:**
```java
@Query("SELECT registration FROM RegistrationStatusEntity registration 
        WHERE registration.id.workflowInstanceId = :workflowInstanceId 
        AND registration.isDeleted =false 
        AND registration.isActive=true")
public List<RegistrationStatusEntity> findByWorkflowInstanceId(
    @Param("workflowInstanceId") String workflowInstanceId);
```

**Explanation:** This is a JPQL (Java Persistence Query Language) query that Hibernate/JPA automatically translates into the native SQL query you provided.

### Where It's Called From

The query is executed from **7 different processing stages**:

#### 1. Packet Validator Stage
- **File:** `registration-processor/pre-processor/registration-processor-packet-validator-stage/src/main/java/io/mosip/registration/processor/stages/packet/validator/PacketValidateProcessor.java`
- **Line:** 491
- **Purpose:** Validates incoming registration packets

#### 2. Biometric Authentication Stage
- **File:** `registration-processor/core-processor/registration-processor-biometric-authentication-stage/src/main/java/io/mosip/registration/processor/biometric/authentication/stage/BiometricAuthenticationStage.java`
- **Line:** 172
- **Purpose:** Authenticates biometric information

#### 3. Message Sender Stage
- **File:** `registration-processor/post-processor/registration-processor-message-sender-stage/src/main/java/io/mosip/registration/processor/message/sender/stage/MessageSenderStage.java`
- **Line:** 217
- **Purpose:** Sends notification messages

#### 4. Manual Verification Service (MVS)
- **File:** `registration-processor/core-processor/registration-processor-mvs-stage/src/main/java/io/mosip/registration/processor/mvs/service/impl/MVSServiceImpl.java`
- **Line:** 237
- **Purpose:** Handles manual verification adjudication

#### 5. Supervisor Approval Tag Generator
- **File:** `registration-processor/pre-processor/registration-processor-packet-classifier-stage/src/main/java/io/mosip/registration/processor/stages/packetclassifier/tagging/impl/SupervisorApprovalStatusTagGenerator.java`
- **Line:** 49
- **Purpose:** Generates tags for supervisor approval workflow

#### 6. Securezone Notification Stage
- **File:** `registration-processor/pre-processor/registration-processor-securezone-notification-stage/src/main/java/io/mosip/registration/processor/securezone/notification/stage/SecurezoneNotificationStage.java`
- **Line:** 325
- **Purpose:** Sends secure zone notifications

#### 7. Packet Uploader Service
- **File:** `registration-processor/pre-processor/registration-processor-packet-uploader-stage/src/main/java/io/mosip/registration/processor/packet/uploader/service/impl/PacketUploaderServiceImpl.java`
- **Line:** 202
- **Purpose:** Uploads registration packets

### Call Path

All 7 stages follow the same call hierarchy:

```
Stage Class
  ↓
SyncRegistrationService.findByWorkflowInstanceId()
  ↓
SyncRegistrationDao.findByWorkflowInstanceId()
  ↓
SyncRegistrationRepository.findByworkflowInstanceId()
  ↓
[JPQL Query Execution]
  ↓
[Hibernate translates to Native SQL]
  ↓
[PostgreSQL executes the query you provided]
```

### Quick Grep Command

To find all usages yourself:
```bash
grep -rn "findByWorkflowInstanceId" registration-processor/
```

## Complete Documentation

For more details, see:
- **QUERY_DOCUMENTATION_README.md** - Start here for overview
- **QUERY_LOCATION_ANALYSIS.md** - Complete technical analysis
- **QUERY_CALL_FLOW.md** - Visual call flow diagrams
- **QUERY_QUICK_REFERENCE.md** - Quick developer reference
