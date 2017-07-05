/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4806786
 * @summary jar -C doesn't ignore multiple // in path
 */

import java.io.*;
import java.util.*;
import java.util.jar.*;
import sun.tools.jar.Main;

public class ChangeDir {
    private final static String jarName = "test.jar";
    private final static String fileName = "hello.txt";

    /** Remove dirs & files needed for test. */
    private static void cleanup(File dir) throws Throwable {
        if (dir != null && dir.exists()) {
            for (File ff : dir.listFiles()) {
                check(ff.delete());
            }
            check(dir.delete());
            check(new File(jarName).delete());
        }
    }

    public static void realMain(String[] args) throws Throwable {
        doTest("/");
        doTest("//");
        doTest("///");
        doTest("////");
        if (System.getProperty("os.name").startsWith("Windows")) {
            doTest("\\");
            doTest("\\\\");
            doTest("\\\\\\");
            doTest("\\\\\\\\");
            doTest("\\/");
        }
    }

    static void doTest(String sep) throws Throwable {
        File testDir = null;
        JarFile jf = null;
        try {
            // Create a subdirectory "a/b"
            File f = File.createTempFile("delete", ".me");
            String dirName = f.getParent();
            testDir = new File(dirName + sep + "a" + sep + "b");
            cleanup(testDir);
            check(testDir.mkdirs());

            // Create file in that subdirectory
            File testFile = new File(testDir, fileName);
            check(testFile.createNewFile());

            // Create a jar file from that subdirectory, but with a // in the
            // path  name.
            List<String> argList = new ArrayList<String>();
            argList.add("cf");
            argList.add(jarName);
            argList.add("-C");
            argList.add(dirName + sep + "a" + sep + sep + "b"); // Note double 'sep' is intentional
            argList.add(fileName);
            String jarArgs[] = new String[argList.size()];
            jarArgs = argList.toArray(jarArgs);

            Main jarTool = new Main(System.out, System.err, "jar");
            if (!jarTool.run(jarArgs)) {
                fail("Could not create jar file.");
            }

            // Check that the entry for hello.txt does *not* have a pathname.
            jf = new JarFile(jarName);
            for (Enumeration<JarEntry> i = jf.entries(); i.hasMoreElements();) {
                JarEntry je = i.nextElement();
                String name = je.getName();
                if (name.indexOf(fileName) != -1) {
                    if (name.indexOf(fileName) != 0) {
                        fail(String.format(
                                 "Expected '%s' but got '%s'%n", fileName, name));
                    }
                }
            }
        } finally {
            if (jf != null) {
                jf.close();
            }
            cleanup(testDir);
        }
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() {passed++;}
    static void fail() {failed++; Thread.dumpStack();}
    static void fail(String msg) {System.out.println(msg); fail();}
    static void unexpected(Throwable t) {failed++; t.printStackTrace();}
    static void check(boolean cond) {if (cond) pass(); else fail();}
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        try {realMain(args);} catch (Throwable t) {unexpected(t);}
        System.out.println("\nPassed = " + passed + " failed = " + failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
