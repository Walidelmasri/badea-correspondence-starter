-- Execute in SQL Developer as Script (F5), logged into STRATEGYKPI.
-- No DDL or writes in this file.
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE
DECLARE
    v_conflicts NUMBER;
BEGIN
    IF USER <> 'STRATEGYKPI'
       OR SYS_CONTEXT('USERENV','CURRENT_SCHEMA') <> 'STRATEGYKPI' THEN
        RAISE_APPLICATION_ERROR(-20001,
            'STOP: SQL login and current schema must both be STRATEGYKPI');
    END IF;
    SELECT COUNT(*) INTO v_conflicts
    FROM USER_OBJECTS
    WHERE OBJECT_NAME IN (
      'TARASOL_ORG_UNITS',
      'TARASOL_UNIT_ASSIGNMENTS',
      'TARASOL_SECRETARY_ASSIGNMENTS',
      'TARASOL_DELEGATIONS'
    );
    IF v_conflicts <> 0 THEN
        RAISE_APPLICATION_ERROR(-20002,
            'STOP: A Stage 1 object already exists. Inspect partial migrations first.');
    END IF;
    DBMS_OUTPUT.PUT_LINE('PASS: STRATEGYKPI schema; four Stage 1 names are free.');
END;
/
