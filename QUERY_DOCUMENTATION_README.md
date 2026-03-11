# README: SQL Query Location Documentation

## Overview

This documentation set provides comprehensive information about where and how a specific SQL query is executed in the registration-processor application.

## Query in Question

The SQL query that selects from `regprc.registration` table, filtering by:
- `workflow_instance_id` 
- `is_deleted = false`
- `is_active = true`

## Documentation Files

### 1. QUERY_LOCATION_ANALYSIS.md
**Purpose:** Complete technical analysis

**Contents:**
- Full SQL query details
- Repository layer method definitions
- DAO layer implementations
- Service layer methods
- All 7 application stages that execute the query
- Test files that use the method
- Complete summary

**Use when:** You need detailed technical information about the query's implementation and usage.

### 2. QUERY_CALL_FLOW.md
**Purpose:** Visual understanding of execution flow

**Contents:**
- Call stack diagrams
- Flow from application stages to database
- Breakdown by each processing stage
- Alternative execution paths
- Workflow event triggers

**Use when:** You need to understand the execution path and how the query is invoked.

### 3. QUERY_QUICK_REFERENCE.md
**Purpose:** Quick developer reference

**Contents:**
- File locations and line numbers
- Quick grep commands
- Summary table of stages
- Modification guidelines
- Performance considerations

**Use when:** You need to quickly find the query or understand basic usage.

## Quick Answer

**Where is the query defined?**
```
File: RegistrationRepositary.java
Path: registration-processor/registration-processor-registration-status-service-impl/
      src/main/java/io/mosip/registration/processor/status/repositary/
Line: 56-57
Method: findByWorkflowInstanceId(String workflowInstanceId)
```

**Where is it called?**
7 processing stages in the registration workflow:
1. PacketValidateProcessor
2. BiometricAuthenticationStage
3. MessageSenderStage
4. MVSServiceImpl
5. SupervisorApprovalStatusTagGenerator
6. SecurezoneNotificationStage
7. PacketUploaderServiceImpl

## Technology Stack

- **Framework:** Spring Data JPA
- **Query Language:** JPQL (Java Persistence Query Language)
- **ORM:** Hibernate
- **Database:** PostgreSQL
- **Schema:** regprc
- **Table:** registration

## Important Note on Spelling

The codebase uses "repositary" (a misspelling of "repository") in:
- Directory names: `src/.../repositary/`
- Class names: `RegistrationRepositary.java`
- Interface names: `SyncRegistrationRepository.java` (this one is correct)

This documentation reflects the actual spelling as it exists in the codebase.

## How to Use This Documentation

1. **Start with QUERY_QUICK_REFERENCE.md** for basic information
2. **Refer to QUERY_CALL_FLOW.md** to understand execution flow
3. **Dive into QUERY_LOCATION_ANALYSIS.md** for complete technical details

## Contributing

If you make changes to the query or add new stages that use it, please update:
- QUERY_LOCATION_ANALYSIS.md (add to stages list)
- QUERY_CALL_FLOW.md (add to diagram)
- QUERY_QUICK_REFERENCE.md (add to table)

## Related Code

- **Entity:** `RegistrationStatusEntity.java`
- **Alternative Entity:** `SyncRegistrationEntity.java`
- **DAO:** `SyncRegistrationDao.java`, `RegistrationStatusDao.java`
- **Service:** `SyncRegistrationService.java` and implementation

## Questions?

For questions about:
- **Query definition:** See QUERY_LOCATION_ANALYSIS.md, "Repository Layer" section
- **Query execution:** See QUERY_CALL_FLOW.md
- **Quick lookup:** See QUERY_QUICK_REFERENCE.md
