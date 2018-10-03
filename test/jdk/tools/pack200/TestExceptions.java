/*
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;

/*
 * @test
 * @bug 6985763
 * @summary verify that proper exceptions are thrown
 * @compile -XDignore.symbol.file Utils.java TestExceptions.java
 * @run main TestExceptions
 * @author ksrini
 */

public class TestExceptions {

    static final File testJar = new File("test.jar");
    static final File testPackFile = new File("test.pack");

    static void init() {
        Utils.jar("cvf", testJar.getAbsolutePath(), ".");
        JarFile jf = null;
        try {
            jf = new JarFile(testJar);
            Utils.pack(jf, testPackFile);
        } catch (IOException ioe) {
            throw new Error("Initialization error", ioe);
        } finally {
            Utils.close(jf);
        }
    }

    // a test that closes the input jarFile.
    static void pack200Test1() {
        PackTestInput ti = null;
        // setup the scenario
        try {
            ti = new PackTestInput(new JarFile(testJar), new ByteArrayOutputStream());
        } catch (Exception e) {
            throw new Error("Initialization error", e);
        } finally {
            Utils.close(ti.getJarFile());
        }
        // test the scenario
        try {
            System.out.println(ti);
            Pack200.Packer p = Pack200.newPacker();
            p.pack(ti.getJarFile(), ti.getOutputStream());
        } catch (Exception e) {
            ti.checkException(e);
        } finally {
            if (ti != null) {
                ti.close();
            }
        }
    }

    // test the Pack200.pack(JarFile, OutputStream);
    static void pack200Test2() {
        List<PackTestInput> tlist = new ArrayList<PackTestInput>();
        try {
            // setup the test scenarios
            try {
                tlist.add(new PackTestInput((JarFile)null, null));
                tlist.add(new PackTestInput(new JarFile(testJar), null));
                tlist.add(new PackTestInput((JarFile)null, new ByteArrayOutputStream()));
            } catch (Exception e) {
                throw new Error("Initialization error", e);
            }

            // test the scenarios
            for (PackTestInput ti : tlist) {
                System.out.println(ti);
                try {
                    Pack200.Packer p = Pack200.newPacker();
                    p.pack(ti.getJarFile(), ti.getOutputStream());
                } catch (Exception e) {
                    ti.checkException(e);
                }
            }
        } finally { // clean up
            for (TestInput ti : tlist) {
                if (ti != null) {
                    ti.close();
                }
            }
        }
    }

    // test the Pack200.pack(JarInputStream, OutputStream);
    static void pack200Test3() {
        List<PackTestJarInputStream> tlist = new ArrayList<PackTestJarInputStream>();
        try {
            // setup the test scenarios
            try {
                tlist.add(new PackTestJarInputStream((JarInputStream)null, null));
                tlist.add(new PackTestJarInputStream((JarInputStream)null,
                        new ByteArrayOutputStream()));
                tlist.add(new PackTestJarInputStream(
                        new JarInputStream(new FileInputStream(testJar)), null));

            } catch (Exception e) {
                throw new Error("Initialization error", e);
            }
            for (PackTestJarInputStream ti : tlist) {
                System.out.println(ti);
                try {
                    Pack200.Packer p = Pack200.newPacker();
                    p.pack(ti.getJarInputStream(), ti.getOutputStream());
                } catch (Exception e) {
                    ti.checkException(e);
                }
            }
        } finally { // clean up
            for (PackTestJarInputStream ti : tlist) {
                if (ti != null) {
                    ti.close();
                }
            }
        }
    }

    // test the Pack200.unpack(InputStream, OutputStream);
    static void unpack200Test1() {
        List<UnpackTestInput> tlist = new ArrayList<UnpackTestInput>();
        try {
            // setup the test scenarios
            try {
                tlist.add(new UnpackTestInput((InputStream)null, null));
                tlist.add(new UnpackTestInput(new FileInputStream(testPackFile),
                        null));
                tlist.add(new UnpackTestInput((InputStream) null,
                        new JarOutputStream(new ByteArrayOutputStream())));
            } catch (Exception e) {
                throw new Error("Initialization error", e);
            }

            // test the scenarios
            for (UnpackTestInput ti : tlist) {
                System.out.println(ti);
                try {
                    Pack200.Unpacker unpacker = Pack200.newUnpacker();
                    unpacker.unpack(ti.getInputStream(), ti.getJarOutputStream());
                } catch (Exception e) {
                    ti.checkException(e);
                }
            }
        } finally { // clean up
            for (TestInput ti : tlist) {
                if (ti != null) {
                    ti.close();
                }
            }
        }
    }

    // test the Pack200.unpack(File, OutputStream);
    static void unpack200Test2() {
        List<UnpackTestFileInput> tlist = new ArrayList<UnpackTestFileInput>();
        try {
            // setup the test scenarios
            try {
                tlist.add(new UnpackTestFileInput((File)null, null));
                tlist.add(new UnpackTestFileInput(testPackFile, null));
                tlist.add(new UnpackTestFileInput((File)null,
                        new JarOutputStream(new ByteArrayOutputStream())));
            } catch (Exception e) {
                throw new Error("Initialization error", e);
            }

            // test the scenarios
            for (UnpackTestFileInput ti : tlist) {
                System.out.println(ti);
                try {
                    Pack200.Unpacker unpacker = Pack200.newUnpacker();
                    unpacker.unpack(ti.getInputFile(), ti.getJarOutputStream());
                } catch (Exception e) {
                    ti.checkException(e);
                }
            }
        } finally { // clean up
            for (TestInput ti : tlist) {
                if (ti != null) {
                    ti.close();
                }
            }
        }
    }

    public static void main(String... args) throws IOException {
        init();
        pack200Test1();
        pack200Test2();
        pack200Test3();
        unpack200Test1();
        Utils.cleanup();
    }

    // containers for test inputs and management
    static abstract class TestInput {

        private final Object in;
        private final Object out;
        final boolean shouldNPE;
        final String testname;

        public TestInput(String name, Object in, Object out) {
            this.testname = name;
            this.in = in;
            this.out = out;
            shouldNPE = (in == null || out == null);
        }

        @Override
        public String toString() {
            StringBuilder outStr = new StringBuilder(testname);
            outStr.append(", input:").append(in);
            outStr.append(", output:").append(this.out);
            outStr.append(", should NPE:").append(shouldNPE);
            return outStr.toString();
        }

        void close() {
            if (in != null && (in instanceof Closeable)) {
                Utils.close((Closeable) in);
            }
            if (out != null && (out instanceof Closeable)) {
                Utils.close((Closeable) out);
            }
        }

        void checkException(Throwable t) {
            if (shouldNPE) {
                if (t instanceof NullPointerException) {
                    System.out.println("Got expected exception");
                    return;
                } else {
                    throw new RuntimeException("Expected NPE, but got ", t);
                }
            }
            if (t instanceof IOException) {
                System.out.println("Got expected exception");
                return;
            } else {
                throw new RuntimeException("Expected IOException but got ", t);
            }
        }
    }

    static class PackTestInput extends TestInput {

        public PackTestInput(JarFile jf, OutputStream out) {
            super("PackTestInput", jf, out);
        }

        JarFile getJarFile() {
            return (JarFile) super.in;
        }

        OutputStream getOutputStream() {
            return (OutputStream) super.out;
        }
    };

    static class PackTestJarInputStream extends TestInput {

        public PackTestJarInputStream(JarInputStream in, OutputStream out) {
            super("PackTestJarInputStream", in, out);
        }

        JarInputStream getJarInputStream() {
            return (JarInputStream) super.in;
        }

        OutputStream getOutputStream() {
            return (OutputStream) super.out;
        }
    };

    static class UnpackTestInput extends TestInput {

        public UnpackTestInput(InputStream in, JarOutputStream out) {
            super("UnpackTestInput", in, out);
        }

        InputStream getInputStream() {
            return (InputStream) super.in;
        }

        JarOutputStream getJarOutputStream() {
            return (JarOutputStream) super.out;
        }
    };

    static class UnpackTestFileInput extends TestInput {

        public UnpackTestFileInput(File in, JarOutputStream out) {
            super("UnpackTestInput", in, out);
        }

        File getInputFile() {
            return (File) super.in;
        }

        JarOutputStream getJarOutputStream() {
            return (JarOutputStream) super.out;
        }
    };
}
