package testjbdc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Scanner;

/**
 * T4: สร้างคำสั่งซื้อใหม่ (Create New Order)
 * ไฟล์เดียว: เมนู Demo (เหมือน T6) + method createOrder(...) ให้ T4_CreateOrderTest เรียกใช้
 * Isolation Level = SERIALIZABLE (กัน Phantom Read ตอน SUM(line_total), Dirty Read, Non-Repeatable Read)
 */
public class TransactionT4_CreateOrder {

    // ---------- SQL เดิม (ไม่แก้) ----------
    // T4.1: สร้างหัวบิลในตาราง orders (ตั้งยอดรวม order_total เป็น 0 ไว้ก่อน)
    static final String INSERT_ORDER = "INSERT INTO orders (order_numb, customer_numb, order_date, order_total, order_filled) VALUES (?, ?, GETDATE(), 0, 0)";
    // T4.2: เพิ่มรายการสินค้าลงในตาราง order_line
    static final String INSERT_LINE = "INSERT INTO order_line (order_numb, model_numb, quantity_ordered, unit_price, line_total, all_shipped) VALUES (?, ?, ?, ?, ?, 0)";
    // T4.3: คำนวณผลรวมจาก line_total แล้วอัปเดตกลับไปที่ order_total ในตาราง orders
    static final String UPDATE_TOTAL = "UPDATE orders SET order_total = (SELECT SUM(line_total) FROM order_line WHERE order_numb = ?) WHERE order_numb = ?";

    // ---------- SQL ตรวจสอบก่อนบันทึก ----------
    static final String CHECK_CUSTOMER = "SELECT COUNT(*) FROM customer WHERE customer_numb = ?";
    static final String CHECK_MODEL    = "SELECT COUNT(*) FROM model WHERE model_numb = ?";
    static final String CHECK_ORDER    = "SELECT COUNT(*) FROM orders WHERE order_numb = ?";

    // =====================================================================
    //  ส่วนเมนู Demo
    // =====================================================================
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in);
             Connection conn = DBConnection.getConnection()) {

            scanner.useLocale(Locale.US);
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            System.out.println("Connected to database successfully. Running Transaction T4...");

            System.out.print("กรุณากรอกหมายเลขใบสั่งซื้อใหม่ (Order Number): ");
            int orderNumb = scanner.nextInt();
            System.out.print("กรุณากรอกรหัสลูกค้า (Customer Number): ");
            int customerNumb = scanner.nextInt();

            // ตรวจสอบอัตโนมัติ: ลูกค้าต้องมีอยู่จริง และเลข Order ต้องไม่ซ้ำ
            if (count(conn, CHECK_CUSTOMER, customerNumb) == 0) {
                System.out.println("-> [ไม่ผ่าน] ไม่พบลูกค้าหมายเลข " + customerNumb + " ในระบบ");
                conn.rollback();
                return;
            }
            if (count(conn, CHECK_ORDER, orderNumb) > 0) {
                System.out.println("-> [ไม่ผ่าน] มีใบสั่งซื้อหมายเลข " + orderNumb + " อยู่แล้ว");
                conn.rollback();
                return;
            }
            System.out.println("-> [ผ่าน] ลูกค้ามีอยู่จริงและเลข Order ใช้ได้");

            boolean headerCreated = false;
            int lineCount = 0;

            while (true) {
                System.out.println("\n=======================================================");
                System.out.println("   ระบบสร้างคำสั่งซื้อ (Order #" + orderNumb + ") - T4");
                System.out.println("=======================================================");
                System.out.println("1. สร้างหัวบิล (T4.1 Insert orders)");
                System.out.println("2. เพิ่มรายการสินค้า (T4.2 Insert order_line)");
                System.out.println("3. คำนวณยอดรวมและอัปเดต order_total (T4.3)");
                System.out.println("4. ยืนยันบันทึก (Commit และออก)");
                System.out.println("5. Exit (ยกเลิกการทำงาน / Rollback และออก)");
                System.out.print("กรุณาเลือกเมนู (1-5): ");
                int choice = scanner.nextInt();

                try {
                    if (choice == 1) {
                        if (headerCreated) {
                            System.out.println("-> สร้างหัวบิลไปแล้ว");
                        } else {
                            insertOrderHeader(conn, orderNumb, customerNumb);
                            headerCreated = true;
                            System.out.println("-> [สำเร็จ] สร้างหัวบิล #" + orderNumb + " แล้ว (รอ Commit)");
                        }

                    } else if (choice == 2) {
                        if (!headerCreated) {
                            System.out.println("-> กรุณาสร้างหัวบิล (เมนู 1) ก่อน");
                            continue;
                        }
                        System.out.print("รหัสรุ่นสินค้า (Model Number): ");
                        int modelNumb = scanner.nextInt();
                        if (count(conn, CHECK_MODEL, modelNumb) == 0) {
                            System.out.println("-> ไม่พบรุ่นสินค้าหมายเลข " + modelNumb);
                            continue;
                        }
                        System.out.print("จำนวน (ชิ้น): ");
                        int qty = scanner.nextInt();
                        System.out.print("ราคาต่อชิ้น: ");
                        double price = scanner.nextDouble();
                        if (qty <= 0 || price < 0) {
                            System.out.println("-> จำนวนต้องมากกว่า 0 และราคาต้องไม่ติดลบ");
                            continue;
                        }
                        insertLine(conn, orderNumb, modelNumb, qty, price);
                        lineCount++;
                        System.out.printf("-> [สำเร็จ] เพิ่มรุ่น #%d จำนวน %d ชิ้น รวม $%.2f (รอ Commit)%n",
                                modelNumb, qty, qty * price);

                    } else if (choice == 3) {
                        if (!headerCreated || lineCount == 0) {
                            System.out.println("-> ต้องมีหัวบิลและรายการสินค้าอย่างน้อย 1 รายการก่อน");
                            continue;
                        }
                        updateTotal(conn, orderNumb);
                        System.out.println("-> [สำเร็จ] อัปเดต order_total แล้ว (รอ Commit)");

                    } else if (choice == 4) {
                        if (!headerCreated || lineCount == 0) {
                            System.out.println("-> ต้องมีหัวบิลและรายการสินค้าอย่างน้อย 1 รายการก่อน");
                            continue;
                        }
                        updateTotal(conn, orderNumb); // กันลืมกดข้อ 3
                        conn.commit();
                        System.out.println("Success! Transaction T4 completed and committed successfully.");
                        break;

                    } else if (choice == 5) {
                        conn.rollback();
                        System.out.println("-> ยกเลิกการทำงานที่ค้างอยู่ (Rollback) และออกจากโปรแกรมเรียบร้อย");
                        break;

                    } else {
                        System.out.println("-> กรุณาเลือกตัวเลข 1 ถึง 5 เท่านั้น");
                    }
                } catch (SQLException e) {
                    conn.rollback();
                    headerCreated = false;
                    lineCount = 0;
                    System.err.println("-> [Error] Rollback ข้อมูลทั้งหมดแล้ว เริ่มใหม่จากเมนู 1: " + e.getMessage());
                }
            }

        } catch (SQLException e) {
            System.err.println("Error occurred: " + e.getMessage());
        } catch (java.util.InputMismatchException e) {
            System.err.println("ข้อมูลที่กรอกไม่ถูกต้อง กรุณากรอกเป็นตัวเลขเท่านั้น");
        }
    }

    // =====================================================================
    //  ส่วนที่ให้ T4_CreateOrderTest เรียกใช้ (ไม่มี Scanner)
    //  ค่าเดิม: Model 1 จำนวน 2 ชิ้น ชิ้นละ 124.95
    //  commit=false -> ทำครบทุกขั้นตอนแล้ว rollback
    // =====================================================================
    public static void createOrder(Connection conn, int orderNumb, int customerNumb,
                                   int isolation, boolean commit) throws SQLException {
        boolean oldAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(isolation);

            insertOrderHeader(conn, orderNumb, customerNumb);
            insertLine(conn, orderNumb, 1, 2, 124.95);
            updateTotal(conn, orderNumb);

            if (commit) conn.commit(); else conn.rollback();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAutoCommit);
        }
    }

    // ---------- ขั้นตอนย่อย (ใช้ SQL เดิม) ----------
    static void insertOrderHeader(Connection conn, int orderNumb, int customerNumb) throws SQLException {
        try (PreparedStatement psOrder = conn.prepareStatement(INSERT_ORDER)) {
            psOrder.setInt(1, orderNumb);
            psOrder.setInt(2, customerNumb);
            psOrder.executeUpdate();
        }
    }

    static void insertLine(Connection conn, int orderNumb, int modelNumb, int qty, double price) throws SQLException {
        try (PreparedStatement psLine = conn.prepareStatement(INSERT_LINE)) {
            psLine.setInt(1, orderNumb);
            psLine.setInt(2, modelNumb);
            psLine.setInt(3, qty);
            psLine.setDouble(4, price);
            psLine.setDouble(5, qty * price); // คำนวณ line_total
            psLine.executeUpdate();
        }
    }

    static void updateTotal(Connection conn, int orderNumb) throws SQLException {
        try (PreparedStatement psUpdate = conn.prepareStatement(UPDATE_TOTAL)) {
            psUpdate.setInt(1, orderNumb);
            psUpdate.setInt(2, orderNumb);
            psUpdate.executeUpdate();
        }
    }

    static int count(Connection conn, String sql, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}