/*
 * @test /nodynamiccopyright/
 * @bug 8202056
 * @compile/ref=InterfaceNonPrivateMethods.out -XDrawDiagnostics -Xlint:serial InterfaceNonPrivateMethods.java
 */

import java.io.*;

// Holder class
class InterfaceNonPrivateMethods {

    interface NonPrivateMethods extends Serializable {
        public void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException;
        public void readObjectNoData() throws ObjectStreamException;
        public void writeObject(ObjectOutputStream stream) throws IOException;

        // Ineffective default methods; serialization only looks up
        // superclass chain
        public default Object writeReplace() throws ObjectStreamException {
            return null;
        }
        public default Object readResolve() throws ObjectStreamException {
            return null;
        }
    }

    interface NonPrivateMethodsDefaults extends Serializable {
        default public void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
            return;
        }
        default public void readObjectNoData()
            throws ObjectStreamException {
            return;
        }
        default public void writeObject(ObjectOutputStream stream)
            throws IOException {
            return;
        }
    }
}

