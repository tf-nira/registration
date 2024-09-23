\c mosip_regprc

REASSIGN OWNED BY sysadmin TO postgres;

REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA regprc FROM regprcuser;

REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA regprc FROM sysadmin;

GRANT SELECT, INSERT, TRUNCATE, REFERENCES, UPDATE, DELETE ON ALL TABLES IN SCHEMA regprc TO regprcuser;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA regprc TO postgres;

----------------------------------------------Multiple table level changes on regprc db-------------------------------------------------------
CREATE TABLE consumed__nin_introducer (
    nin character varying (500) PRIMARY KEY,
    usage_count INTEGER,
    last_used TIMESTAMP WITHOUT TIME ZONE
);


INSERT INTO regprc.transaction_type (code,descr,lang_code,is_active,cr_by,cr_dtimes,upd_by,upd_dtimes,is_deleted,del_dtimes) VALUES
	 ('CITIZENSHIP_VERIFICATION','transaction_done','eng',true,'MOSIP_SYSTEM',now(),NULL,NULL,false,NULL);
INSERT INTO regprc.transaction_type (code,descr,lang_code,is_active,cr_by,cr_dtimes,upd_by,upd_dtimes,is_deleted,del_dtimes) VALUES
	 ('PAYMENT_VALIDATION','transaction_done','eng',true,'MOSIP_SYSTEM',now(),NULL,NULL,false,NULL);


