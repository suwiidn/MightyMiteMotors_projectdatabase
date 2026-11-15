package testjbdc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Locale;
import java.util.Scanner;

/**
 * T7: ปรับปรุงราคาในแคตตาล็อก (Q)
 * ขั้นตอน: SELECT ค่าปัจจุบัน (UPDLOCK) -> UPDATE suggested_retail_price / time_to_manufacture -> commit
 *
 * Isolation Level = REPEATABLE_READ + UPDLOCK
 * เหตุผล: ค่าเดิมที่ฝ่ายการตลาดเห็นบนหน้าจอ ต้องเป็นค่าเดียวกับที่ถูกเขียนทับ ไม่มีใครแก้ระหว่างนั้น
 * (กัน Non-Repeatable Read / Lost Update และ Dirty Read)
 */
public class TransactionT7_UpdateModelPrice {

    public enum Result { SUCCESS, MODEL_NOT_FOUND, INVALID_VALUE }

    // ---------- SQL ของ T7 ----------
    // 1) อ่านค่าปัจจุบันพร้อมล็อกแถว
    static final String SELECT_MODEL =
            "SELECT suggested_retail_price, time_to_manufacture FROM model WITH (UPDLOCK) WHERE model_numb = ?";
    // 2) แก้ไขเฉพาะค่าที่ส่งมา (NULL = คงค่าเดิม)
    static final String UPDATE_MODEL =
            "UPDATE model SET suggested_retail_price = ISNULL(?, suggested_retail_price), " +
            "time_to_manufacture = ISNULL(?, time_to_manufacture) WHERE model_numb = ?";

    // ใช้ตอนทดสอบ (ไม่ล็อก)
    static final String SELECT_MODEL_PLAIN =
            "SELECT suggested_retail_price, time_to_manufacture FROM model WHERE model_numb = ?";

    // =====================================================================
    //  ส่วนเมนู Demo
    // =====================================================================
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in);
             Connection conn = DBConnection.getConnection()) {

            scanner.useLocale(Locale.US);
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

            System.out.print("กรุณากรอกรหัสรุ่นสินค้า (Model Number) ที่ต้องการแก้ไข: ");
            int modelNumb = scanner.nextInt();

            double[] oldValues = readModel(conn, SELECT_MODEL, modelNumb);
            if (oldValues == null) {
                System.out.println("-> ไม่พบรุ่นสินค้าหมายเลข " + modelNumb);
                conn.rollback();
                return;
            }
            System.out.printf("%n--- ข้อมูลปัจจุบันของรุ่น #%d ---%n", modelNumb);
            System.out.printf("ราคาแนะนำ: $%.2f | ระยะเวลาผลิต: %d วัน%n", oldValues[0], (int) oldValues[1]);

            boolean changed = false;
            while (true) {
                System.out.println("\n=======================================================");
                System.out.println("   ปรับปรุงแคตตาล็อก (Model #" + modelNumb + ") - T7");
                System.out.println("=======================================================");
                System.out.println("1. แก้ราคาแนะนำ (suggested_retail_price)");
                System.out.println("2. แก้ระยะเวลาการผลิต (time_to_manufacture)");
                System.out.println("3. ยืนยันบันทึก (Commit และออก)");
                System.out.println("4. Exit (ยกเลิกการทำงาน / Rollback และออก)");
                System.out.print("กรุณาเลือกเมนู (1-4): ");
                int choice = scanner.nextInt();

                try {
                    if (choice == 1) {
                        System.out.print("ราคาใหม่: ");
                        double price = scanner.nextDouble();
                        if (price <= 0) {
                            System.out.println("-> ราคาต้องมากกว่า 0");
                            continue;
                        }
                        applyUpdate(conn, modelNumb, price, null);
                        changed = true;
                        System.out.printf("-> [สำเร็จ] ราคาเปลี่ยนจาก $%.2f เป็น $%.2f (รอ Commit)%n", oldValues[0], price);

                    } else if (choice == 2) {
                        System.out.print("ระยะเวลาผลิตใหม่ (วัน): ");
                        int days = scanner.nextInt();
                        if (days < 0) {
                            System.out.println("-> ระยะเวลาต้องไม่ติดลบ");
                            continue;
                        }
                        applyUpdate(conn, modelNumb, null, days);
                        changed = true;
                        System.out.println("-> [สำเร็จ] ระยะเวลาผลิตเปลี่ยนจาก " + (int) oldValues[1] + " เป็น " + days + " วัน (รอ Commit)");

                    } else if (choice == 3) {
                        if (!changed) {
                            System.out.println("-> ยังไม่ได้แก้ไขข้อมูล");
                        } else {
                            conn.commit();
                            System.out.println("-> [สำเร็จสมบูรณ์] บันทึกการแก้ไขรุ่น #" + modelNumb + " เรียบร้อย (Committed)!");
                            break;
                        }

                    } else if (choice == 4) {
                        conn.rollback();
                        System.out.println("-> ยกเลิกการทำงานที่ค้างอยู่ (Rollback) และออกจากโปรแกรมเรียบร้อย");
                        break;

                    } else {
                        System.out.println("-> กรุณาเลือกตัวเลข 1 ถึง 4 เท่านั้น");
                    }
                } catch (SQLException e) {
                    conn.rollback();
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
    //  ส่วนที่ให้ T7_UpdateModelPriceTest เรียกใช้ (ไม่มี Scanner)
    //  newPrice / newDays เป็น null = ไม่แก้ค่านั้น
    //  commit=false -> ทำครบทุกขั้นตอนแล้ว rollback
    // =====================================================================
    public static Result updateModel(Connection conn, int modelNumb, Double newPrice, Integer newDays,
                                     int isolation, boolean commit) throws SQLException {
        boolean oldAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(isolation);

            if ((newPrice == null && newDays == null)
                    || (newPrice != null && newPrice <= 0)
                    || (newDays != null && newDays < 0)) {
                conn.rollback();
                return Result.INVALID_VALUE;
            }
            if (readModel(conn, SELECT_MODEL, modelNumb) == null) {
                conn.rollback();
                return Result.MODEL_NOT_FOUND;
            }
            applyUpdate(conn, modelNumb, newPrice, newDays);

            if (commit) conn.commit(); else conn.rollback();
            return Result.SUCCESS;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAutoCommit);
        }
    }

    static int applyUpdate(Connection conn, int modelNumb, Double price, Integer days) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_MODEL)) {
            if (price == null) ps.setNull(1, Types.DECIMAL); else ps.setDouble(1, price);
            if (days == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, days);
            ps.setInt(3, modelNumb);
            return ps.executeUpdate();
        }
    }

    /** คืน {ราคา, ระยะเวลาผลิต} หรือ null ถ้าไม่พบรุ่นนี้ */
    static double[] readModel(Connection conn, String sql, int modelNumb) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, modelNumb);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new double[] { rs.getDouble(1), rs.getInt(2) };
            }
        }
    }
}