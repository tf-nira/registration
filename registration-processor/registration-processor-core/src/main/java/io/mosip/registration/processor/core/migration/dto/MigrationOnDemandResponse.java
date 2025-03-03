package io.mosip.registration.processor.core.migration.dto;

import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class MigrationOnDemandResponse implements Serializable {

	String rid;

	String status;

}
