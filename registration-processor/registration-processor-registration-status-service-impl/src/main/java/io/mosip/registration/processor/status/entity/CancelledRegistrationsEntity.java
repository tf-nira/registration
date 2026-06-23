package io.mosip.registration.processor.status.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cancelled_reg", schema = "regprc")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelledRegistrationsEntity {

    @Id
    @Column(name = "rid")
    private String rid;

    @Column(name = "nin")
    private String nin;

    @Column(name = "deactivated")
    private boolean deactivated;
}