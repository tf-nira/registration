package io.mosip.registration.processor.packet.storage.entity;

import java.io.Serializable;
import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "ma_matched_rids", schema = "regprc")
public class MAMatchedRidsEntity extends BasePacketEntity<MAMatchedRidsPKEntity> implements Serializable {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 1L;

	/** The reg id. */
	@Column(name = "matched_reg_ids")
	private String matchedRegIds;
	
	@Column(name = "matched_count")
	private int matchedCount;
	
	@Column(name = "is_issued")
	private boolean isIssued;
	
	@Column(name = "credential_id")
	private String credentialId;
	
	@Column(name = "remark")
	private String remark;

	/** The cr by. */
	@Column(name = "cr_by")
	private String crBy;

	/** The cr dtimes. */
	@Column(name = "cr_dtimes")
	private Timestamp crDtimes;

	/** The del dtimes. */
	@Column(name = "del_dtimes")
	private Timestamp delDtimes;

	/** The is deleted. */
	@Column(name = "is_deleted")
	private Boolean isDeleted;

	/** The upd by. */
	@Column(name = "upd_by")
	private String updBy;

	/** The upd dtimes. */
	@Column(name = "upd_dtimes")
	private Timestamp updDtimes;
	
	public String getMatchedRegIds() {
		return matchedRegIds;
	}

	public void setMatchedRegIds(String matchedRegIds) {
		this.matchedRegIds = matchedRegIds;
	}

	public int getMatchedCount() {
		return matchedCount;
	}

	public void setMatchedCount(int matchedCount) {
		this.matchedCount = matchedCount;
	}

	public boolean isIssued() {
		return isIssued;
	}

	public void setIssued(boolean isIssued) {
		this.isIssued = isIssued;
	}
	
	public String getCredentialId() {
		return credentialId;
	}

	public void setCredentialId(String credentialId) {
		this.credentialId = credentialId;
	}

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark;
	}

	public String getCrBy() {
		return crBy;
	}

	public void setCrBy(String crBy) {
		this.crBy = crBy;
	}

	public Timestamp getCrDtimes() {
		return crDtimes;
	}

	public void setCrDtimes(Timestamp crDtimes) {
		this.crDtimes = crDtimes;
	}

	public Timestamp getDelDtimes() {
		return delDtimes;
	}

	public void setDelDtimes(Timestamp delDtimes) {
		this.delDtimes = delDtimes;
	}

	public Boolean getIsDeleted() {
		return isDeleted;
	}

	public void setIsDeleted(Boolean isDeleted) {
		this.isDeleted = isDeleted;
	}

	public String getUpdBy() {
		return updBy;
	}

	public void setUpdBy(String updBy) {
		this.updBy = updBy;
	}

	public Timestamp getUpdDtimes() {
		return updDtimes;
	}

	public void setUpdDtimes(Timestamp updDtimes) {
		this.updDtimes = updDtimes;
	}
}
