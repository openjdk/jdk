/*
 * @test /nodynamiccopyright/
 * @bug 8202056
 * @compile/ref=InterfaceFields.out -XDrawDiagnostics -Xlint:serial InterfaceFields.java
 */

import java.io.*;

interface InterfaceFields extends Serializable {
    public static final long serialVersionUID = 12345;

    public static final ObjectStreamField[] serialPersistentFields = {};
}

