/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 7007609 7009618
 * @summary Check that ZipFile objects are always collected
 */

import java.io.*;
import java.util.Random;
import java.util.zip.*;
import java.util.concurrent.CountDownLatch;

public class FinalizeZipFile {

    private final static CountDownLatch finalizersDone = new CountDownLatch(3);

    private static class InstrumentedZipFile extends ZipFile {

        public InstrumentedZipFile(File f) throws Exception {
            super(f);
            System.out.printf("Using %s%n", f.getPath());
        }
        @Override
        protected void finalize() throws IOException {
            System.out.printf("Killing %s%n", getName());
            super.finalize();
            finalizersDone.countDown();
        }
    }

    private static void makeGarbage() throws Throwable {
        final Random rnd = new Random();
        // Create some ZipFiles.
        // Find some .jar files in test directory.
        final File testdir = new File(System.getProperty("test.src", "."));
        check(testdir.isDirectory());
        final File[] jars = testdir.listFiles(
            new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".jar");}});
        check(jars.length > 1);

        new InstrumentedZipFile(jars[rnd.nextInt(jars.length)]).close();
        new InstrumentedZipFile(jars[rnd.nextInt(jars.length)]).close();

        // Create a ZipFile and get an input stream from it
        for (int i = 0; i < jars.length + 10; i++) {
            ZipFile zf = new InstrumentedZipFile(jars[rnd.nextInt(jars.length)]);
            ZipEntry ze = zf.getEntry("META-INF/MANIFEST.MF");
            if (ze != null) {
                InputStream is = zf.getInputStream(ze);
                break;
            }
        }
    }

    public static void realMain(String[] args) throws Throwable {
        makeGarbage();

        System.gc();
        finalizersDone.await();

        // Not all ZipFiles were collected?
        equal(finalizersDone.getCount(), 0L);
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
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
