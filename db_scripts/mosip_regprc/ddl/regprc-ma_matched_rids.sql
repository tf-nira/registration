CREATE TABLE regprc.ma_matched_rids (
    reg_id character varying(39) NOT NULL,
    matched_reg_ids character varying,
    matched_count INTEGER NOT NULL,
    is_issued boolean DEFAULT FALSE,
    credential_id character varying(64),
    remark character varying,
    cr_by character varying(256) NOT NULL,
    cr_dtimes timestamp NOT NULL,
    del_dtimes timestamp,
    is_deleted boolean DEFAULT FALSE,
    upd_by character varying(256),
    upd_dtimes timestamp,
    CONSTRAINT pk_ma_matched_rids PRIMARY KEY (reg_id)
);