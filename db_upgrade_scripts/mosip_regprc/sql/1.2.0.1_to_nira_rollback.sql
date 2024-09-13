\c mosip_regprc

REASSIGN OWNED BY postgres TO sysadmin;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA regprc TO sysadmin;

DROP TABLE IF EXISTS regprc.consumed__nin_introducer CASCADE;

ALTER TABLE regprc.individual_demographic_dedup DROP COLUMN IF EXISTS nrcid;

---------------------delete scripts keep always last if it throws error need to handle it manually--------------------------------------
DELETE from regprc.transaction_type where code='CITIZENSHIP_VERIFICATION';
DELETE from regprc.transaction_type where code='PAYMENT_VALIDATION';

