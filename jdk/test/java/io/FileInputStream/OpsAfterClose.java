/**
 *  @test
 *  @bug 6359397
 *  @summary Test if FileInputStream methods will check if the stream
 *          has been closed.
 */

import java.io.*;

public enum OpsAfterClose {

        READ { boolean check(FileInputStream r) {
                    try {
                        r.read();
                    } catch (IOException io) {
                        System.out.print("Excep Msg: "+ io.getMessage() + ", ");
                        return true;
                    }
                    return false;
             } },

        READ_BUF { boolean check(FileInputStream r) {
                    try {
                        byte buf[] = new byte[2];
                        r.read(buf);
                    } catch (IOException io) {
                        System.out.print("Excep Msg: "+ io.getMessage() + ", ");
                        return true;
                    }
                    return false;
            } },
        READ_BUF_OFF { boolean check(FileInputStream r) {
                    try {
                        byte buf[] = new byte[2];
                        int len = 1;
                        r.read(buf, 0, len);
                    } catch (IOException io) {
                        System.out.print("Excep Msg: "+ io.getMessage() + ", ");
                        return true;
                    }
                    return false;
             } },
        GET_CHANNEL { boolean check(FileInputStream r) {
                    r.getChannel();
                    return true;
             } },
        GET_FD { boolean check(FileInputStream r) {
                    try {
                        r.getFD();
                        return true;
                    } catch (IOException io) {
                        System.out.print("Excep Msg: "+ io.getMessage() + ", ");
                        return false;
                    }
             } },
        SKIP { boolean check(FileInputStream r) {
                    try {
                        r.skip(1);
                    } catch (IOException io) {
                        System.out.print("Excep Msg: "+ io.getMessage() + ", ");
                        return true;
                    }
                    return false;
             } },
        CLOSE { boolean check(FileInputStream r) {
                try {
                    r.close();
                    return true; // No Exception thrown on windows
                } catch (IOException io) {
                    System.out.print("Excep Msg: "+ io.getMessage() + ", ");
                    return true; // Exception thrown on solaris and linux
                }
             } };

    abstract boolean check(FileInputStream r);

    public static void main(String args[]) throws Exception {

        boolean failed = false;

        File f = new File(System.getProperty("test.dir", "."),
                          "f.txt");
        f.createNewFile();
        f.deleteOnExit();

        FileInputStream fis = new FileInputStream(f);
        if (testFileInputStream(fis)) {
            throw new Exception("Test failed for some of the operation{s}" +
                " on FileInputStream, check the messages");
        }
    }

    private static boolean testFileInputStream(FileInputStream r)
            throws Exception {
        r.close();
        boolean failed = false;
        boolean result;
        System.out.println("Testing File:" + r);
        for (OpsAfterClose op : OpsAfterClose.values()) {
            result = op.check(r);
            if (!result) {
                failed = true;
            }
           System.out.println(op + ":" + result);
        }
        if (failed) {
            System.out.println("Test failed for the failed operation{s}" +
                        " above for the FileInputStream:" + r);
        }
        return failed;
    }
}
