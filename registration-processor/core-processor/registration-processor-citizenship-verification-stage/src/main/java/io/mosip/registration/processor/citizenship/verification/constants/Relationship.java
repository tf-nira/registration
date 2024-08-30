package io.mosip.registration.processor.citizenship.verification.constants;

public enum Relationship {
	FATHER("Father"), //tested and complete
	MOTHER("Mother"), //tested and complete
    GRAND_MOTHER_ON_FATHERS_SIDE("Grandmother on Father's side"),
    GRAND_FATHER_ON_FATHERS_SIDE("Grandfather on Father's side"), //tested and complete
    MATERNAL_UCLE_OR_AUNT("Maternal Uncle or Aunt"),
    PATERNAL_UCLE_OR_AUNT("Paternal Uncle or Aunt"),
	BROTHER_OR_SISTER("Sister or Brother"); //tested and complete

    private final String relation;

    private Relationship(String relation) {
        this.relation = relation;
    }
    public String getRelationship() {
    	return relation;
    }
    
    public static Relationship fromString(String relation) {
        for (Relationship r : Relationship.values()) {
            if (r.getRelationship().equalsIgnoreCase(relation)) {
                return r;
            }
        }
        throw new IllegalArgumentException("No enum constant for relation: " + relation);
    }
}
