package io.mosip.registration.processor.citizenship.verification.constants;

public enum Relationship {
	FATHER("Father"),
	MOTHER("Mother"), 
	
	GRAND_FATHER_ON_FATHERS_SIDE("Grandfather on Father's side"),
	GRAND_FATHER_ON_MOTHERS_SIDE ("Grandfather on Mother's side"),
	
    GRAND_MOTHER_ON_FATHERS_SIDE("Grandmother on Father's side"), 
    GRAND_MOTHER_ON_MOTHERS_SIDE("Grandmother on Mother's side"), 
    
    MATERNAL_AUNT("Maternal Aunt"),
    PATERNAL_AUNT("Paternal Aunt"),
    MATERNAL_UNCLE("Maternal Uncle"),
    PATERNAL_UNCLE("Paternal Uncle"),
    
	BROTHER("Biological Brother"),
	SISTER("Biological Sister"),
	
	FIRST_COUSIN_FATHERS_SIDE ("1st Cousin (Father's side)"),
    FIRST_COUSIN_MOTHERS_SIDE("1st Cousin (Mother's side)"),

    OTHER("Other");

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
