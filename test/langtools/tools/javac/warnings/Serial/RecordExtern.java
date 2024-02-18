/*
 * @test /nodynamiccopyright/
 * @bug 8310835
 * @compile/ref=RecordExtern.out -XDrawDiagnostics -Xlint:serial RecordExtern.java
 */

import java.io.*;

record RecordExtern(int foo) implements Externalizable {
    // Verify a warning is generated in a record class for each of the
    // ineffectual extern methods.

    // ineffective Externalizable methods
    @Override
    public void writeExternal(ObjectOutput oo) {
        ;
    }

    @Override
    public void readExternal(ObjectInput oi) {
        ;
    }

    // *Not* Externalizable methods; shouldn't generate a warning
    public void writeExternal() {
        ;
    }

    public void readExternal() {
        ;
    }

    // Check warnings for serialization methods and fields too

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
}
