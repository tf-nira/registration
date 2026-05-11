package io.mosip.registration.processor.status.entity;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import lombok.Data;

@Data
@Entity
@Table(name = "reg_notification_message", schema = "regprc")
public class NotificationMessageEntity {
	
	@Id
	@Column(name = "reg_id")
	private String regId;
	
	@Column(name = "notification_message")
	private String notificationMessage;
	
	@Column(name = "sent_to_opencrvs")
	private Boolean sentToOpencrvs;
	
	@NotNull
	@Column(name = "cr_by")
	private String createdBy;

	@NotNull
	@Column(name = "cr_dtimes", updatable = false)
	private LocalDateTime createDateTime;
	
	@Column(name = "upd_by")
	private String updatedBy;

	@Column(name = "upd_dtimes")
	private LocalDateTime updateDateTime;

	@Column(name = "is_deleted")
	private Boolean isDeleted;

	@Column(name = "del_dtimes")
	private LocalDateTime deletedDateTime;
}
