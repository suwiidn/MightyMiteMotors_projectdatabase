/* =====================================================================
   Might-Mite Motors (MMM) - CREATE TABLE + INSERT  (SQL Server / SSMS)
   ===================================================================== */

USE MightyMiteMotors;
GO

/* =====================================================================
   1) CREATE TABLE
   หมายเหตุการปรับจากโค้ดในหนังสือ (ให้ใช้ได้กับ SQL Server):
   - NUMBER -> DECIMAL, BOOLEAN -> BIT
   - ตาราง "order" เป็น reserved word -> ใช้ชื่อ orders (ตรงกับ ER Diagram v4)
   - time_to_manufacture: TIME เก็บได้ไม่เกิน 24 ชม. จึงเปลี่ยนเป็น INT (หน่วย: วัน)
   ===================================================================== */

CREATE TABLE model (
    model_numb            INT            NOT NULL,
    model_description     VARCHAR(40)    NOT NULL,
    suggested_retail_price DECIMAL(6,2)  NOT NULL,
    shipping_weight       DECIMAL(6,2)   NULL,
    time_to_manufacture   INT            NULL,      -- วัน
    CONSTRAINT PK_model PRIMARY KEY (model_numb),
    CONSTRAINT CK_model_price  CHECK (suggested_retail_price >= 0),
    CONSTRAINT CK_model_weight CHECK (shipping_weight IS NULL OR shipping_weight >= 0),
    CONSTRAINT CK_model_ttm    CHECK (time_to_manufacture IS NULL OR time_to_manufacture >= 0)
);

CREATE TABLE customer (
    customer_numb   INT          NOT NULL,
    customer_name   VARCHAR(40)  NOT NULL,
    customer_street VARCHAR(50)  NULL,
    customer_city   VARCHAR(50)  NULL,
    customer_state  CHAR(2)      NULL,
    customer_zip    CHAR(10)     NULL,
    contact_person  VARCHAR(30)  NULL,
    contact_phone   CHAR(12)     NULL,
    contact_fax     CHAR(12)     NULL,
    CONSTRAINT PK_customer PRIMARY KEY (customer_numb)
);

CREATE TABLE orders (
    order_numb    INT           NOT NULL,
    customer_numb INT           NOT NULL,
    order_date    DATE          NOT NULL,
    order_total   DECIMAL(8,2)  NOT NULL DEFAULT 0,   -- ผลรวม line_total (คำนวณใน T4)
    order_filled  BIT           NOT NULL DEFAULT 0,
    CONSTRAINT PK_orders PRIMARY KEY (order_numb),
    CONSTRAINT FK_orders_customer FOREIGN KEY (customer_numb) REFERENCES customer(customer_numb),
    CONSTRAINT CK_orders_total CHECK (order_total >= 0)
);

-- Weak entity: PK = (order_numb, model_numb)
CREATE TABLE order_line (
    order_numb       INT           NOT NULL,
    model_numb       INT           NOT NULL,
    quantity_ordered INT           NOT NULL,
    unit_price       DECIMAL(6,2)  NOT NULL,
    line_total       DECIMAL(8,2)  NOT NULL,          -- = quantity_ordered * unit_price
    all_shipped      BIT           NOT NULL DEFAULT 0,
    CONSTRAINT PK_order_line PRIMARY KEY (order_numb, model_numb),
    CONSTRAINT FK_ol_orders FOREIGN KEY (order_numb) REFERENCES orders(order_numb),
    CONSTRAINT FK_ol_model  FOREIGN KEY (model_numb) REFERENCES model(model_numb),
    CONSTRAINT CK_ol_qty    CHECK (quantity_ordered > 0),
    CONSTRAINT CK_ol_price  CHECK (unit_price >= 0),
    CONSTRAINT CK_ol_total  CHECK (line_total = quantity_ordered * unit_price)
);

-- Weak entity: PK = (order_numb, model_numb, shipping_date)  รองรับการส่งสินค้าบางส่วน (partial shipment)
CREATE TABLE shipment (
    order_numb       INT   NOT NULL,
    model_numb       INT   NOT NULL,
    shipping_date    DATE  NOT NULL,
    quantity_shipped INT   NOT NULL,
    CONSTRAINT PK_shipment PRIMARY KEY (order_numb, model_numb, shipping_date),
    CONSTRAINT FK_shp_order_line FOREIGN KEY (order_numb, model_numb)
        REFERENCES order_line(order_numb, model_numb),
    CONSTRAINT CK_shp_qty CHECK (quantity_shipped > 0)
);

-- 1 แถว = รถ 1 คัน (มี serial number)
-- order_numb IS NULL และ date_shipped IS NULL  = พร้อมขาย (ยังไม่ถูกจอง)
-- order_numb NOT NULL และ date_shipped IS NULL = ถูกจองให้ order แล้ว (RESERVED_FOR)
-- date_shipped NOT NULL                        = ส่งแล้ว
CREATE TABLE product (
    serial_numb       INT   NOT NULL,
    model_numb        INT   NOT NULL,
    date_manufactured DATE  NOT NULL,
    date_shipped      DATE  NULL,
    order_numb        INT   NULL,
    CONSTRAINT PK_product PRIMARY KEY (serial_numb),
    CONSTRAINT FK_product_model  FOREIGN KEY (model_numb) REFERENCES model(model_numb),
    CONSTRAINT FK_product_orders FOREIGN KEY (order_numb) REFERENCES orders(order_numb),
    CONSTRAINT CK_product_ship_dates CHECK (date_shipped IS NULL OR date_shipped >= date_manufactured),
    CONSTRAINT CK_product_ship_order CHECK (date_shipped IS NULL OR order_numb IS NOT NULL)
);
GO

/* =====================================================================
   2) INSERT
   ===================================================================== */

/* ---- model: จาก Product Catalog (Figure 13.1) ---- */
INSERT INTO model (model_numb, model_description, suggested_retail_price) VALUES
(1,  'All Terrain Vehicle',        124.95),
(2,  '4-Wheel Drive Cruiser',      249.99),
(3,  'Classic Roadster',           189.95),
(4,  'Sports Car #1',              249.95),
(5,  'Sports Car #2',              249.95),
(6,  'Turbo-Injected Porsche',     299.95),
(7,  'Indy Car',                   269.95),
(8,  '2-Ton Pickup',               189.95),
(9,  'Santa Fe Train',             159.95),
(10, 'Oval Track',                  39.95),
(11, '6 Pieces Straight Track',     19.95),
(12, 'Rechargeable Battery (6v)',   27.95);

/* ---- customer: ร้านค้าของเล่น/catalog house (ข้อมูลตัวอย่าง) ---- */
INSERT INTO customer (customer_numb, customer_name, customer_street, customer_city, customer_state,
                      customer_zip, contact_person, contact_phone, contact_fax) VALUES
(1001, 'Toy World',          '120 Main St',        'Columbus',    'OH', '43215',      'Linda Carter', '614-555-0101', '614-555-0102'),
(1002, 'Kids Kingdom',       '45 Market Ave',      'Pittsburgh',  'PA', '15222',      'Mark Evans',   '412-555-0133', '412-555-0134'),
(1003, 'Happy Wheels Toys',  '9 Lakeshore Dr',     'Chicago',     'IL', '60601',      'Sara Nguyen',  '312-555-0177', NULL),
(1004, 'Sunrise Catalog Co.','800 Commerce Blvd',  'Dallas',      'TX', '75201',      'Tom Baker',    '214-555-0190', '214-555-0191');

/* ---- orders (ยอดรวม = ผลรวม line_total ของแต่ละ order) ----
   100001: ส่งครบแล้ว            100002: ส่งบางส่วน (partial shipment)
   100003: ยังไม่ส่ง (ใช้ทดสอบ T6 ยกเลิก)   100004: สินค้าไม่พอ จองได้บางส่วน (ใช้ทดสอบ T2) */
INSERT INTO orders (order_numb, customer_numb, order_date, order_total, order_filled) VALUES
(100001, 1001, '2026-11-02', 2199.25, 1),
(100002, 1002, '2026-11-05', 6899.30, 0),
(100003, 1003, '2026-11-10', 1099.25, 0),
(100004, 1001, '2026-11-12', 2999.50, 0);

/* ---- order_line (unit_price = suggested_retail_price ณ วันสั่ง) ---- */
INSERT INTO order_line (order_numb, model_numb, quantity_ordered, unit_price, line_total, all_shipped) VALUES
(100001, 1,  10, 124.95, 1249.50, 1),
(100001, 3,   5, 189.95,  949.75, 1),
(100002, 2,  20, 249.99, 4999.80, 0),   -- ส่งแล้ว 8 จาก 20
(100002, 8,  10, 189.95, 1899.50, 0),
(100003, 9,   5, 159.95,  799.75, 0),
(100003, 10,  5,  39.95,  199.75, 0),
(100003, 11,  5,  19.95,   99.75, 0),
(100004, 6,  10, 299.95, 2999.50, 0);   -- มีในสต็อกแค่ 5 คัน

/* ---- shipment ---- */
INSERT INTO shipment (order_numb, model_numb, shipping_date, quantity_shipped) VALUES
(100001, 1, '2026-11-05', 10),
(100001, 3, '2026-11-05',  5),
(100002, 2, '2026-11-08',  8);

/* ---- product ----
  
;WITH nums AS (
    SELECT TOP (400) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS seq
    FROM sys.all_objects a CROSS JOIN sys.all_objects b
),
stock (model_numb, total_units) AS (
    SELECT * FROM (VALUES
        (1, 215), (2,  35), (3, 180),
        (4, 312), (5,  82), (6,   5), (7, 212), (8, 189),
        (9,  37), (10, 111), (11, 195), (12, 22)
    ) v(model_numb, total_units)
)
INSERT INTO product (serial_numb, model_numb, date_manufactured, date_shipped, order_numb)
SELECT s.model_numb * 10000 + n.seq,
       s.model_numb,
       DATEADD(DAY, -(n.seq % 45), '2026-10-31'),
       NULL,
       NULL
FROM stock s
JOIN nums  n ON n.seq <= s.total_units;

GO