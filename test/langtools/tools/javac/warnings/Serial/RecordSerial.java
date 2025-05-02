/*
 * @test /nodynamiccopyright/
 * @bug 8202056 8310861
 * @compile/ref=RecordSerial.out -XDrawDiagnostics -Xlint:serial RecordSerial.java
 */

import java.io.*;

record RecordSerial(int foo) implements Serializable {
    // Verify a warning is generated in a record class for each of the
    // ineffectual serial fields and methods.

    // partially effective
    private static final long serialVersionUID = 42;

    // ineffectual
    private static final ObjectStreamField[] serialPersistentFields = {};

    // ineffectual
    private void writeObject(ObjectOutputStream stream) throws IOException {
        stream.defaultWriteObject();
    }

    // (possibly) effective
    private Object writeReplace() throws ObjectStreamException {
        return null;
    }

    // ineffectual
    private void readObject(ObjectInputStream stream)
        throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
    }

    // ineffectual
    private void readObjectNoData() throws ObjectStreamException {
        return;
    }

    // (possibly) effective
    private Object readResolve() throws ObjectStreamException {
        return null;
    }

    // meaningless Externalizable methods, no warning generated
    public void writeExternal(ObjectOutput oo) {
        ;
    }

    public void readExternal(ObjectInput oi) {
        ;
    }
}
