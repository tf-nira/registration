package io.mosip.registration.processor.securezone.notification.entity;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;
@Data
@Entity
@Table(name = "registration", schema = "regprc")
public class Registration {

    private String regId;
    private String process;
    private String refRegId;
    private String applicantType;
    private String statusCode;
    private String langCode;
    private String statusComment;
    private String latestTrnId;
    private String latestTrnTypeCode;
    private String latestTrnStatusCode;
    private LocalDateTime latestTrnDtimes;
    private Integer regProcessRetryCount;
    private String regStageName;
    private Integer trnRetryCount;
    private LocalDateTime pktCrDtimes;
    private Boolean isActive;
    private String crBy;
    private LocalDateTime crDtimes;
    private String updBy;
    private LocalDateTime updDtimes;
    private Boolean isDeleted;
    private LocalDateTime delDtimes;
    private LocalDateTime resumeTimestamp;
    private String defaultResumeAction;
    private String pauseRuleIds;
    private String lastSuccessStageName;
    @Id
    private String workflowInstanceId;
    private String source;
    private Integer iteration;
    private Boolean needsNotification;
    private Boolean notificationSent;
    private Boolean isAnonymousProfileAdded;



}
