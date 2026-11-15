package testjbdc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

/**
 * T2: ตรวจสอบสินค้าคงเหลือและจองสินค้าให้ order (ning)
 * ขั้นตอน: เช็ก order / model -> นับสินค้าว่าง (order_numb IS NULL) -> UPDATE product SET order_numb = ?
 *
 * Isolation Level = SERIALIZABLE + UPDLOCK ตอนนับสต็อก
 * เหตุผล: ถ้าสอง Transaction นับพร้อมกันได้ว่า "เหลือ 1 ชิ้น" แล้วต่างคนต่างจอง จะเกิดการจองเกิน (Phantom Read / Lost Update)
 * UPDLOCK + SERIALIZABLE ทำให้ Transaction ที่สองต้องรอจนอันแรก commit/rollback
 */
public class T2_ReserveProduct {

    public enum Result { SUCCESS, NOT_ENOUGH_STOCK, ORDER_NOT_FOUND, MODEL_NOT_FOUND, INVALID_QTY }

    // ---------- SQL ของ T2 ----------
    // 1) นับสินค้าว่างของรุ่นนั้น (ล็อกแถวไว้กันคนอื่นจองซ้ำ)
    static final String COUNT_AVAILABLE =
            "SELECT COUNT(*) FROM product WITH (UPDLOCK) WHERE model_numb = ? AND order_numb IS NULL";
    // 2) จองสินค้าตามจำนวนที่ต้องการ
    static final String RESERVE =
            "UPDATE TOP (?) product SET order_numb = ? WHERE model_numb = ? AND order_numb IS NULL";

    // ใช้ตอนทดสอบ (ไม่ล็อก)
    static final String COUNT_AVAILABLE_PLAIN =
            "SELECT COUNT(*) FROM product WHERE model_numb = ? AND order_numb IS NULL";

    static final String CHECK_ORDER = "SELECT COUNT(*) FROM orders WHERE order_numb = ?";
    static final String CHECK_MODEL = "SELECT COUNT(*) FROM model WHERE model_numb = ?";

    // =====================================================================
    //  ส่วนเมนู Demo
    // =====================================================================
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in);
             Connection conn = DBConnection.getConnection()) {

            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

            System.out.print("กรุณากรอกหมายเลขใบสั่งซื้อ (Order Number): ");
            int orderNumb = scanner.nextInt();
            System.out.print("กรุณากรอกรหัสรุ่นสินค้า (Model Number): ");
            int modelNumb = scanner.nextInt();
            System.out.print("กรุณากรอกจำนวนที่ต้องการจอง (ชิ้น): ");
            int qty = scanner.nextInt();

            if (qty <= 0) {
                System.out.println("-> จำนวนต้องมากกว่า 0");
                return;
            }
            if (count(conn, CHECK_ORDER, orderNumb) == 0) {
                System.out.println("-> ไม่พบใบสั่งซื้อหมายเลข " + orderNumb);
                conn.rollback();
                return;
            }
            if (count(conn, CHECK_MODEL, modelNumb) == 0) {
                System.out.println("-> ไม่พบรุ่นสินค้าหมายเลข " + modelNumb);
                conn.rollback();
                return;
            }

            int available = count(conn, COUNT_AVAILABLE, modelNumb);
            System.out.println("\n-> สินค้ารุ่น #" + modelNumb + " ที่พร้อมจอง (ว่าง): " + available + " ชิ้น");

            boolean reserved = false;
            while (true) {
                System.out.println("\n=======================================================");
                System.out.println("   ระบบจองสินค้าให้ Order #" + orderNumb + " - T2");
                System.out.println("=======================================================");
                System.out.println("1. จองสินค้า " + qty + " ชิ้น (UPDATE product SET order_numb)");
                System.out.println("2. ยืนยันบันทึก (Commit และออก)");
                System.out.println("3. Exit (ยกเลิกการทำงาน / Rollback และออก)");
                System.out.print("กรุณาเลือกเมนู (1-3): ");
                int choice = scanner.nextInt();

                try {
                    if (choice == 1) {
                        if (reserved) {
                            System.out.println("-> จองไปแล้ว กรุณา Commit (เมนู 2)");
                        } else if (available < qty) {
                            System.out.println("-> [ไม่พอ] สินค้าว่างมีเพียง " + available + " ชิ้น ไม่สามารถจอง " + qty + " ชิ้นได้");
                        } else {
                            int rows = reserveRows(conn, qty, orderNumb, modelNumb);
                            if (rows == qty) {
                                reserved = true;
                                System.out.println("-> [สำเร็จ] จองสินค้า " + rows + " ชิ้นให้ Order #" + orderNumb + " (รอ Commit)");
                            } else {
                                conn.rollback();
                                System.out.println("-> จองได้ไม่ครบ (" + rows + "/" + qty + ") Rollback แล้ว");
                                break;
                            }
                        }

                    } else if (choice == 2) {
                        if (!reserved) {
                            System.out.println("-> ยังไม่ได้จองสินค้า (เมนู 1)");
                        } else {
                            conn.commit();
                            System.out.println("-> [สำเร็จสมบูรณ์] จองสินค้า " + qty + " ชิ้นให้ Order #" + orderNumb + " เรียบร้อย (Committed)!");
                            break;
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
                    reserved = false;
                    System.err.println("-> [Error] Rollback ข้อมูลทั้งหมดแล้ว: " + e.getMessage());
                    break;
                }
            }

        } catch (SQLException e) {
            System.err.println("ไม่สามารถเชื่อมต่อ/ทำงานกับฐานข้อมูลได้: " + e.getMessage());
        } catch (java.util.InputMismatchException e) {
            System.err.println("ข้อมูลที่กรอกไม่ถูกต้อง กรุณากรอกเป็นตัวเลขเท่านั้น");
        }
    }

    // =====================================================================
    //  ส่วนที่ให้ T2_ReserveProductTest เรียกใช้ (ไม่มี Scanner)
    //  commit=false -> ทำครบทุกขั้นตอนแล้ว rollback (ไม่เปลี่ยนข้อมูลจริง)
    // =====================================================================
    public static Result reserve(Connection conn, int orderNumb, int modelNumb, int qty,
                                 int isolation, boolean commit) throws SQLException {
        boolean oldAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(isolation);

            if (qty <= 0) {
                conn.rollback();
                return Result.INVALID_QTY;
            }
            if (count(conn, CHECK_ORDER, orderNumb) == 0) {
                conn.rollback();
                return Result.ORDER_NOT_FOUND;
            }
            if (count(conn, CHECK_MODEL, modelNumb) == 0) {
                conn.rollback();
                return Result.MODEL_NOT_FOUND;
            }
            if (count(conn, COUNT_AVAILABLE, modelNumb) < qty) {
                conn.rollback();
                return Result.NOT_ENOUGH_STOCK;
            }
            int rows = reserveRows(conn, qty, orderNumb, modelNumb);
            if (rows != qty) {
                conn.rollback();
                return Result.NOT_ENOUGH_STOCK;
            }

            if (commit) conn.commit(); else conn.rollback();
            return Result.SUCCESS;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAutoCommit);
        }
    }

    static int reserveRows(Connection conn, int qty, int orderNumb, int modelNumb) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(RESERVE)) {
            ps.setInt(1, qty);
            ps.setInt(2, orderNumb);
            ps.setInt(3, modelNumb);
            return ps.executeUpdate();
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