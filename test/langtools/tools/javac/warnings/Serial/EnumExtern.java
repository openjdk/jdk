/*
 * @test /nodynamiccopyright/
 * @bug 8310835
 * @compile/ref=EnumExtern.out -XDrawDiagnostics -Xlint:serial EnumExtern.java
 * @compile/ref=empty.out      -XDrawDiagnostics               EnumExtern.java
 */

import java.io.*;

enum EnumExtern implements Externalizable {
    INSTANCE;

    // Verify a warning is generated in an enum class for each of the
    // distinguished serial fields and methods as well as extern methods.

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

    // ineffective Externalizable methods
    @Override
    public void writeExternal(ObjectOutput oo) {
        ;
    }

    @Override
    public void readExternal(ObjectInput oi) {
        ;
    }

    // _Not_ Externalizable methods; shouldn't generate a warning
    public void writeExternal() {
        ;
    }

    public void readExternal() {
        ;
    }
}
