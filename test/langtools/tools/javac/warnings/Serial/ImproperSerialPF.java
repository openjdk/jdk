/*
 * @test /nodynamiccopyright/
 * @bug 8202056
 * @compile/ref=ImproperSerialPF.out -XDrawDiagnostics -Xlint:serial ImproperSerialPF.java
 */

import java.io.*;

class ImproperSerialPF implements Serializable {
    // Proper declaration of serialPersistentFields is:
    // private static final ObjectStreamField[] serialPersistentFields = ...
    public /*instance*/ Object serialPersistentFields = null;

    private static final long serialVersionUID = 42;
}
