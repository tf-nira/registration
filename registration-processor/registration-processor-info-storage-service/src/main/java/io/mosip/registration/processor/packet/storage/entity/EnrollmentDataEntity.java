package io.mosip.registration.processor.packet.storage.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.io.Serializable;

@Setter
@Getter
@Entity
@Table(name = "enrollment_data", schema = "regprc")
public class EnrollmentDataEntity extends BasePacketEntity<EnrollmentDataPKEntity> implements Serializable {

    @Column(name = "enrollment_date")
    private String enrollmentDate;
}
