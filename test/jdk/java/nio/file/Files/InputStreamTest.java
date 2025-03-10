/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8227609 8233451
 * @summary Test of InputStream and OutputStream created by java.nio.file.Files
 * @library .. /test/lib
 * @build jdk.test.lib.Platform
 * @run junit/othervm --enable-native-access=ALL-UNNAMED InputStreamTest
 */

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import jdk.test.lib.Platform;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.*;

public class InputStreamTest {

    private static final String PIPE = "pipe";
    private static final Path PIPE_PATH = Path.of(PIPE);
    private static final String SENTENCE =
        "Tout est permis mais rien n’est possible";

    private static Path TMPDIR;

    private static class mkfifo {
        public static final FunctionDescriptor DESC = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_SHORT
        );

        public static final MemorySegment ADDR;
        static {
            Linker linker = Linker.nativeLinker();
            SymbolLookup stdlib = linker.defaultLookup();
            ADDR = stdlib.find("mkfifo").orElseThrow();
        }

        public static final MethodHandle HANDLE =
            Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    public static int mkfifo(MemorySegment x0, short x1) {
        var mh$ = mkfifo.HANDLE;
        try {
            return (int)mh$.invokeExact(x0, x1);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }

    private static Thread createWriteThread() {
        Thread t = new Thread(
            new Runnable() {
                public void run() {
                    try (FileOutputStream fos = new FileOutputStream(PIPE);) {
                        fos.write(SENTENCE.getBytes());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        );
        t.start();
        return t;
    }

    @BeforeAll
    static void before() throws InterruptedException, IOException {
        TMPDIR = TestUtil.createTemporaryDirectory();

        if (Platform.isWindows())
            return;

        Files.deleteIfExists(PIPE_PATH);
        try (var newArena = Arena.ofConfined()) {
            var addr = newArena.allocateFrom(PIPE);
            short mode = 0666;
            assertEquals(0, mkfifo(addr, mode));
        }
        if (Files.notExists(PIPE_PATH))
            throw new RuntimeException("Failed to create " + PIPE);
    }

    @AfterAll
    static void after() throws IOException {
        TestUtil.removeAll(TMPDIR);

        if (Platform.isWindows())
            return;

        Files.deleteIfExists(PIPE_PATH);
    }

    /**
     * Tests Files.newInputStream(Path).skip().
     */
    @Test
    void skip() throws IOException {
        Path file = Files.createFile(TMPDIR.resolve("foo"));
        try (OutputStream out = Files.newOutputStream(file)) {
            final int size = 512;
            byte[] blah = new byte[size];
            for (int i = 0; i < size; i++) {
                blah[i] = (byte)(i % 128);
            }
            out.write(blah);
            out.close();

            try (InputStream in = Files.newInputStream(file)) {
                assertTrue(in.available() == size);
                assertTrue(in.skip(size/4) == size/4); // 0.25
                assertTrue(in.available() == 3*size/4);

                int b = in.read();
                assertTrue(b == blah[size/4]);
                assertTrue(in.available() == 3*size/4 - 1);
                assertTrue(in.skip(-1) == -1); // 0.25
                assertTrue(in.available() == 3*size/4);

                assertTrue(in.skip(-size/2) == -size/4); // 0
                assertTrue(in.available() == size);

                assertTrue(in.skip(5*size/4) == size); // 1.0
                assertTrue(in.available() == 0);

                assertTrue(in.skip(-3*size/4) == -3*size/4); // 0.25
                assertTrue(in.available() == 3*size/4);

                byte[] buf = new byte[16];
                in.read(buf, 2, 12);
                assertTrue(Arrays.equals(buf, 2, 14,
                    blah, size/4, size/4 + 12));
                assertTrue(in.skip(-12) == -12); // 0.25

                assertTrue(in.skip(3*size/4) == 3*size/4); // 1.0
                assertTrue(in.available() == 0);

                assertTrue(in.skip(-size/2) == -size/2); // 0.5
                assertTrue(in.available() == size/2);

                assertTrue(in.skip(-size) == -size/2); // 0
                assertTrue(in.available() == size);

                assertTrue(in.skip(size/2) == size/2); // 0.5
                assertTrue(in.available() == size/2);

                assertTrue(in.skip(Long.MIN_VALUE) == -size/2); // 0
                assertTrue(in.available() == size);

                assertTrue(in.skip(size/2) == size/2); // 0.5
                assertTrue(in.available() == size/2);

                assertTrue(in.skip(Long.MAX_VALUE - size/4) == size/2);
                assertTrue(in.available() == 0);

                in.close();
                try {
                    in.skip(1);
                    throw new RuntimeException("skip() did not fail");
                } catch (IOException ioe) {
                    if (!(ioe instanceof ClosedChannelException)) {
                        throw new RuntimeException
                            ("IOException is not a ClosedChannelException");
                    }
                }
            }
        }
    }

    /**
     * Tests that Files.newInputStream(Path).available() does not throw
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void availableStdin() throws IOException {
        Path stdin = Path.of("/dev", "stdin");
        if (Files.exists(stdin)) {
            try (InputStream s = Files.newInputStream(stdin);) {
                s.available();
            }
        }
    }

    /**
     * Tests that Files.newInputStream(Path).skip(0) does not throw
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void skipStdin() throws IOException {
        Path stdin = Path.of("/dev", "stdin");
        if (Files.exists(stdin)) {
            try (InputStream s = Files.newInputStream(stdin);) {
                s.skip(0);
            }
        }
    }

    /**
     * Tests Files.newInputStream(Path).readAllBytes().
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void readAllBytes() throws InterruptedException, IOException {
        Thread t = createWriteThread();
        try (InputStream in = Files.newInputStream(Path.of(PIPE))) {
            String s = new String(in.readAllBytes());
            System.out.println(s);
            assertEquals(SENTENCE, s);
        } finally {
            t.join();
        }
    }

    /**
     * Tests Files.newInputStream(Path).readNBytes(byte[],int,int).
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void readNBytesNoOverride() throws InterruptedException, IOException {
        Thread t = createWriteThread();
        try (InputStream in = Files.newInputStream(Path.of(PIPE))) {
            final int offset = 11;
            final int length = 17;
            assert length <= SENTENCE.length();
            byte[] b = new byte[offset + length];
            int n = in.readNBytes(b, offset, length);
            String s = new String(b, offset, length);
            System.out.println(s);
            assertEquals(SENTENCE.substring(0, length), s);
        } finally {
            t.join();
        }
    }

    /**
     * Tests Files.newInputStream(Path).readNBytes(int).
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void readNBytesOverride() throws InterruptedException, IOException {
        Thread t = createWriteThread();
        try (InputStream in = Files.newInputStream(Path.of(PIPE))) {
            final int length = 17;
            assert length <= SENTENCE.length();
            byte[] b = in.readNBytes(length);
            String s = new String(b);
            System.out.println(s);
            assertEquals(SENTENCE.substring(0, length), s);
        } finally {
            t.join();
        }
    }
}
