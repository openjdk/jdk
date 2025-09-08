/*
 * @test
 * @summary Test WhiteBox.waitForReferenceProcessing
 * @bug 8305186 8355632
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @modules java.base/java.lang.ref:open
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -ea -esa
 *      -Xbootclasspath/a:.
 *      -XX:+UnlockDiagnosticVMOptions
 *      -XX:+WhiteBoxAPI
 *      WaitForRefProcTest
 */

import jdk.test.whitebox.WhiteBox;

public class WaitForRefProcTest {

    public static void main(String[] args) {
        WhiteBox.getWhiteBox().fullGC();
        try {
            boolean ret = WhiteBox.getWhiteBox().waitForReferenceProcessing();
            System.out.println("wFRP returned " + ret);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException("test caught InterruptedException");
        }
    }
}
