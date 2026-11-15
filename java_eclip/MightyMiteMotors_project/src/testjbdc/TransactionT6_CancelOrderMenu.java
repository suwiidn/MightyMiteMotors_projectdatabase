package testjbdc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

/**
 * T6: ยกเลิกใบสั่งซื้อ (ไฟล์เดียว: เมนู Demo + method สำหรับให้ Test เรียกใช้)
 * Isolation Level = SERIALIZABLE (กัน Dirty Read / Non-Repeatable Read / Phantom Read)
 */
public class TransactionT6_CancelOrderMenu {

    public enum Result { SUCCESS, SHIPPED, NOT_FOUND }

    static final String SQL_ORDER_EXISTS = "SELECT COUNT(*) FROM orders WHERE order_numb = ?";
    static final String SQL_CHECK_SHIP   = "SELECT COUNT(*) FROM shipment WHERE order_numb = ?";
    static final String SQL_UNLOCK       = "UPDATE product SET order_numb = NULL WHERE order_numb = ?";
    static final String SQL_DELETE_LINE  = "DELETE FROM order_line WHERE order_numb = ?";
    static final String SQL_DELETE_ORDER = "DELETE FROM orders WHERE order_numb = ?";
    static final String SQL_DETAILS =
            "SELECT m.model_numb, m.model_description, ol.quantity_ordered, ol.line_total, o.order_total " +
            "FROM orders o " +
            "JOIN order_line ol ON o.order_numb = ol.order_numb " +
            "JOIN model m ON ol.model_numb = m.model_numb " +
            "WHERE o.order_numb = ?";

    // =====================================================================
    //  ส่วนเมนู Demo (เหมือนโค้ดเดิม: เช็ก shipment -> แสดงบิล -> เมนู 1/2/3)
    // =====================================================================
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in);
             Connection conn = DBConnection.getConnection()) {

            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

            showOrdersWithShipment(conn);   // <-- เพิ่ม: แสดงใบสั่งซื้อพร้อมสถานะการจัดส่ง

            System.out.print("กรุณากรอกหมายเลขใบสั่งซื้อ (Order Number) ที่ต้องการจัดการ: ");
            int orderNumb = scanner.nextInt();

            // ขั้นตอน 1: ตรวจสอบ shipment
            System.out.println("\nกำลังตรวจสอบสถานะการจัดส่งของ Order #" + orderNumb + "...");
            int shipCount = count(conn, SQL_CHECK_SHIP, orderNumb);
            if (shipCount > 0) {
                System.out.println("-> [ห้ามยกเลิก] ออเดอร์นี้มีการจัดส่งไปแล้ว (" + shipCount + " รายการ) ไม่สามารถยกเลิกได้!");
                conn.rollback();
                return;
            }
            System.out.println("-> [ผ่าน] ออเดอร์นี้ยังไม่มีการจัดส่ง (Shipment = 0) สามารถดำเนินการยกเลิกได้");

            // ขั้นตอน 2: แสดงรายการสินค้า
            try (PreparedStatement ps = conn.prepareStatement(SQL_DETAILS)) {
                ps.setInt(1, orderNumb);
                try (ResultSet rs = ps.executeQuery()) {
                    System.out.println("\n--- รายการสินค้าในใบสั่งซื้อ #" + orderNumb + " ---");
                    boolean hasItems = false;
                    double orderTotal = 0.0;
                    while (rs.next()) {
                        hasItems = true;
                        orderTotal = rs.getDouble("order_total");
                        System.out.printf("- รุ่น #%03d (%s) | จำนวน: %d ชิ้น | ราคารายการนี้: $%.2f%n",
                                rs.getInt("model_numb"), rs.getString("model_description"),
                                rs.getInt("quantity_ordered"), rs.getDouble("line_total"));
                    }
                    if (!hasItems) {
                        System.out.println("ไม่พบรายการสินค้าในใบสั่งซื้อ #" + orderNumb + " (หรือไม่มีบิลนี้ในระบบ)");
                        conn.rollback();
                        return;
                    }
                    System.out.printf(">> ยอดรวมสุทธิทั้งบิล (Order Total): $%.2f%n", orderTotal);
                }
            }

            // เมนู
            while (true) {
                System.out.println("\n=======================================================");
                System.out.println("   ระบบยกเลิกคำสั่งซื้อ (Order #" + orderNumb + ") - T6");
                System.out.println("=======================================================");
                System.out.println("1. ปลดล็อกสินค้าที่จองไว้ (Unlock Product)");
                System.out.println("2. ลบรายการสินค้าและลบบิลหลัก พร้อมยืนยัน (Delete Order Line, Orders & Commit)");
                System.out.println("3. Exit (ยกเลิกการทำงาน / Rollback และออก)");
                System.out.print("กรุณาเลือกเมนู (1-3): ");
                int choice = scanner.nextInt();

                try {
                    if (choice == 1) {
                        int rows = update(conn, SQL_UNLOCK, orderNumb);
                        System.out.println("-> [สำเร็จ] ปลดล็อกสินค้ากลับมาว่างแล้วจำนวน " + rows + " ชิ้น (รอ Commit)");

                    } else if (choice == 2) {
                        update(conn, SQL_UNLOCK, orderNumb); // กัน FK ติดถ้ายังไม่ได้กด 1
                        int rowsLine = update(conn, SQL_DELETE_LINE, orderNumb);
                        int rowsOrder = update(conn, SQL_DELETE_ORDER, orderNumb);

                        if (rowsOrder > 0) {
                            conn.commit();
                            System.out.println("-> [สำเร็จสมบูรณ์] ลบรายการสินค้า (" + rowsLine + " รายการ) และลบบิลหลัก #" + orderNumb + " เรียบร้อย (Committed)!");
                            break;
                        } else {
                            conn.rollback();
                            System.out.println("-> ไม่พบใบสั่งซื้อหมายเลข " + orderNumb + " ในระบบ ทำการ Rollback เรียบร้อย");
                        }

                    } else if (choice == 3) {
                        conn.rollback();
                        System.out.println("-> ยกเลิกการทำงานที่ค้างอยู่ (Rollback) และออกจากโปรแกรมเรียบร้อย");
                        break;

                    } else {
                        System.out.println("-> กรุณาเลือกตัวเลข 1 ถึง 3 เท่านั้น");
                    }
                } catch (SQLException e) {
                    conn.rollback();
                    System.err.println("-> [Error] ระบบ Rollback ข้อมูลทั้งหมดแล้ว: " + e.getMessage());
                }
            }

        } catch (SQLException e) {
            System.err.println("ไม่สามารถเชื่อมต่อฐานข้อมูลได้: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("ข้อมูลที่กรอกไม่ถูกต้อง กรุณากรอกเป็นตัวเลขเท่านั้น");
        }
    }

    // =====================================================================
    //  ส่วนแสดงรายการ (เพิ่มใหม่) ใช้ NOLOCK เพื่อไม่ไปล็อกแถว
    // =====================================================================

    /** แสดงใบสั่งซื้อพร้อมจำนวน shipment และสถานะว่ายกเลิกได้หรือไม่ */
    static void showOrdersWithShipment(Connection conn) throws SQLException {
        String sql = "SELECT o.order_numb, o.customer_numb, o.order_date, o.order_total, " +
                     "(SELECT COUNT(*) FROM shipment s WITH (NOLOCK) WHERE s.order_numb = o.order_numb) AS ship_cnt " +
                     "FROM orders o WITH (NOLOCK) ORDER BY o.order_numb";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            System.out.println("\n--- รายการใบสั่งซื้อ ---");
            System.out.printf("%-12s %-14s %-14s %-12s %-10s %-15s%n",
                    "เลขออเดอร์", "รหัสลูกค้า", "วันที่สั่ง", "ยอดรวม", "จัดส่ง", "สถานะ");
            System.out.println("-".repeat(80));
            while (rs.next()) {
                int ship = rs.getInt("ship_cnt");
                System.out.printf("%-12d %-14d %-14s $%-11.2f %-10d %-15s%n",
                        rs.getInt("order_numb"), rs.getInt("customer_numb"),
                        rs.getDate("order_date"), rs.getDouble("order_total"),
                        ship, ship > 0 ? "ส่งแล้ว (ห้ามยกเลิก)" : "ยกเลิกได้");
            }
        }
    }

    // =====================================================================
    //  ส่วนที่ให้ CancelOrderTest เรียกใช้ (ไม่มี Scanner)
    // =====================================================================

    /** ทำ T6 ครบทุกขั้นตอน commit=false -> ทำแล้ว rollback (ไม่ทำลายข้อมูลจริง) */
    public static Result cancelOrder(Connection conn, int orderNumb, int isolation, boolean commit)
            throws SQLException {
        boolean oldAuto = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(isolation);

            if (count(conn, SQL_ORDER_EXISTS, orderNumb) == 0) {
                conn.rollback();
                return Result.NOT_FOUND;
            }
            if (count(conn, SQL_CHECK_SHIP, orderNumb) > 0) {
                conn.rollback();
                return Result.SHIPPED;
            }
            update(conn, SQL_UNLOCK, orderNumb);
            update(conn, SQL_DELETE_LINE, orderNumb);
            update(conn, SQL_DELETE_ORDER, orderNumb);

            if (commit) conn.commit(); else conn.rollback();
            return Result.SUCCESS;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAuto);
        }
    }

    public static int countOrder(Connection conn, int orderNumb) throws SQLException {
        return count(conn, SQL_ORDER_EXISTS, orderNumb);
    }

    public static int countOrderLines(Connection conn, int orderNumb) throws SQLException {
        return count(conn, "SELECT COUNT(*) FROM order_line WHERE order_numb = ?", orderNumb);
    }

    static int count(Connection conn, String sql, int orderNumb) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderNumb);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    static int update(Connection conn, String sql, int orderNumb) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderNumb);
            return ps.executeUpdate();
        }
    }
}