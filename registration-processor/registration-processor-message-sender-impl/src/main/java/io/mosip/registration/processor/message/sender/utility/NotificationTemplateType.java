package io.mosip.registration.processor.message.sender.utility;

/**
 * The Enum NotificationTemplateType.
 * 
 * @author M1048358
 */
public enum NotificationTemplateType {

	/** The uin generation success. */
	UIN_CREATED,

	/** The uin generation update. */
	UIN_UPDATE,

	/** The duplicate uin. */
	DUPLICATE_UIN,

	/** The technical issue. */
	TECHNICAL_ISSUE,
	
	TECHNICAL_ISSUE_WITH_ERROR,
	
	/** The MVS packet reject. */
	MVS_PACKET_REJECTED,
	
	/** THe Lost UIN. */
	LOST_UIN,
	
	ONDEMAND,

	SUPERVISOR_REJECTION,

	MA_PACKET_REJECTED
}
