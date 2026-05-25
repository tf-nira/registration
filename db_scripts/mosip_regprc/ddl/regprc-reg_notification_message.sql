CREATE TABLE regprc.reg_notification_message (
    reg_id character varying(39) NOT NULL PRIMARY KEY,
    notification_message character varying,
	sent_to_opencrvs boolean DEFAULT FALSE,
    cr_by character varying(256) NOT NULL,
	cr_dtimes timestamp NOT NULL,
	upd_by character varying(256),
	upd_dtimes timestamp,
	is_deleted boolean DEFAULT FALSE,
	del_dtimes timestamp
);