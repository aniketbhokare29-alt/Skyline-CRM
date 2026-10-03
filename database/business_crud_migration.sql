-- Adds stable IDs to existing Skyline CRM tables without deleting data.
-- Run this once against the skyline_crm database after the initial schema.
USE skyline_crm;

DROP PROCEDURE IF EXISTS ensure_crm_id;
DELIMITER //
CREATE PROCEDURE ensure_crm_id(IN table_name_value VARCHAR(64))
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = table_name_value
      AND column_name = 'id'
  ) THEN
    SET @id_sql = CONCAT(
      'ALTER TABLE `', table_name_value,
      '` ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST'
    );
    PREPARE id_stmt FROM @id_sql;
    EXECUTE id_stmt;
    DEALLOCATE PREPARE id_stmt;
  END IF;
END//
DELIMITER ;

CALL ensure_crm_id('enquiries');
CALL ensure_crm_id('properties');
CALL ensure_crm_id('followups');
CALL ensure_crm_id('qualifications');
CALL ensure_crm_id('sitevisits');
CALL ensure_crm_id('negotiations');
CALL ensure_crm_id('bookings');
CALL ensure_crm_id('documentcollections');
CALL ensure_crm_id('loans');
CALL ensure_crm_id('agreements');
CALL ensure_crm_id('customerpayments');
CALL ensure_crm_id('possessions');
CALL ensure_crm_id('supporttickets');
CALL ensure_crm_id('employees');
CALL ensure_crm_id('attendance');
CALL ensure_crm_id('leaverequests');
CALL ensure_crm_id('salarypayments');
CALL ensure_crm_id('vendors');
CALL ensure_crm_id('purchaseorders');
CALL ensure_crm_id('vendorbills');
CALL ensure_crm_id('vendorpayments');
CALL ensure_crm_id('pettycashentries');
CALL ensure_crm_id('pettycashapprovals');

DROP PROCEDURE IF EXISTS ensure_crm_id;
