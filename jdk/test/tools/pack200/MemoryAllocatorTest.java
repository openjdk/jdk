/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6755943
 * @summary Checks any memory overruns in archive length.
 * @run main/timeout=1200 MemoryAllocatorTest
 */
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MemoryAllocatorTest {

    /*
     * The smallest possible pack file with 1 empty resource
     */
    static int[] magic = {
        0xCA, 0xFE, 0xD0, 0x0D
    };
    static int[] version_info = {
        0x07, // minor
        0x96  // major
    };
    static int[] option = {
        0x10
    };
    static int[] size_hi = {
        0x00
    };
    static int[] size_lo_ulong = {
        0xFF, 0xFC, 0xFC, 0xFC, 0xFC // ULONG_MAX 0xFFFFFFFF
    };
    static int[] size_lo_correct = {
        0x17
    };
    static int[] data = {
        0x00, 0xEC, 0xDA, 0xDE, 0xF8, 0x45, 0x01, 0x02,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x01, 0x31, 0x01, 0x00
    };
    // End of pack file data

    static final String JAVA_HOME = System.getProperty("java.home");

    static final boolean debug = Boolean.getBoolean("MemoryAllocatorTest.Debug");
    static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    static final boolean LINUX = System.getProperty("os.name").startsWith("Linux");
    static final boolean SIXTYFOUR_BIT = System.getProperty("sun.arch.data.model", "32").equals("64");
    static final private int EXPECTED_EXIT_CODE = (WINDOWS) ? -1 : 255;

    static int testExitValue = 0;

    static byte[] bytes(int[] a) {
        byte[] b = new byte[a.length];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) a[i];
        }
        return b;
    }

    static void createPackFile(boolean good, File packFile) throws IOException {
        FileOutputStream fos = new FileOutputStream(packFile);
        fos.write(bytes(magic));
        fos.write(bytes(version_info));
        fos.write(bytes(option));
        fos.write(bytes(size_hi));
        if (good) {
            fos.write(bytes(size_lo_correct));
        } else {
            fos.write(bytes(size_lo_ulong));
        }
        fos.write(bytes(data));
    }

    /*
     * This method modifies the LSB of the size_lo for various wicked
     * values between MAXINT-0x3F and MAXINT.
     */
    static int modifyPackFile(File packFile) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(packFile, "rws");
        long len = packFile.length();
        FileChannel fc = raf.getChannel();
        MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_WRITE, 0, len);
        int pos = magic.length + version_info.length + option.length +
                size_hi.length;
        byte value = bb.get(pos);
        value--;
        bb.position(pos);
        bb.put(value);
        bb.force();
        fc.truncate(len);
        fc.close();
        return value & 0xFF;
    }

    static String getUnpack200Cmd() throws Exception {
        File binDir = new File(JAVA_HOME, "bin");
        File unpack200File = WINDOWS
                ? new File(binDir, "unpack200.exe")
                : new File(binDir, "unpack200");

        String cmd = unpack200File.getAbsolutePath();
        if (!unpack200File.canExecute()) {
            throw new Exception("please check" +
                    cmd + " exists and is executable");
        }
        return cmd;
    }

    static TestResult runUnpacker(File packFile) throws Exception {
        if (!packFile.exists()) {
            throw new Exception("please check" + packFile + " exists");
        }
        ArrayList<String> alist = new ArrayList<String>();
        ProcessBuilder pb = new ProcessBuilder(getUnpack200Cmd(),
                packFile.getName(), "testout.jar");
        Map<String, String> env = pb.environment();
        pb.directory(new File("."));
        int retval = 0;
        try {
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(p.getInputStream()), 8192);
            String in = rd.readLine();
            while (in != null) {
                alist.add(in);
                System.out.println(in);
                in = rd.readLine();
            }
            retval = p.waitFor();
            p.destroy();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex.getMessage());
        }
        return new TestResult("", retval, alist);
    }

    /*
     * The debug version builds of unpack200 call abort(3) which might set
     * an unexpected return value, therefore this test is to determine
     * if we are using a product or non-product build and check the
     * return value appropriately.
     */
    static boolean isNonProductVersion() throws Exception {
        ArrayList<String> alist = new ArrayList<String>();
        ProcessBuilder pb = new ProcessBuilder(getUnpack200Cmd(), "--version");
        Map<String, String> env = pb.environment();
        pb.directory(new File("."));
        int retval = 0;
        try {
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(p.getInputStream()), 8192);
            String in = rd.readLine();
            while (in != null) {
                alist.add(in);
                System.out.println(in);
                in = rd.readLine();
            }
            retval = p.waitFor();
            p.destroy();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex.getMessage());
        }
        for (String x : alist) {
            if (x.contains("non-product")) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param args the command line arguments
     * @throws java.lang.Exception
     */
    public static void main(String[] args) throws Exception {

        File packFile = new File("tiny.pack");
        boolean isNPVersion = isNonProductVersion();

        // Create a good pack file and test if everything is ok
        createPackFile(true, packFile);
        TestResult tr = runUnpacker(packFile);
        tr.setDescription("a good pack file");
        tr.checkPositive();
        tr.isOK();
        System.out.println(tr);

        /*
         * jprt systems on windows and linux seem to have abundant memory
         * therefore can take a very long time to run, and even if it does
         * the error message is not accurate for us to discern if the test
         * passess successfully.
         */
        if (SIXTYFOUR_BIT && (LINUX || WINDOWS)) {
            System.out.println("Warning: Windows/Linux 64bit tests passes vacuously");
            return;
        }

        /*
         * debug builds call abort, the exit code under these conditions
         * are not really relevant.
         */
        if (isNPVersion) {
            System.out.println("Warning: non-product build: exit values not checked");
        }

        // create a bad pack file
        createPackFile(false, packFile);
        tr = runUnpacker(packFile);
        tr.setDescription("a wicked pack file");
        tr.contains("Native allocation failed");
        if(!isNPVersion) {
            tr.checkValue(EXPECTED_EXIT_CODE);
        }
        System.out.println(tr);
        int value = modifyPackFile(packFile);
        tr.setDescription("value=" + value);

        // continue creating bad pack files by modifying the specimen pack file.
        while (value >= 0xc0) {
            tr = runUnpacker(packFile);
            tr.contains("Native allocation failed");
            if (!isNPVersion) {
                tr.checkValue(EXPECTED_EXIT_CODE);
            }
            tr.setDescription("wicked value=0x" +
                    Integer.toHexString(value & 0xFF));
            System.out.println(tr);
            value = modifyPackFile(packFile);
        }
        if (testExitValue != 0) {
            throw new Exception("Pack200 archive length tests(" +
                    testExitValue + ") failed ");
        } else {
            System.out.println("All tests pass");
        }
    }

    /*
     * A class to encapsulate the test results and stuff, with some ease
     * of use methods to check the test results.
     */
    static class TestResult {

        StringBuilder status;
        int exitValue;
        List<String> testOutput;
        String description;

        public TestResult(String str, int rv, List<String> oList) {
            status = new StringBuilder(str);
            exitValue = rv;
            testOutput = oList;
        }

        void setDescription(String description) {
            this.description = description;
        }

        void checkValue(int value) {
            if (exitValue != value) {
                status =
                        status.append("  Error: test expected exit value " +
                        value + "got " + exitValue);
                testExitValue++;
            }
        }

        void checkNegative() {
            if (exitValue == 0) {
                status = status.append(
                        "  Error: test did not expect 0 exit value");

                testExitValue++;
            }
        }

        void checkPositive() {
            if (exitValue != 0) {
                status = status.append(
                        "  Error: test did not return 0 exit value");
                testExitValue++;
            }
        }

        boolean isOK() {
            return exitValue == 0;
        }

        boolean isZeroOutput() {
            if (!testOutput.isEmpty()) {
                status = status.append("  Error: No message from cmd please");
                testExitValue++;
                return false;
            }
            return true;
        }

        boolean isNotZeroOutput() {
            if (testOutput.isEmpty()) {
                status = status.append("  Error: Missing message");
                testExitValue++;
                return false;
            }
            return true;
        }

        public String toString() {
            if (debug) {
                for (String x : testOutput) {
                    status = status.append(x + "\n");
                }
            }
            if (description != null) {
                status.insert(0, description);
            }
            return status.append("\nexitValue = " + exitValue).toString();
        }

        boolean contains(String str) {
            for (String x : testOutput) {
                if (x.contains(str)) {
                    return true;
                }
            }
            status = status.append("   Error: string <" + str + "> not found ");
            testExitValue++;
            return false;
        }
    }
}
