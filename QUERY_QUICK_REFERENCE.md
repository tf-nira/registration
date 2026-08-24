# SQL Query Quick Reference

## Query Identifier
The SQL query that selects from `regprc.registration` table filtering by `workflow_instance_id`, `is_deleted`, and `is_active`.

## Where to Find

### Primary Repository Method
**File:** `registration-processor/registration-processor-registration-status-service-impl/src/main/java/io/mosip/registration/processor/status/repositary/RegistrationRepositary.java`

**Line:** 56-57

**Method Name:** `findByWorkflowInstanceId`

### Alternative Repository Method  
**File:** `registration-processor/registration-processor-registration-status-service-impl/src/main/java/io/mosip/registration/processor/status/repositary/SyncRegistrationRepository.java`

**Line:** 43-44

**Method Name:** `findByworkflowInstanceId` (Note: lowercase 'w' is intentional - actual method name in codebase)

## Quick Grep Command

To find all usages of this query:
```bash
grep -rn "findByWorkflowInstanceId" registration-processor/
```

## Stages That Use This Query

| Stage | File | Line | Purpose |
|-------|------|------|---------|
| Packet Validator | PacketValidateProcessor.java | 491 | Validate packets |
| Biometric Auth | BiometricAuthenticationStage.java | 172 | Authenticate biometrics |
| Message Sender | MessageSenderStage.java | 217 | Send messages |
| MVS | MVSServiceImpl.java | 237 | Manual verification |
| Supervisor Approval | SupervisorApprovalStatusTagGenerator.java | 49 | Tag generation |
| Secure Zone | SecurezoneNotificationStage.java | 325 | Send notifications |
| Packet Uploader | PacketUploaderServiceImpl.java | 202 | Upload packets |

## JPQL Query

```java
@Query("SELECT registration FROM RegistrationStatusEntity registration WHERE registration.id.workflowInstanceId = :workflowInstanceId AND registration.isDeleted =false AND registration.isActive=true")
```

## Database Table
- **Schema:** `regprc`
- **Table:** `registration`
- **Primary Filter Column:** `workflow_instance_id`

## How to Modify

If you need to modify this query:

1. **Repository Level:** Update the `@Query` annotation in `RegistrationRepositary.java` or `SyncRegistrationRepository.java`
2. **Entity Level:** Update `RegistrationStatusEntity.java` or `SyncRegistrationEntity.java` if column mappings change
3. **Test Impact:** Update all test files that mock this method (see QUERY_LOCATION_ANALYSIS.md for list)

## Performance Considerations

The query filters on:
- `workflow_instance_id` (indexed as primary key component)
- `is_deleted` (boolean flag)
- `is_active` (boolean flag)

Ensure these columns have appropriate indexes for optimal performance.

## Related Documentation

- Full analysis: `QUERY_LOCATION_ANALYSIS.md`
- Call flow diagram: `QUERY_CALL_FLOW.md`
