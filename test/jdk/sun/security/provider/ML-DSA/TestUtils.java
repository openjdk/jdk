import java.io.*;
import java.util.HexFormat;

public class TestUtils {
    public static final String[] nibbles = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"};

    public static String hexChar(int nibble) {
        return nibbles[nibble & 0xf];
    }

    public static String hexStr(byte b) {
        return hexChar((int) (b >> 4)) + hexChar((int) b);
    }

    public static String hexStr(byte[] b) {
        String strOut = "";
        for (int i = 0; i < b.length; i++) {
            strOut += (hexChar((int) (b[i] >> 4)) + hexChar((int) b[i]));
        }
        return strOut;
    }

    public static String hexStr(byte[] b, int bytesPerRow) {
        String strOut = "                ";
        for (int i = 0; i < b.length; i++) {
            strOut += (hexChar((int) (b[i] >> 4)) + hexChar((int) b[i]));
            if ((i % bytesPerRow) == (bytesPerRow - 1)) {
                strOut += "\n                ";
            }
        }
        return strOut;
    }

    public static void printHex(String prefix, byte[] arr) {
        printHex(prefix, arr, 0, arr.length);
    }

    public static void printHex(String prefix, byte[] arr, int start, int len) {
        String str = prefix;
        for (int i = 0; i < len; i++) {
            str += hexStr(arr[start + i]);
        }
        System.out.println(str);
    }

    public static void printHex(String prefix, short[] arr) {
        printHex(prefix, arr, 0, arr.length);
    }

    public static void printHex(String prefix, short[] arr, int start, int len) {
        String str = prefix;
        for (int i = 0; i < len; i++) {
//            short xx = arr[i];
//            System.out.println("xx = " + xx);
//            System.out.println("first byte = " + (byte)(arr[start + i] & 0xff));
//            System.out.println("second byte = " + (byte)(arr[start + i] >>> 8));
            str += hexStr((byte) (arr[start + i] & 0xff));
            str += hexStr((byte) (arr[start + i] >>> 8));
            if ((i % 32 == 31) && (i != len -1)) {
                str += "\n";
            } else {
                str += " ";
            }
        }
        System.out.println(str);
    }


    static void printHex(String prefix, int[] arr) {
        printHex(prefix, arr, 0, arr.length);
    }

    static void printHex(String prefix, int[] arr, int start, int len) {
        String str = prefix;
        for (int i = 0; i < len; i++) {
            int xx = arr[i];
//            System.out.println("xx = " + xx);
//            System.out.println("first byte = " + (byte)(arr[start + i] & 0xff));
//            System.out.println("second byte = " + (byte)(arr[start + i] >>> 8));
            str += hexStr((byte) arr[start + i]);
            str += hexStr((byte) (arr[start + i] >>> 8));
            str += hexStr((byte) (arr[start + i] >>> 16));
            str += hexStr((byte) (arr[start + i] >>> 24));
            str += " ";

        }
        System.out.println(str);
    }

    static byte[] hexDecode(String s) {
        var cleaned = s
            .replaceAll("//.*", "")
            .replaceAll("\\s", "");
        return HexFormat.of().parseHex(cleaned);
    }

    //    static String bitString(short x) {
//        byte a = (byte) (x >> 8);
//        String result = "";
//        for (int i = 0; i < 8; i++) {
//            result +=
//        }
//    }
    public static <T extends Serializable> byte[] pickle(T obj)
        throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();
        return baos.toByteArray();
    }

    public static <T extends Serializable> T unpickle(byte[] b, Class<T> cl)
        throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(b);
        ObjectInputStream ois = new ObjectInputStream(bais);
        Object o = ois.readObject();
        return cl.cast(o);
    }
}
