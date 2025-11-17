package io.mosip.registration.processor.stages.legacy.data.val.dto;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;

import lombok.Data;

@Data
public class IdentifyPersonGraphQLResponse {
	private boolean success;
	private String message;
	private String requestId;
	private String recordId;
	private String processingTime;
	private List <Person> person;
	private TransactionStatus transactionStatus;
	
	public IdentifyPersonGraphQLResponse(boolean success, String message, String requestId, 
            String recordId, String processingTime, 
            String nationalId, List<Person> person, TransactionStatus transactionStatus) {
		this.success = success;
        this.message = message;
        this.requestId = requestId;
        this.recordId = recordId;
        this.processingTime = processingTime;
        this.person = person;
        this.transactionStatus = transactionStatus;
	}
	
	public static class Person {
		private String nationalId;
		private List<Score> scores;
		
		public Person(String nationalId, List<Score> scores) {
			this.nationalId = nationalId;
			this.scores = scores;
		}
		
		public String getNationalId() { return nationalId;}
		public List<Score> getScore() {return scores;}
		
	}
	
	// Inner class for scores
    public static class Score {
        private int position;
        private int hitPosition;
        private int score;
        
        public Score(int position, int hitPosition, int score) {
            this.position = position;
            this.hitPosition = hitPosition;
            this.score = score;
        }
        
        public int getPosition() { return position; }
        public int getHitPosition() { return hitPosition; }
        public int getScore() { return score; }
    }
    
    @Data
    public static class TransactionStatus {
    	private String transactionStatus;
    	private Error error;
    	private int passwordDaysLeft;
    	private double executionCost;
    }
    
    @Override
    public String toString() {
        return "IdentificationResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", requestId='" + requestId + '\'' +
                ", recordId='" + recordId + '\'' +
                ", processingTime='" + processingTime + '\'' +
                '}';
    }
}
