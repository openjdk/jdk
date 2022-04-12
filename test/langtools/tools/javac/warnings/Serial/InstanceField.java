/*
 * @test /nodynamiccopyright/
 * @bug 8202056
 * @compile/ref=InstanceField.out -XDrawDiagnostics -Xlint:serial InstanceField.java
 */

import java.io.*;

class IntanceField implements Serializable {
    private static final long serialVersionUID = 42;

    // Non-transient instance fields in a serializable class w/o
    // serialPersistentFields defined should get warnings if the type
    // of the field cannot be serialized.

    private Object foo;

    private Object[] foos;

    private Thread[][] ArrayOfArrayOfThreads;

    // No warnings

    private static Object bar;

    private static Object[] bars;

    private int baz;

    private double[] quux;

    static class NestedInstance implements Serializable {
        private static final long serialVersionUID = 24;

        // Should disable instance field warnings
        private static final ObjectStreamField[] serialPersistentFields = {};

        private Object foo;
    }
}
