/*
 * @test /nodynamiccopyright/
 * @bug 5009574
 * @summary verify an enum type can't be directly subclassed
 * @author Joseph D. Darcy
 *
 * @compile/fail/ref=FauxEnum3.out -XDrawDiagnostics  FauxEnum3.java
 * @compile/fail/ref=FauxEnum3.preview.out -XDrawDiagnostics --enable-preview -source ${jdk.version} FauxEnum3.java
 */

public class FauxEnum3 extends SpecializedEnum {
}

enum SpecializedEnum {
    RED {
        boolean special() {return true;}
    },
    GREEN,
    BLUE;
    boolean special() {return false;}
}
