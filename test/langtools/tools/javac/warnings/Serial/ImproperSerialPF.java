/*
 * @test /nodynamiccopyright/
 * @bug 8202056
 * @compile/ref=ImproperSerialPF.out -XDrawDiagnostics -Xlint:serial ImproperSerialPF.java
 */

import java.io.*;

class ImproperSerialPF implements Serializable {
    // Proper declaration of serialPersistentFields is:
    // private static final ObjectStreamField[] serialPersistentFields = ...
    public /*instance*/ Object serialPersistentFields = Boolean.TRUE;

    private static final long serialVersionUID = 42;

    static class LiteralNullSPF implements Serializable {
        private static final ObjectStreamField[] serialPersistentFields = null;

        private static final long serialVersionUID = 42;
    }

    // Casting obscures the simple syntactic null-check
    static class CastedNullSPF implements Serializable {
        private static final ObjectStreamField[] serialPersistentFields =
            (ObjectStreamField[])null;

        private static final long serialVersionUID = 42;
    }

    // Conditional obscures the simple syntactic null-check too
    static class ConditionalNullSPF implements Serializable {
        private static final ObjectStreamField[] serialPersistentFields =
            (true ? null : null);

        private static final long serialVersionUID = 42;
    }
}
