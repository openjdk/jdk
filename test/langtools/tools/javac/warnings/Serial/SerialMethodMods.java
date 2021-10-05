/*
 * @test /nodynamiccopyright/
 * @bug 8202056
 * @compile/ref=SerialMethodMods.out -XDrawDiagnostics -Xlint:serial SerialMethodMods.java
 */


import java.io.*;

class SerialMethodMods implements Serializable {
    private static final long serialVersionUID = 42;

    // Should be private
    void writeObject(ObjectOutputStream stream) throws IOException {
        stream.defaultWriteObject();
    }

    // Should be private
    public void readObject(ObjectInputStream stream)
        throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
    }

    // Make static once additional warning in place
    // private void readObjectNoData() throws ObjectStreamException

    /*
     * ANY-ACCESS-MODIFIER Object writeReplace() throws ObjectStreamException
     *
     * ANY-ACCESS-MODIFIER Object readResolve() throws ObjectStreamException
     *
     */
}
