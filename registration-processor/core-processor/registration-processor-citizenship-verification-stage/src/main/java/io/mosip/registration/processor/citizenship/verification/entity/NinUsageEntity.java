package io.mosip.registration.processor.citizenship.verification.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
//import javax.persistence.GeneratedValue;
//import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "consumed_nin_introducer", schema = "regprc")
public class NinUsageEntity implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "nin")
	private String nin;

	@Column(name = "usage_count")
	private int usageCount;

	@Column(name = "last_used")
	private LocalDateTime lastUsed;

	public void incrementUsage() {
		this.usageCount++;
		this.lastUsed = LocalDateTime.now(); // Updating the last used time to current time
	}
}
