ระบบฐานข้อมูล Might-Mite-Motors รองรับงานขายรถของเล่นแบบสั่งซื้อ โดยแบ่งเป็น 3 Requirements หลัก ครอบคลุมตั้งแต่การเลือกสินค้า การรับออเดอร์ ไปจนถึงการติดตามและยกเลิกออเดอร์ ตารางที่เกี่ยวข้อง ได้แก่ model, product, customer, orders, order_line และ shipment 

Requirement
ด้าน
1
ค้นหาและปรับปรุง Catalog สินค้า (Product Catalog): ลูกค้าและเซลส์ค้นหารหัสรุ่น ชื่อรุ่น และราคาแนะนำก่อนสั่งซื้อ และฝ่ายการตลาดอัปเดตราคาหรือระยะเวลาการผลิตให้เป็นปัจจุบัน 
Marketing & Sales 
2
ลงทะเบียนลูกค้าและรับออเดอร์ (Order Entry): ตรวจสอบ/ลงทะเบียนลูกค้า สร้างใบสั่งซื้อและรายการสินค้า ตรวจสอบสินค้าคงเหลือและจองสินค้า 
Marketing & Sales 
3
ติดตามสถานะ ประวัติ และยกเลิกออเดอร์: ตรวจสอบสถานะการจัดส่ง ดูประวัติและยอดรวมของลูกค้า และยกเลิกออเดอร์ที่ยังไม่ส่ง 
Sales + Shipping  



Transactions อย่างน้อย คนละ 2 ข้อ
สมาชิก
Transaction 1
Transaction 2
ning
T1 ค้นหาและแสดงแคตตาล็อกสินค้า (SELECT model_numb, model_description, suggested_retail_price FROM model) 
T2 ตรวจสอบสินค้าคงเหลือและจองสินค้าให้ order (SELECT COUNT product WHERE order_numb IS NULL แล้ว UPDATE product SET order_numb = ? ภายใน Transaction เดียว) 
     JN
T3 ลงทะเบียนลูกค้าใหม่ (SELECT เช็กว่ามี customer ซ้ำหรือไม่ แล้ว INSERT customer ถ้ายังไม่มี) 
T4 สร้างและบันทึกคำสั่งซื้อใหม่ (INSERT orders + INSERT order_line + UPDATE order_total) 
dan
T5 ตรวจสอบสถานะการจัดส่ง (SELECT JOIN orders, order_line, shipments) 
T6 ยกเลิกคำสั่งซื้อที่ยังไม่ได้จัดส่ง (SELECT เช็ก shipments → UPDATE product SET order_numb = NULL → DELETE order_line → DELETE orders) 
Q
T7 ปรับปรุงราคาในแคตตาล็อก (UPDATE model.suggested_retail_price หรือ time_to_manufacture) 
T8 เรียกดูประวัติการสั่งซื้อของลูกค้า (SELECT JOIN customer กับ orders เพื่อดูยอดรวม) 

