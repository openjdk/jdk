/*
 * @test /nodynamiccopyright/
 * @bug 8202056
 * @compile/ref=EnumSerial.out -XDrawDiagnostics -Xlint:serial EnumSerial.java
 */

import java.io.*;

enum EnumSerial implements Serializable {
    INSTANCE;

    // Verify a warning is generated in an enum class for each of the
    // distinguished serial fields and methods.

    private static final long serialVersionUID = 42;
    private static final ObjectStreamField[] serialPersistentFields = {};

    private void writeObject(ObjectOutputStream stream) throws IOException {
        stream.defaultWriteObject();
    }

    private Object writeReplace() throws ObjectStreamException {
        return null;
    }

    private void readObject(ObjectInputStream stream)
        throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
    }

    private void readObjectNoData() throws ObjectStreamException {
        return;
    }

    private Object readResolve() throws ObjectStreamException {
        return null;
    }
}
