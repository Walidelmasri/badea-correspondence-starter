-- ONE-TIME PREDEPLOY MIGRATION under STRATEGYKPI (existing four-table schema).
-- Must be reviewed and backed up by the database administrator.
-- Oracle DDL auto-commits. No new tables, deletes or employee appointment rewrites.
-- Existing legacy secretary links remain intact; new secretary rows use NULL.
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET SERVEROUTPUT ON
DECLARE
  v_table NUMBER;
  v_nullable VARCHAR2(1);
BEGIN
  IF USER <> 'STRATEGYKPI' OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA') <> 'STRATEGYKPI' THEN
    RAISE_APPLICATION_ERROR(-20901, 'STOP: Must connect as STRATEGYKPI in its own schema');
  END IF;
  SELECT COUNT(*) INTO v_table FROM USER_TABLES
    WHERE TABLE_NAME IN ('TARASOL_SECRETARY_ASSIGNMENTS', 'TARASOL_ORG_UNITS');
  IF v_table <> 2 THEN
    RAISE_APPLICATION_ERROR(-20902, 'STOP: Required Tarasol tables missing');
  END IF;
  SELECT NULLABLE INTO v_nullable FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TARASOL_SECRETARY_ASSIGNMENTS'
      AND COLUMN_NAME = 'MANAGER_ASSIGNMENT_ID';
  IF v_nullable <> 'N' THEN
    RAISE_APPLICATION_ERROR(-20903, 'STOP: Column already nullable; no migration needed');
  END IF;
  DBMS_OUTPUT.PUT_LINE('Preflight passed. Only manager link nullability will change.');
END;
/
ALTER TABLE TARASOL_SECRETARY_ASSIGNMENTS MODIFY (MANAGER_ASSIGNMENT_ID NULL);
DECLARE
  v_nullable VARCHAR2(1);
BEGIN
  SELECT NULLABLE INTO v_nullable FROM USER_TAB_COLUMNS
    WHERE TABLE_NAME = 'TARASOL_SECRETARY_ASSIGNMENTS'
      AND COLUMN_NAME = 'MANAGER_ASSIGNMENT_ID';
  IF v_nullable <> 'Y' THEN RAISE_APPLICATION_ERROR(-20904, 'STOP: Migration verification failed'); END IF;
  DBMS_OUTPUT.PUT_LINE('Migration verified: secretaries may now belong to units without a manager');
END;
/
