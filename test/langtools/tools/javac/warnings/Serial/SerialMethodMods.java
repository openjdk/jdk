/*
 * @test /nodynamiccopyright/
 * @bug 8202056
 * @compile/ref=SerialMethodMods.out -XDrawDiagnostics -Xlint:serial SerialMethodMods.java
 */

import java.io.*;

abstract class SerialMethodMods implements Serializable {
    private static final long serialVersionUID = 42;

    // Should be private
    void writeObject(ObjectOutputStream stream) throws IOException {
        stream.defaultWriteObject();
    }

    // Should be private
    public void readObject(ObjectInputStream stream)
        throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
    }

    // Should be concrete instance method
    private static void readObjectNoData() throws ObjectStreamException {}

    // Should be concrete instance method
    public abstract Object writeReplace() throws ObjectStreamException;

    // Should be concrete instance method
    public static Object readResolve() throws ObjectStreamException {
        return null;
    }
}
