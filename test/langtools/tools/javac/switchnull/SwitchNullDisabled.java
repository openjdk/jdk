/*
 * @test /nodynamiccopyright/
 * @bug 8206986
 * @summary Verify "case null" is not allowed for --release 16, 20
 * @compile/fail/ref=SwitchNullDisabled.out -XDrawDiagnostics --release 20 SwitchNullDisabled.java
 * @compile SwitchNullDisabled.java
 * @compile -J-XX:+UnlockExperimentalVMOptions -J-XX:hashCode=2 SwitchNullDisabled.java
 */

public class SwitchNullDisabled {
    private int switchNull(String str) {
        switch (str) {
            case null: return 0;
            case "": return 1;
            default: return 2;
        }
    }
}
