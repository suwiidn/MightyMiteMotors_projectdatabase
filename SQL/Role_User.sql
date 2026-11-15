/* =====================================================================
   Section 2: Roles / Logins / Privileges / Views / Indexes
   Database: MightyMiteMotors (SQL Server)
   ตาราง: model, product, customer, orders, order_line, shipment
   รันด้วย SSMS ด้วยบัญชี sa (หรือ db_owner)
   ===================================================================== */

-- ---------------------------------------------------------------------
-- 2.2.1  สร้าง Login (ระดับ Server)  *** เปลี่ยนรหัสผ่านก่อนใช้งานจริง ***
-- ---------------------------------------------------------------------
USE master;
GO
IF SUSER_ID('login_dba')       IS NULL CREATE LOGIN login_dba       WITH PASSWORD = 'Dba@MMM_2026!',       CHECK_POLICY = OFF;
IF SUSER_ID('login_marketing') IS NULL CREATE LOGIN login_marketing WITH PASSWORD = 'Mkt@MMM_2026!',       CHECK_POLICY = OFF;
IF SUSER_ID('login_sales')     IS NULL CREATE LOGIN login_sales     WITH PASSWORD = 'Sales@MMM_2026!',     CHECK_POLICY = OFF;
IF SUSER_ID('login_shipping')  IS NULL CREATE LOGIN login_shipping  WITH PASSWORD = 'Ship@MMM_2026!',      CHECK_POLICY = OFF;
-- Login สำหรับ Application
IF SUSER_ID('app_catalog_web')  IS NULL CREATE LOGIN app_catalog_web  WITH PASSWORD = 'AppCat@MMM_2026!',   CHECK_POLICY = OFF;
IF SUSER_ID('app_order_system') IS NULL CREATE LOGIN app_order_system WITH PASSWORD = 'AppOrder@MMM_2026!', CHECK_POLICY = OFF;
GO

USE MightyMiteMotors;
GO

-- ---------------------------------------------------------------------
-- 2.2.2  สร้าง User ในฐานข้อมูล และ Role
-- ---------------------------------------------------------------------
CREATE USER login_dba        FOR LOGIN login_dba;
CREATE USER login_marketing  FOR LOGIN login_marketing;
CREATE USER login_sales      FOR LOGIN login_sales;
CREATE USER login_shipping   FOR LOGIN login_shipping;
CREATE USER app_catalog_web  FOR LOGIN app_catalog_web;
CREATE USER app_order_system FOR LOGIN app_order_system;
GO

CREATE ROLE role_marketing;       -- ฝ่ายการตลาด (T1, T7)
CREATE ROLE role_sales;           -- เซลส์ (T1-T4, T6, T8)
CREATE ROLE role_shipping;        -- ฝ่ายจัดส่ง (T5)
CREATE ROLE role_catalog_viewer;  -- Web แคตตาล็อกสำหรับลูกค้า (T1)
CREATE ROLE role_order_app;       -- ระบบรับออเดอร์ (Java/JDBC: T2-T6)
GO

ALTER ROLE db_owner            ADD MEMBER login_dba;        -- DB Admin: Create/Drop/Alter ได้ทั้งหมด
ALTER ROLE role_marketing      ADD MEMBER login_marketing;
ALTER ROLE role_sales          ADD MEMBER login_sales;
ALTER ROLE role_shipping       ADD MEMBER login_shipping;
ALTER ROLE role_catalog_viewer ADD MEMBER app_catalog_web;
ALTER ROLE role_order_app      ADD MEMBER app_order_system;
GO

-- ---------------------------------------------------------------------
-- 2.3  Views (ใช้ป้องกันการเข้าถึงตารางจริง)
-- ---------------------------------------------------------------------
-- (1) vw_catalog : Materialized View (Indexed View) แคตตาล็อกสำหรับลูกค้า ไม่เปิดคอลัมน์อื่นของ model
CREATE VIEW dbo.vw_catalog
WITH SCHEMABINDING
AS
SELECT model_numb, model_description, suggested_retail_price
FROM dbo.model;
GO
CREATE UNIQUE CLUSTERED INDEX uix_vw_catalog ON dbo.vw_catalog (model_numb);
GO

-- (2) vw_customer_order_summary : Materialized View สรุปยอดสั่งซื้อต่อลูกค้า (T8)
CREATE VIEW dbo.vw_customer_order_summary
WITH SCHEMABINDING
AS
SELECT customer_numb,
       COUNT_BIG(*)                AS order_count,
       SUM(ISNULL(order_total, 0)) AS total_amount
FROM dbo.orders
GROUP BY customer_numb;
GO
CREATE UNIQUE CLUSTERED INDEX uix_vw_customer_order_summary
    ON dbo.vw_customer_order_summary (customer_numb);
GO

-- (3) vw_order_shipping_status : สถานะการจัดส่ง (T5) ไม่แสดงข้อมูลลูกค้า/ราคา
CREATE VIEW dbo.vw_order_shipping_status
AS
SELECT o.order_numb, o.order_date, o.order_filled,
       ol.model_numb, ol.quantity_ordered, ol.all_shipped,
       (SELECT COUNT(*) FROM dbo.shipment s WHERE s.order_numb = o.order_numb) AS shipment_count
FROM dbo.orders o
JOIN dbo.order_line ol ON ol.order_numb = o.order_numb;
GO

-- ---------------------------------------------------------------------
-- 2.2.3  Grant / Deny สิทธิ์
-- ---------------------------------------------------------------------
-- role_marketing: ดูและแก้เฉพาะราคา/ระยะเวลาผลิตของ model (T1, T7)
GRANT SELECT ON dbo.model TO role_marketing;
GRANT UPDATE ON dbo.model (suggested_retail_price, time_to_manufacture) TO role_marketing;

-- role_catalog_viewer: ดูได้เฉพาะ view แคตตาล็อก (ไม่แตะตารางจริง)
GRANT SELECT ON dbo.vw_catalog TO role_catalog_viewer;

-- role_sales: รับออเดอร์ จองสินค้า ยกเลิกออเดอร์ ดูประวัติลูกค้า (T1-T4, T6, T8)
GRANT SELECT ON dbo.vw_catalog               TO role_sales;
GRANT SELECT ON dbo.model                    TO role_sales;
GRANT SELECT, INSERT ON dbo.customer         TO role_sales;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.orders     TO role_sales;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.order_line TO role_sales;
GRANT SELECT, UPDATE ON dbo.product          TO role_sales;
GRANT SELECT ON dbo.shipment                 TO role_sales;
GRANT SELECT ON dbo.vw_customer_order_summary TO role_sales;
DENY  DELETE ON dbo.customer                 TO role_sales;

-- role_shipping: ดูสถานะการจัดส่งเท่านั้น (T5)
GRANT SELECT ON dbo.vw_order_shipping_status TO role_shipping;
GRANT SELECT ON dbo.shipment                 TO role_shipping;

-- role_order_app: Application รับออเดอร์ (Java/JDBC) ไม่มีสิทธิ์ DDL
GRANT SELECT ON dbo.model                    TO role_order_app;
GRANT SELECT, INSERT ON dbo.customer         TO role_order_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.orders     TO role_order_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.order_line TO role_order_app;
GRANT SELECT, UPDATE ON dbo.product          TO role_order_app;
GRANT SELECT ON dbo.shipment                 TO role_order_app;
DENY  DELETE ON dbo.customer                 TO role_order_app;
DENY  ALTER  ON SCHEMA::dbo                  TO role_order_app;
GO

-- ---------------------------------------------------------------------
-- 2.4  Non-Clustered Index (เพิ่มประสิทธิภาพ Query ใน 1.4)
-- ---------------------------------------------------------------------
-- T1: ค้นหาแคตตาล็อกตามชื่อรุ่น (covering index)
CREATE NONCLUSTERED INDEX ix_model_description
    ON dbo.model (model_description) INCLUDE (suggested_retail_price);

-- T2, T6: นับสินค้าว่าง (order_numb IS NULL) / จอง / ปลดการจอง
CREATE NONCLUSTERED INDEX ix_product_order_numb
    ON dbo.product (order_numb);

-- T5, T6: เช็กว่า order มี shipment หรือยัง
CREATE NONCLUSTERED INDEX ix_shipment_order_numb
    ON dbo.shipment (order_numb);

-- T8: ประวัติการสั่งซื้อของลูกค้า
CREATE NONCLUSTERED INDEX ix_orders_customer_numb
    ON dbo.orders (customer_numb) INCLUDE (order_date, order_total);

-- T4, T6: join order_line กับ model
CREATE NONCLUSTERED INDEX ix_order_line_model_numb
    ON dbo.order_line (model_numb);

-- T3: ตรวจลูกค้าซ้ำตามชื่อ (แก้ชื่อคอลัมน์ให้ตรงกับตาราง customer แล้วเอา comment ออก)
-- CREATE NONCLUSTERED INDEX ix_customer_name ON dbo.customer (customer_name);
GO