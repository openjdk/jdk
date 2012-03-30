/*
 * @test WBApi
 * @summary verify that whitebox functions can be linked and executed
 * @run compile -J-XX:+UnlockDiagnosticVMOptions -J-XX:+WhiteBoxAPI WBApi.java
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI WBApi
 */

import sun.hotspot.WhiteBox;
public class WBApi {
    public static void main(String... args) {
        System.out.printf("args at: %x\n",WhiteBox.getWhiteBox().getObjectAddress(args));
    }
}
