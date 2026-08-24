# SQL Query Call Flow Diagram

## Complete Call Stack

This document shows the complete call flow from the application stages down to the actual SQL query execution.

## Call Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    APPLICATION STAGES                            │
│  (7 Different Stages Call This Query)                           │
└─────────────────────────────────────────────────────────────────┘
                                 │
                                 ├─── PacketValidateProcessor.java (line 491)
                                 ├─── BiometricAuthenticationStage.java (line 172)
                                 ├─── MessageSenderStage.java (line 217)
                                 ├─── MVSServiceImpl.java (line 237)
                                 ├─── SupervisorApprovalStatusTagGenerator.java (line 49)
                                 ├─── SecurezoneNotificationStage.java (line 325)
                                 └─── PacketUploaderServiceImpl.java (line 202)
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SERVICE LAYER                                 │
│  SyncRegistrationServiceImpl.findByWorkflowInstanceId()         │
│  (line 616-618)                                                  │
└─────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    DAO LAYER                                     │
│  SyncRegistrationDao.findByWorkflowInstanceId()                 │
│  (line 106-110)                                                  │
└─────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    REPOSITORY LAYER (JPA)                        │
│  SyncRegistrationRepository.findByworkflowInstanceId()          │
│  (line 43-44)                                                    │
│                                                                  │
│  OR                                                              │
│                                                                  │
│  RegistrationRepositary.findByWorkflowInstanceId()              │
│  (line 56-57)                                                    │
└─────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    JPQL QUERY                                    │
│  @Query("SELECT registration FROM                                │
│         RegistrationStatusEntity registration                    │
│         WHERE registration.id.workflowInstanceId =               │
│               :workflowInstanceId                                │
│         AND registration.isDeleted = false                       │
│         AND registration.isActive = true")                       │
└─────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    HIBERNATE/JPA                                 │
│  Translates JPQL to Native SQL                                   │
└─────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    NATIVE SQL QUERY                              │
│  SELECT registrati0_.workflow_instance_id,                       │
│         registrati0_.applicant_type,                             │
│         ... (all 32 columns) ...                                 │
│  FROM regprc.registration registrati0_                           │
│  WHERE registrati0_.workflow_instance_id = $1                    │
│    AND registrati0_.is_deleted = false                           │
│    AND registrati0_.is_active = true                             │
└─────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    DATABASE (PostgreSQL)                         │
│  Table: regprc.registration                                      │
└─────────────────────────────────────────────────────────────────┘
```

## Detailed Breakdown by Stage

### 1. Pre-Processing Stages

#### A. Packet Validator Stage
```
PacketValidateProcessor.process()
  └─> syncRegistrationservice.findByWorkflowInstanceId(object.getWorkflowInstanceId())
      └─> SyncRegistrationServiceImpl.findByWorkflowInstanceId()
          └─> SyncRegistrationDao.findByWorkflowInstanceId()
              └─> SyncRegistrationRepository.findByworkflowInstanceId()
                  └─> [SQL QUERY EXECUTED]
```

#### B. Packet Classifier Stage
```
SupervisorApprovalStatusTagGenerator.generateTags()
  └─> syncRegistrationService.findByWorkflowInstanceId(workflowInstanceId)
      └─> [Same call stack as above]
```

#### C. Securezone Notification Stage
```
SecurezoneNotificationStage.process()
  └─> syncRegistrationService.findByWorkflowInstanceId(messageDTO.getWorkflowInstanceId())
      └─> [Same call stack as above]
```

#### D. Packet Uploader Stage
```
PacketUploaderServiceImpl.validateAndUploadPacket()
  └─> syncRegistrationService.findByWorkflowInstanceId(messageDTO.getWorkflowInstanceId())
      └─> [Same call stack as above]
```

### 2. Core Processing Stages

#### A. Biometric Authentication Stage
```
BiometricAuthenticationStage.process()
  └─> syncRegistrationservice.findByWorkflowInstanceId(object.getWorkflowInstanceId())
      └─> [Same call stack as above]
```

#### B. Manual Verification Service (MVS)
```
MVSServiceImpl.manualAdjudicationStatus()
  └─> syncRegistrationService.findByWorkflowInstanceId(messageDTO.getWorkflowInstanceId())
      └─> [Same call stack as above]
```

### 3. Post-Processing Stages

#### A. Message Sender Stage
```
MessageSenderStage.process()
  └─> syncRegistrationservice.findByWorkflowInstanceId(object.getWorkflowInstanceId())
      └─> [Same call stack as above]
```

## Alternative Path (Direct Repository Access)

Some parts of the code may directly use `RegistrationRepositary`:

```
[Some Service/DAO]
  └─> registrationStatusRepositary.findByWorkflowInstanceId(workflowInstanceId)
      └─> RegistrationRepositary.findByWorkflowInstanceId()
          └─> [SQL QUERY EXECUTED]
```

This is used in:
- `RegistrationStatusDao.java` (lines 119, 138)

## Key Points

1. **Single Entry Point:** All calls funnel through the same repository method
2. **Consistent Query:** The SQL query is always the same regardless of which stage initiates it
3. **Service Layer:** Most calls go through `SyncRegistrationService` for consistency
4. **Workflow Context:** The query is always triggered during packet processing workflows
5. **Parameter:** Always uses `workflowInstanceId` as the search parameter

## When Does This Query Fire?

The query fires during these workflow events:

1. **Packet Validation** - When validating incoming packets
2. **Packet Classification** - When classifying packets by supervisor approval
3. **Secure Zone Notification** - When sending secure zone notifications
4. **Packet Upload** - When uploading packets
5. **Biometric Authentication** - When authenticating biometric data
6. **Manual Verification** - When manually verifying packets
7. **Message Sending** - When sending messages about packet status

All of these are part of the registration packet processing pipeline.
