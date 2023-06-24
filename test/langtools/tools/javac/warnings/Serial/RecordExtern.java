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

    // *Not* Externalizable methods
    public void writeExternal() {
        ;
    }

    public void readExternal() {
        ;
    }
}
