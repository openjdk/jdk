/*
 * Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 6572945
 * @summary rewrite javah as an annotation processor, instead of as a doclet
 * @modules jdk.compiler/com.sun.tools.javah
 * @build TestClass1 TestClass2 TestClass3
 * @run main T6572945
 */

import java.io.*;
import java.util.*;
import com.sun.tools.javah.Main;

public class T6572945
{
    static File testSrc = new File(System.getProperty("test.src", "."));
    static File testClasses = new File(System.getProperty("test.classes", "."));
    static boolean isWindows = System.getProperty("os.name").startsWith("Windows");

    public static void main(String... args)
        throws IOException, InterruptedException
    {
        boolean ok = new T6572945().run(args);
        if (!ok)
            throw new Error("Test Failed");
    }

    public boolean run(String[] args)
        throws IOException, InterruptedException
    {
        if (args.length == 1)
            jdk = new File(args[0]);

        test("-o", "jni.file.1",  "-jni", "TestClass1");
        test("-o", "jni.file.2",  "-jni", "TestClass1", "TestClass2");
        test("-d", "jni.dir.1",   "-jni", "TestClass1", "TestClass2");
        test("-o", "jni.file.3",  "-jni", "TestClass3");

        // The following tests are disabled because llni support has been
        // discontinued, and because bugs in old javah means that character
        // for character testing against output from old javah does not work.
        // In fact, the LLNI impl in new javah is actually better than the
        // impl in old javah because of a couple of significant bug fixes.

//        test("-o", "llni.file.1", "-llni", "TestClass1");
//        test("-o", "llni.file.2", "-llni", "TestClass1", "TestClass2");
//        test("-d", "llni.dir.1",  "-llni", "TestClass1", "TestClass2");
//        test("-o", "llni.file.3", "-llni", "TestClass3");

        return (errors == 0);
    }

    void test(String... args)
        throws IOException, InterruptedException
    {
        String[] cp_args = new String[args.length + 2];
        cp_args[0] = "-classpath";
        cp_args[1] = testClasses.getPath();
        System.arraycopy(args, 0, cp_args, 2, args.length);

        if (jdk != null)
            init(cp_args);

        File out = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-o")) {
                out = new File(args[++i]);
                break;
            } else if (args[i].equals("-d")) {
                out = new File(args[++i]);
                out.mkdirs();
                break;
            }
        }

        try {
            System.out.println("test: " + Arrays.asList(cp_args));

//            // Uncomment and use the following lines to execute javah via the
//            // command line -- for example, to run old javah and set up the golden files
//            List<String> cmd = new ArrayList<String>();
//            File javaHome = new File(System.getProperty("java.home"));
//            if (javaHome.getName().equals("jre"))
//                javaHome = javaHome.getParentFile();
//            File javah = new File(new File(javaHome, "bin"), "javah");
//            cmd.add(javah.getPath());
//            cmd.addAll(Arrays.asList(cp_args));
//            ProcessBuilder pb = new ProcessBuilder(cmd);
//            pb.redirectErrorStream(true);
//            pb.start();
//            Process p = pb.start();
//            String line;
//            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
//            while ((line = in.readLine()) != null)
//                System.err.println(line);
//            in.close();
//            int rc = p.waitFor();

            // Use new javah
            PrintWriter err = new PrintWriter(System.err, true);
            int rc = Main.run(cp_args, err);

            if (rc != 0) {
                error("javah failed: rc=" + rc);
                return;
            }

            // The golden files use the LL suffix for long constants, which
            // is used on Linux and Solaris.   On Windows, the suffix is i64,
            // so compare will update the golden files on the fly before the
            // final comparison.
            compare(new File(new File(testSrc, "gold"), out.getName()), out);
        } catch (Throwable t) {
            t.printStackTrace();
            error("javah threw exception");
        }
    }

    void init(String[] args) throws IOException, InterruptedException {
        String[] cmdArgs = new String[args.length + 1];
        cmdArgs[0] = new File(new File(jdk, "bin"), "javah").getPath();
        System.arraycopy(args, 0, cmdArgs, 1, args.length);

        System.out.println("init: " + Arrays.asList(cmdArgs));

        ProcessBuilder pb = new ProcessBuilder(cmdArgs);
        pb.directory(new File(testSrc, "gold"));
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = in.readLine()) != null)
            System.out.println("javah: " + line);
        int rc = p.waitFor();
        if (rc != 0)
            error("javah: exit code " + rc);
    }

    /** Compare two directories.
     *  @param f1 The golden directory
     *  @param f2 The directory to be compared
     */
    void compare(File f1, File f2) {
        compare(f1, f2, null);
    }

    /** Compare two files or directories
     *  @param f1 The golden directory
     *  @param f2 The directory to be compared
     *  @param p An optional path identifying a file within the two directories
     */
    void compare(File f1, File f2, String p) {
        File f1p = (p == null ? f1 : new File(f1, p));
        File f2p = (p == null ? f2 : new File(f2, p));
        System.out.println("compare " + f1p + " " + f2p);
        if (f1p.isDirectory() && f2p.isDirectory()) {
            Set<String> children = new HashSet<String>();
            children.addAll(Arrays.asList(f1p.list()));
            children.addAll(Arrays.asList(f2p.list()));
            for (String c: children) {
                compare(f1, f2, new File(p, c).getPath()); // null-safe for p
            }
        }
        else if (f1p.isFile() && f2p.isFile()) {
            String s1 = read(f1p);
            if (isWindows) {
                // f1/s1 is the golden file
                // on Windows, long constants use the i64 suffix, not LL
                s1 = s1.replaceAll("( [0-9]+)LL\n", "$1i64\n");
            }
            String s2 = read(f2p);
            if (!s1.equals(s2)) {
                System.out.println("File: " + f1p + "\n" + s1);
                System.out.println("File: " + f2p + "\n" + s2);
                error("Files differ: " + f1p + " " + f2p);
            }
        }
        else if (f1p.exists() && !f2p.exists())
            error("Only in " + f1 + ": " + p);
        else if (f2p.exists() && !f1p.exists())
            error("Only in " + f2 + ": " + p);
        else
            error("Files differ: " + f1p + " " + f2p);
    }

    private String read(File f) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(f));
            try {
                StringBuilder sb = new StringBuilder((int) f.length());
                String line;
                while ((line = in.readLine()) != null) {
                    sb.append(line);
                    sb.append("\n");
                }
                return sb.toString();
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
        } catch (IOException e) {
            error("error reading " + f + ": " + e);
            return "";
        }
    }


    private void error(String msg) {
        System.out.println(msg);
        errors++;
    }

    private int errors;
    private File jdk;
}
