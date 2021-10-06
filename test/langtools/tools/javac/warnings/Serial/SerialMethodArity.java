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

    private void readObjectNoData() throws ObjectStreamException {}

    public Object writeReplace() throws ObjectStreamException {
        return null;
    }

    public Object readResolve() throws ObjectStreamException {
        return null;
    }
}
