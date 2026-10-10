CREATE DATABASE IF NOT EXISTS skyline_crm
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE skyline_crm;

CREATE TABLE IF NOT EXISTS enquiries (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255), mobile VARCHAR(255), email VARCHAR(255), city VARCHAR(255),
  budget VARCHAR(255), interestedProperty VARCHAR(255), source VARCHAR(255), status VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS properties (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  unit VARCHAR(191), wing VARCHAR(255), floor VARCHAR(255), flatType VARCHAR(255), area VARCHAR(255),
  price VARCHAR(255), parking VARCHAR(255), availability VARCHAR(255), amenities VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS followups (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer VARCHAR(255), type VARCHAR(255), date VARCHAR(255), time VARCHAR(255),
  nextDate VARCHAR(255), executive VARCHAR(255), remarks VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS qualifications (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer VARCHAR(255), budget VARCHAR(255), loanRequired VARCHAR(255), preferredLocation VARCHAR(255),
  flatType VARCHAR(255), purchaseTimeline VARCHAR(255), purpose VARCHAR(255), status VARCHAR(255), notes VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS sitevisits (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer VARCHAR(255), visitDate VARCHAR(255), visitTime VARCHAR(255), pickupRequired VARCHAR(255),
  executive VARCHAR(255), status VARCHAR(255), feedback VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS negotiations (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer VARCHAR(255), unit VARCHAR(255), offeredPrice VARCHAR(255), discount VARCHAR(255),
  managementApproval VARCHAR(255), specialOffer VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS bookings (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer VARCHAR(255), bookingAmount VARCHAR(255), bookingDate VARCHAR(255), unit VARCHAR(255),
  floor VARCHAR(255), flatType VARCHAR(255), status VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS documentcollections (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer VARCHAR(255), panCard VARCHAR(255), aadhaarCard VARCHAR(255), passportPhoto VARCHAR(255),
  addressProof VARCHAR(255), incomeProof VARCHAR(255), bankStatement VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS documents (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customerId VARCHAR(191) NOT NULL,
  customer VARCHAR(255),
  bookingId VARCHAR(191),
  documentType VARCHAR(64) NOT NULL,
  originalFileName VARCHAR(255),
  storedFileName VARCHAR(255),
  filePath VARCHAR(500) NOT NULL,
  fileType VARCHAR(191),
  fileSize BIGINT NOT NULL DEFAULT 0,
  verificationStatus VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  uploadedBy VARCHAR(191),
  uploadedDate VARCHAR(100),
  version INT NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS loans (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer VARCHAR(255), bankName VARCHAR(255), loanAmount VARCHAR(255), emi VARCHAR(255),
  sanctionDate VARCHAR(255), status VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS agreements (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer VARCHAR(255), unit VARCHAR(255), agreementDate VARCHAR(255), stampDuty VARCHAR(255),
  registrationDate VARCHAR(255), registrationNumber VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS customerpayments (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer VARCHAR(255), installmentType VARCHAR(255), totalCost VARCHAR(255), paidAmount VARCHAR(255),
  pendingAmount VARCHAR(255), dueDate VARCHAR(255), history VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS possessions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer VARCHAR(255), unit VARCHAR(255), finalInspection VARCHAR(255), utilityConnection VARCHAR(255),
  keyHandover VARCHAR(255), possessionLetter VARCHAR(255), status VARCHAR(255), remarks VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS supporttickets (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer VARCHAR(255), unit VARCHAR(255), category VARCHAR(255), priority VARCHAR(255), status VARCHAR(255),
  assignedTo VARCHAR(255), description VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS employees (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  employeeId VARCHAR(191), name VARCHAR(255), department VARCHAR(255), designation VARCHAR(255),
  mobile VARCHAR(255), email VARCHAR(255), dateOfJoining VARCHAR(255), salaryStructure VARCHAR(255),
  bankDetails VARCHAR(255), identityDetails VARCHAR(255), statutoryDetails VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS attendance (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  employeeId VARCHAR(255), employeeName VARCHAR(255), date VARCHAR(255), status VARCHAR(255),
  overtimeHours VARCHAR(255), remarks VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS leaverequests (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  employeeId VARCHAR(255), employeeName VARCHAR(255), fromDate VARCHAR(255), toDate VARCHAR(255),
  leaveType VARCHAR(255), status VARCHAR(255), reason VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS salarypayments (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  employeeId VARCHAR(255), employeeName VARCHAR(255), salaryMonth VARCHAR(255), basicSalary VARCHAR(255),
  hra VARCHAR(255), incentives VARCHAR(255), bonus VARCHAR(255), deductions VARCHAR(255), grossSalary VARCHAR(255),
  netSalary VARCHAR(255), paymentDate VARCHAR(255), paymentMode VARCHAR(255), paymentStatus VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS registeredusers (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255), mobile VARCHAR(255), email VARCHAR(191) UNIQUE,
  role VARCHAR(32) NOT NULL DEFAULT 'EMPLOYEE',
  password VARCHAR(255), createdDate VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS agents (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  agentId VARCHAR(191) UNIQUE, name VARCHAR(255), mobile VARCHAR(255),
  email VARCHAR(191) UNIQUE, role VARCHAR(32) NOT NULL DEFAULT 'AGENT',
  createdDate VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS vendors (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  vendorName VARCHAR(191), companyName VARCHAR(255), gstNumber VARCHAR(255), panNumber VARCHAR(255),
  contactPerson VARCHAR(255), mobile VARCHAR(255), email VARCHAR(255), address VARCHAR(255),
  bankDetails VARCHAR(255), category VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS purchaseorders (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  poNumber VARCHAR(191), date VARCHAR(255), vendorName VARCHAR(255), material VARCHAR(255),
  quantity VARCHAR(255), rate VARCHAR(255), gst VARCHAR(255), totalAmount VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS vendorbills (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  vendorName VARCHAR(95), invoiceNumber VARCHAR(95), invoiceDate VARCHAR(255), billAmount VARCHAR(255),
  gst VARCHAR(255), dueDate VARCHAR(255), status VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS vendorpayments (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  vendorName VARCHAR(255), paymentDate VARCHAR(255), paymentMode VARCHAR(255), transactionNumber VARCHAR(255),
  bank VARCHAR(255), paidAmount VARCHAR(255), balanceAmount VARCHAR(255), status VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS pettycashentries (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  date VARCHAR(255), voucherNumber VARCHAR(191), expenseCategory VARCHAR(255), description VARCHAR(255),
  employeeName VARCHAR(255), amount VARCHAR(255), paymentMode VARCHAR(255), approvedBy VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS pettycashapprovals (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  requestedBy VARCHAR(255), approvedBy VARCHAR(255), amount VARCHAR(255), remarks VARCHAR(255), status VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS sessions (
  token VARCHAR(191) PRIMARY KEY, role VARCHAR(255) NOT NULL, createdAt BIGINT NOT NULL
);

# Upgrade columns created by an earlier failed schema attempt before adding indexes.
ALTER TABLE properties MODIFY COLUMN unit VARCHAR(191);
ALTER TABLE employees MODIFY COLUMN employeeId VARCHAR(191);
ALTER TABLE registeredusers MODIFY COLUMN email VARCHAR(191);
ALTER TABLE registeredusers MODIFY COLUMN role VARCHAR(32) NOT NULL DEFAULT 'EMPLOYEE';
ALTER TABLE purchaseorders MODIFY COLUMN poNumber VARCHAR(191);
ALTER TABLE vendors MODIFY COLUMN vendorName VARCHAR(191);
ALTER TABLE vendorbills MODIFY COLUMN vendorName VARCHAR(95), MODIFY COLUMN invoiceNumber VARCHAR(95);
ALTER TABLE pettycashentries MODIFY COLUMN voucherNumber VARCHAR(191);
ALTER TABLE sessions MODIFY COLUMN token VARCHAR(191);

SET @drop_sql = (SELECT IF(COUNT(*) > 0, 'DROP INDEX idx_properties_unit ON properties', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'properties' AND index_name = 'idx_properties_unit');
PREPARE drop_stmt FROM @drop_sql; EXECUTE drop_stmt; DEALLOCATE PREPARE drop_stmt;
SET @drop_sql = (SELECT IF(COUNT(*) > 0, 'DROP INDEX idx_employees_id ON employees', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'employees' AND index_name = 'idx_employees_id');
PREPARE drop_stmt FROM @drop_sql; EXECUTE drop_stmt; DEALLOCATE PREPARE drop_stmt;
SET @drop_sql = (SELECT IF(COUNT(*) > 0, 'DROP INDEX idx_po_number ON purchaseorders', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'purchaseorders' AND index_name = 'idx_po_number');
PREPARE drop_stmt FROM @drop_sql; EXECUTE drop_stmt; DEALLOCATE PREPARE drop_stmt;
SET @drop_sql = (SELECT IF(COUNT(*) > 0, 'DROP INDEX idx_vendors_name ON vendors', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'vendors' AND index_name = 'idx_vendors_name');
PREPARE drop_stmt FROM @drop_sql; EXECUTE drop_stmt; DEALLOCATE PREPARE drop_stmt;
SET @drop_sql = (SELECT IF(COUNT(*) > 0, 'DROP INDEX idx_vendorbills_vendor_inv ON vendorbills', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'vendorbills' AND index_name = 'idx_vendorbills_vendor_inv');
PREPARE drop_stmt FROM @drop_sql; EXECUTE drop_stmt; DEALLOCATE PREPARE drop_stmt;
SET @drop_sql = (SELECT IF(COUNT(*) > 0, 'DROP INDEX idx_pettycash_voucher ON pettycashentries', 'SELECT 1') FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'pettycashentries' AND index_name = 'idx_pettycash_voucher');
PREPARE drop_stmt FROM @drop_sql; EXECUTE drop_stmt; DEALLOCATE PREPARE drop_stmt;

CREATE UNIQUE INDEX idx_properties_unit ON properties(unit);
CREATE UNIQUE INDEX idx_employees_id ON employees(employeeId);
CREATE UNIQUE INDEX idx_po_number ON purchaseorders(poNumber);
CREATE UNIQUE INDEX idx_vendors_name ON vendors(vendorName);
CREATE UNIQUE INDEX idx_vendorbills_vendor_inv ON vendorbills(vendorName, invoiceNumber);
CREATE UNIQUE INDEX idx_pettycash_voucher ON pettycashentries(voucherNumber);
