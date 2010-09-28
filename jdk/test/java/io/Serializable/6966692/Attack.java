/*
 * @test
 * @bug 6966692
 * @summary  defaultReadObject can set a field multiple times
 * @run shell Test6966692.sh
*/

import java.io.*;

public class Attack {
    public static void main(String[] args) throws Exception {
        attack(setup());
    }
    /** Returned data has Victim with two aaaa fields. */
    private static byte[] setup() throws Exception {
        Victim victim = new Victim();
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(byteOut);
        out.writeObject(victim);
        out.close();
        byte[] data = byteOut.toByteArray();
        String str = new String(data, 0); // hibyte is 0
        str = str.replaceAll("bbbb", "aaaa");
        str.getBytes(0, data.length, data, 0); // ignore hibyte
        return data;
    }
    private static void attack(byte[] data) throws Exception {
        ObjectInputStream in = new ObjectInputStream(
            new ByteArrayInputStream(data)
        );
        Victim victim = (Victim)in.readObject();
        System.out.println(victim+" "+victim.aaaa);
    }
}
