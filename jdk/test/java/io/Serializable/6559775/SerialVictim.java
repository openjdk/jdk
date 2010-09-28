/**
 * Instances of this class created through deserialization
 *   should, in theory, be immutable.
 * Instances created through the public constructor
 *   are only to ease creation of the binary attack data.
 */

public class SerialVictim implements java.io.Serializable {
    private char[] value;
    public SerialVictim(char[] value) {
        this.value = value;
    }
    //@Override
    public String toString() {
        return new String(value);
    }
    private void readObject(
        java.io.ObjectInputStream in
    ) throws java.io.IOException, java.lang.ClassNotFoundException {
        java.io.ObjectInputStream.GetField fields = in.readFields();
        // Clone before write should, in theory, make instance safe.
        this.value = (char[])((char[])fields.get("value", null)).clone();
        in.readObject();
    }
    private void writeObject(
        java.io.ObjectOutputStream out
    ) throws java.io.IOException {
        out.defaultWriteObject();
        out.writeObject(this);
    }
}
