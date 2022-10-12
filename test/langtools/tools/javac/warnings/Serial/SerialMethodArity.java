/*
 * @test /nodynamiccopyright/
 * @bug 8202056
 * @compile/ref=SerialMethodArity.out -XDrawDiagnostics -Xlint:serial SerialMethodArity.java
 */

import java.io.*;

class SerialMethodMods implements Serializable {
    private static final long serialVersionUID = 42;

    private static class CustomObjectOutputStream extends ObjectOutputStream {
        public CustomObjectOutputStream() throws IOException,
                                                 SecurityException {}
    }

    // Should have a single parameter of exact type ObjectOutputStream
    private void writeObject(CustomObjectOutputStream stream) throws IOException {
        stream.defaultWriteObject();
    }

    // Should have a single parameter of exact type ObjectInputStream
    private void readObject(ObjectInputStream stream, int retries)
        throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
    }

    // Should have no arguments
    private void readObjectNoData(int retries) throws ObjectStreamException {}

    // Should have no arguments
    public Object writeReplace(int arg0, int arg1) throws ObjectStreamException {
        return null;
    }

    // Should have no arguments
    public Object readResolve(double foo, float bar) throws ObjectStreamException {
        return null;
    }
}
