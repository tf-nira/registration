package io.mosip.registration.processor.stages.legacy.data.val.dto;

import lombok.Data;

@Data
public class Fingerprint {

	private String position;
    private String wsq;
    
    @Override
    public String toString() {
        return "Fingerprint{" +
                "position='" + position + '\'' +
                ", wsq='" + wsq + '\'' +
                '}';
    }
}
