package io.mosip.registration.processor.citizenship.verification.constants;

//nin usage limit tested and works as expected
public enum FamilyNINUsageLimitConstant {
	FATHER(Relationship.FATHER, 20),
    MOTHER(Relationship.MOTHER, 20),
    
    BROTHER(Relationship.BROTHER, 20),
    SISTER(Relationship.SISTER, 20),
    
    MATERNAL_AUNT(Relationship.MATERNAL_AUNT, 20),
    PATERNAL_AUNT(Relationship.PATERNAL_AUNT, 20),
    MATERNAL_UNCLE(Relationship.MATERNAL_UNCLE, 20),
    PATERNAL_UNCLE(Relationship.PATERNAL_UNCLE, 20),
    
	FIRST_COUSIN_FATHERS_SIDE (Relationship.FIRST_COUSIN_FATHERS_SIDE, 20),
	FIRST_COUSIN_MOTHERS_SIDE(Relationship.FIRST_COUSIN_MOTHERS_SIDE, 20),
    
    GRAND_FATHER_ON_FATHERS_SIDE(Relationship.GRAND_FATHER_ON_FATHERS_SIDE, 40),
    GRAND_FATHER_ON_MOTHERS_SIDE (Relationship.GRAND_FATHER_ON_MOTHERS_SIDE,40),
    
    GRAND_MOTHER_ON_FATHERS_SIDE(Relationship.GRAND_MOTHER_ON_FATHERS_SIDE, 20),
    GRAND_MOTHER_ON_MOTHERS_SIDE(Relationship.GRAND_MOTHER_ON_MOTHERS_SIDE,20),
    
	OTHER(Relationship.OTHER,20);

    private final Relationship  relation;
    private final int limit;

    FamilyNINUsageLimitConstant(Relationship relation, int limit) {
        this.relation = relation;
        this.limit = limit;
    }

    public int getLimit() {
        return limit;
    }
    
    public Relationship getRelation() {
    	return relation;   
}
    
    
    public static FamilyNINUsageLimitConstant fromRelationship(Relationship relation) {
        for (FamilyNINUsageLimitConstant constant : FamilyNINUsageLimitConstant.values()) {
            if (constant.getRelation() == relation) {
                return constant;
            }
        }
        throw new IllegalArgumentException("No enum constant for relation: " + relation);
    }
}