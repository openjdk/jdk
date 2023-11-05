/*
 * @test /nodynamiccopyright/
 * @bug 8202056
 * @compile/ref=ImproperReturnTypes.out -XDrawDiagnostics -Xlint:serial ImproperReturnTypes.java
 */

import java.io.*;

class ImproperReturnTypes implements Serializable {
    private static final long serialVersionUID = 42;

    /*
     * Serialization-related methods return either void or Object:
     *
     * private             void   writeObject(ObjectOutputStream stream) throws IOException
     * ANY-ACCESS-MODIFIER Object writeReplace() throws ObjectStreamException
     *
     * private             void   readObject(ObjectInputStream stream)
     *                            throws IOException, ClassNotFoundException
     * private             void   readObjectNoData() throws ObjectStreamException
     * ANY-ACCESS-MODIFIER Object readResolve() throws ObjectStreamException
     */

    private int writeObject(ObjectOutputStream stream) throws IOException {
        stream.defaultWriteObject();
        return 0;
    }

    private int writeReplace() throws ObjectStreamException {
        return 1;
    }

    private int readObject(ObjectInputStream stream)
        throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        return 2;
    }

    private int readObjectNoData() throws ObjectStreamException {
        return 3;
    }

    private int readResolve() throws ObjectStreamException {
        return 4;
    }
}
