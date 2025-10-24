package io.mosip.registration.processor.packet.storage.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

@Setter
@Getter
@Embeddable
public class EnrollmentDataPKEntity implements Serializable {

    @Column(name = "reg_id")
    private String regId;

}
