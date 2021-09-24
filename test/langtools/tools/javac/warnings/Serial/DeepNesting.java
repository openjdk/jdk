/*
 * @test /nodynamiccopyright/
 * @bug 8202056
 * @compile/ref=EnumSerial.out -XDrawDiagnostics -Xlint:serial EnumSerial.java
 */

import java.io.Serializable;


enum EnumSerial implements Serializable {
    INSTANCE;

    // Verify a warning is generated in an enum for each of the
    // distinguished serial fields and methods.

}
