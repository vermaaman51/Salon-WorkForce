-- One-time adoption for an existing database already upgraded through old V13.
-- Back up the database and stop the application first. Never run this on a new database.
-- It changes Flyway metadata only; all application tables and rows are preserved.
BEGIN;
DO $$
BEGIN
 IF to_regclass('flyway_schema_history_before_final_schema') IS NOT NULL THEN
  RAISE EXCEPTION 'Old history is already archived; do not adopt twice';
 END IF;
 IF (SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1) IS DISTINCT FROM '13' THEN
  RAISE EXCEPTION 'Expected the old schema at V13 before adoption';
 END IF;
 IF EXISTS(SELECT 1 FROM flyway_schema_history WHERE NOT success) THEN
  RAISE EXCEPTION 'Resolve failed migrations before adoption';
 END IF;
 IF (SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema()
     AND table_name IN ('tenant','users','role','store','user_role','orders','order_history'))<>7 THEN
  RAISE EXCEPTION 'Expected all seven final application tables';
 END IF;
 IF EXISTS(SELECT 1 FROM role WHERE roletype IN ('ROOT','WORKER')) THEN
  RAISE EXCEPTION 'Existing roles have not been upgraded to ADMIN/EXPERT';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='orders' AND column_name='payment_mode')
    OR NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='orders' AND column_name='service')
    OR NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='order_history' AND column_name='payment_mode') THEN
  RAISE EXCEPTION 'Existing schema is missing final order columns';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='orders'::regclass AND tgname='orders_history_capture' AND tgenabled='O')
    OR NOT EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid='order_history'::regclass AND tgname='order_history_immutable' AND tgenabled='O') THEN
  RAISE EXCEPTION 'Expected enabled order snapshot and history protection triggers';
 END IF;
END $$;

ALTER TABLE flyway_schema_history RENAME TO flyway_schema_history_before_final_schema;
ALTER TABLE flyway_schema_history_before_final_schema RENAME CONSTRAINT flyway_schema_history_pk TO flyway_schema_history_before_final_schema_pk;
ALTER INDEX flyway_schema_history_s_idx RENAME TO flyway_schema_history_before_final_schema_s_idx;

CREATE TABLE flyway_schema_history (
 installed_rank INTEGER NOT NULL,
 version VARCHAR(50),
 description VARCHAR(200) NOT NULL,
 type VARCHAR(20) NOT NULL,
 script VARCHAR(1000) NOT NULL,
 checksum INTEGER,
 installed_by VARCHAR(100) NOT NULL,
 installed_on TIMESTAMP NOT NULL DEFAULT now(),
 execution_time INTEGER NOT NULL,
 success BOOLEAN NOT NULL,
 CONSTRAINT flyway_schema_history_pk PRIMARY KEY(installed_rank)
);
CREATE INDEX flyway_schema_history_s_idx ON flyway_schema_history(success);
INSERT INTO flyway_schema_history(installed_rank,version,description,type,script,installed_by,execution_time,success)
VALUES(1,'7','Final schema baseline','BASELINE','<< Flyway Baseline >>',current_user,0,true);
COMMIT;
