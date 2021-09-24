/*
 * @test /nodynamiccopyright/
 * @bug 8202056
 * @compile/ref=Extern.out -XDrawDiagnostics -Xlint:serial Extern.java
 */


import java.io.*;

class Extern implements Externalizable {
    private static final long serialVersionUID = 42;

    // ineffectual
    private static final ObjectStreamField[] serialPersistentFields = {};

    public void writeExternal(ObjectOutput out) throws IOException {
	return;
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	return;
    }

    // ineffectual
    private void readObject(ObjectInputStream stream)
	throws IOException, ClassNotFoundException {
	return;
    }

    // ineffectual
    private void writeObject(ObjectOutputStream stream) throws IOException {
	return;
    }

    // ineffectual
    private void readObjectNoData() throws ObjectStreamException {
	return;
    }

    // (possibly) effective
    private Object writeReplace() throws ObjectStreamException {
	return null;
    }

    // (possibly) effective
    private Object readResolve() throws ObjectStreamException {
	return null;
    }

}
