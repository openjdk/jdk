/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static jdk.test.lib.Asserts.assertNotNull;
import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.util.OpenFiles.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @summary verify functionality of the OpenFilesAgent Java agent
 * @library /test/lib
 * @build jdk.test.lib.util.OpenFilesAgent jdk.test.lib.util.OpenFiles
 * @run driver jdk.test.lib.util.OpenFilesAgent
 * @run junit/othervm -javaagent:OpenFilesAgent.jar OpenFilesTest
 */
public class OpenFilesTest {

    private Path file = Path.of("testfile.txt");

    @BeforeEach
    public void setup() throws IOException {
        Files.createFile(file);
    }

    @AfterEach
    public void cleanup() throws IOException {
        Files.delete(file);
    }

    // Provide file APIs to exercise tests on
    public static Stream<Arguments> arguments() {
        return Stream.of(
                Arguments.of(new FileInputStreamResource()),
                Arguments.of(new FileOutputStreamResource()),
                Arguments.of(new RandomAccessFileResource()),
                Arguments.of(new FilesNewByteChannelResource()),
                Arguments.of(new FilesNewInputStreamResource()),
                Arguments.of(new FilesNewOutputStreamResource()),
                Arguments.of(new FileChannelResource())
        );
    }

    @ParameterizedTest
    @MethodSource("arguments")
    public void shouldDetectOpenAndClose(Resource resource) throws IOException {
        // Sanity check
        assertClosed(file);

        // Open the file
        resource.open(file);

        // Verify open status
        assertOpen(file);
        assertOpen(file.toFile());
        assertOpen(file.toAbsolutePath());
        assertOpen(file.toString());
        assertOpenIf(true, file);

        // Close the file
        resource.close();

        // Verify closed status
        assertClosed(file);
        assertClosed(file.toFile());
        assertClosed(file.toAbsolutePath());
        assertClosed(file.toString());
        assertOpenIf(false, file);

    }

    @ParameterizedTest
    @MethodSource("arguments")
    public void shouldRejectOpenAndClose(Resource resource) throws IOException {
        // Sanity checks
        assertClosed(file);

        // Open the file
        resource.open(file);

        // Fail closed status
        assertThrows(AssertionError.class, () -> {
            assertClosed(file);
        });
        assertThrows(AssertionError.class, () -> {
            assertClosed(file.toFile());
        });
        assertThrows(AssertionError.class, () -> {
            assertClosed(file.toAbsolutePath());
        });
        assertThrows(AssertionError.class, () -> {
            assertClosed(file.toString());
        });

        // Close the file
        resource.close();

        // Fail open status
        assertThrows(AssertionError.class, () -> {
            assertOpen(file);
        });
        assertThrows(AssertionError.class, () -> {
            assertOpen(file.toFile());
        });
        assertThrows(AssertionError.class, () -> {
            assertOpen(file.toAbsolutePath());
        });
        assertThrows(AssertionError.class, () -> {
            assertOpen(file.toString());
        });
    }

    @ParameterizedTest
    @MethodSource("arguments")
    public void shouldCaptureOpeningStackTrace(Resource resource) throws IOException {

        // Open the file
        resource.open(file);

        // Capture an AssertionError
        AssertionError e = assertThrows(AssertionError.class, () -> assertClosed(file));

        // Close file up
        resource.close();
        assertClosed(file);

        // The cause is the capture
        Throwable capture = e.getCause();
        assertNotNull(capture);

        // Message should include a mention of the file path
        assertTrue(capture.getMessage().contains(file.toString()),
                "Expected capture to mention file " + file.toString());

        // Stack trace should include this method
        assertStackTraceIncludes(capture, getClass(), "shouldCaptureOpeningStackTrace");

        // Stack trace should include Resource.open
        assertStackTraceIncludes(capture, resource.getClass(), "open");
    }

    // Assert that a method is found in the stack trace of the given Throwable
    private void assertStackTraceIncludes(Throwable capture, Class<?> clazz, String methodName) {
        for (StackTraceElement se : capture.getStackTrace()) {
            if (se.getClassName().equals(clazz.getName()) && se.getMethodName().equals(methodName)) {
                return;
            }
        }
        fail("Expected stack trace to include " + clazz.getName() + "." + methodName);
    }

    // Abstraction for APIs which can open and close files
    interface Resource {
        void open(Path path) throws IOException;
        void close() throws IOException;
    }

    static class FileInputStreamResource implements Resource {
        private FileInputStream fis;

        @Override
        public void open(Path path) throws IOException {
            this.fis = new FileInputStream(path.toFile());
        }

        @Override
        public void close() throws IOException {
            fis.close();
        }
    }

    static class FileOutputStreamResource implements Resource {
        private FileOutputStream fis;

        @Override
        public void open(Path path) throws IOException {
            this.fis = new FileOutputStream(path.toFile());
        }

        @Override
        public void close() throws IOException {
            fis.close();
        }
    }

    static class RandomAccessFileResource implements Resource {
        private RandomAccessFile raf;

        @Override
        public void open(Path path) throws IOException {
            this.raf = new RandomAccessFile(path.toFile(), "r");
        }

        @Override
        public void close() throws IOException {
            raf.close();
        }
    }

    private static class FileChannelResource implements Resource{

        private Channel channel;

        @Override
        public void open(Path path) throws IOException {
            channel = FileChannel.open(path);
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

    private static class FilesNewByteChannelResource implements Resource {

        private Channel channel;

        @Override
        public void open(Path path) throws IOException {
            channel = Files.newByteChannel(path);
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

    private static class FilesNewOutputStreamResource implements Resource {

        private OutputStream stream;

        @Override
        public void open(Path path) throws IOException {
            stream = Files.newOutputStream(path);
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    private static class FilesNewInputStreamResource implements Resource {

        private InputStream stream;

        @Override
        public void open(Path path) throws IOException {
            stream = Files.newInputStream(path);
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }
    }
}
