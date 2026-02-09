/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8226303 8235459 8358688 8364733
 * @summary Verify all specified `HttpRequest.BodyPublishers::ofFile` behavior
 * @build ByteBufferUtils
 *        RecordingSubscriber
 * @run junit OfFileTest
 *
 * @comment Using `main/othervm` to initiate tests that depend on a custom-configured JVM
 * @run main/othervm -Xmx64m OfFileTest testOOM
 */

public class OfFileTest {

    private static final Path DEFAULT_FS_DIR = Path.of(System.getProperty("user.dir", "."));

    private static final FileSystem ZIP_FS = zipFs();

    private static final Path ZIP_FS_DIR = ZIP_FS.getRootDirectories().iterator().next();

    private static final List<Path> PARENT_DIRS = List.of(DEFAULT_FS_DIR, ZIP_FS_DIR);

    private static FileSystem zipFs() {
        try {
            Path zipFile = DEFAULT_FS_DIR.resolve("file.zip");
            return FileSystems.newFileSystem(zipFile, Map.of("create", "true"));
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    @AfterAll
    static void closeZipFs() throws IOException {
        ZIP_FS.close();
    }

    static List<Path> parentDirs() {
        return PARENT_DIRS;
    }

    @Test
    void testNullPath() {
        assertThrows(NullPointerException.class, () -> HttpRequest.BodyPublishers.ofFile(null));
    }

    @ParameterizedTest
    @MethodSource("parentDirs")
    void testNonExistentPath(Path parentDir) {
        Path nonExistentPath = createFilePath(parentDir, "testNonExistentPath");
        assertThrows(FileNotFoundException.class, () -> HttpRequest.BodyPublishers.ofFile(nonExistentPath));
    }

    @ParameterizedTest
    @MethodSource("parentDirs")
    void testNonExistentPathAtSubscribe(Path parentDir) throws Exception {

        // Create the publisher
        byte[] fileBytes = ByteBufferUtils.byteArrayOfLength(3);
        Path filePath = createFile(parentDir, "testNonExistentPathAtSubscribe", fileBytes);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofFile(filePath);

        // Delete the file
        Files.delete(filePath);

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, fileBytes.length);

        // Verify the state after `request()`
        subscription.request(1);
        assertEquals("onError", subscriber.invocations.take());
        FileNotFoundException actualException = (FileNotFoundException) subscriber.invocations.take();
        String actualExceptionMessage = actualException.getMessage();
        assertTrue(
                actualExceptionMessage.contains("Not a regular file"),
                "Unexpected message: " + actualExceptionMessage);

    }

    @ParameterizedTest
    @MethodSource("parentDirs")
    void testIrregularFile(Path parentDir) throws Exception {

        // Create the publisher
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofFile(parentDir);

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, Files.size(parentDir));

        // Verify the state after `request()`
        subscription.request(1);
        assertEquals("onError", subscriber.invocations.take());
        FileNotFoundException actualException = (FileNotFoundException) subscriber.invocations.take();
        String actualExceptionMessage = actualException.getMessage();
        assertTrue(
                actualExceptionMessage.contains("Not a regular file"),
                "Unexpected message: " + actualExceptionMessage);

    }

    /**
     * A <em>big enough</em> file length to observe the effects of file
     * modification whilst the file is getting read.
     */
    private static final int BIG_FILE_LENGTH = 8 * 1024 * 1024;  // 8 MiB

    @ParameterizedTest
    @MethodSource("parentDirs")
    void testFileModificationWhileReading(Path parentDir) throws Exception {

        // ZIP file system (sadly?) consumes the entire content at open.
        // Hence, we cannot observe the effect of file modification while reading.
        if (parentDir == ZIP_FS_DIR) {
            return;
        }

        // Create the publisher
        byte[] fileBytes = ByteBufferUtils.byteArrayOfLength(BIG_FILE_LENGTH);
        Path filePath = createFile(parentDir, "testFileModificationWhileReading", fileBytes);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofFile(filePath);

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, fileBytes.length);

        // Verify the state after the 1st `request()`
        subscription.request(1);
        assertEquals("onNext", subscriber.invocations.take());
        ByteBuffer buffer1 = (ByteBuffer) subscriber.invocations.take();
        assertTrue(buffer1.limit() > 0, "unexpected empty buffer");
        List<ByteBuffer> buffers = new ArrayList<>();
        buffers.add(buffer1);

        // Truncate the file
        Files.write(filePath, new byte[0]);

        // Drain emissions until completion, and verify the content
        byte[] readBytes = subscriber.drainToByteArray(subscription, Long.MAX_VALUE, buffers);
        assertTrue(
                readBytes.length < fileBytes.length,
                "was expecting less than the total amount (%s bytes), found: %s".formatted(
                        fileBytes.length, readBytes.length));
        ByteBuffer expectedReadBytes = ByteBuffer.wrap(fileBytes, 0, readBytes.length);
        ByteBufferUtils.assertEquals(expectedReadBytes, ByteBuffer.wrap(readBytes), null);

    }

    static Stream<Arguments> testFileOfLengthParams() {
        return PARENT_DIRS
                .stream()
                .flatMap(parentDir -> Stream
                        .of(0, 1, 2, 3, BIG_FILE_LENGTH)
                        .map(fileLength -> Arguments.of(parentDir, fileLength)));
    }

    @ParameterizedTest
    @MethodSource("testFileOfLengthParams")
    void testFileOfLength(Path parentDir, int fileLength) throws Exception {

        // Create the publisher
        byte[] fileBytes = ByteBufferUtils.byteArrayOfLength(fileLength);
        Path filePath = createFile(parentDir, "testFileOfLength", fileBytes);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofFile(filePath);

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, fileBytes.length);

        // Drain emissions until completion, and verify the received content
        byte[] readBytes = subscriber.drainToByteArray(subscription, Long.MAX_VALUE);
        ByteBufferUtils.assertEquals(fileBytes, readBytes, null);

    }

    /**
     * Initiates tests that depend on a custom-configured JVM.
     */
    public static void main(String[] args) throws Exception {
        if ("testOOM".equals(args[0])) {
            testOOM();
        } else {
            throw new IllegalArgumentException("Unknown arguments: " + List.of(args));
        }
    }

    private static void testOOM() {
        for (Path parentDir : PARENT_DIRS) {
            try {
                testOOM(parentDir);
            } catch (Exception exception) {
                throw new AssertionError("failed for parent directory: " + parentDir, exception);
            }
        }
    }

    private static void testOOM(Path parentDir) throws Exception {

        // Create the publisher
        int fileLength = ByteBufferUtils.findLengthExceedingMaxMemory();
        Path filePath = createFileOfLength(parentDir, "testOOM", fileLength);
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofFile(filePath);

        // Subscribe
        RecordingSubscriber subscriber = new RecordingSubscriber();
        Flow.Subscription subscription = subscriber.verifyAndSubscribe(publisher, fileLength);

        // Drain emissions until completion, and verify the received content length
        final int[] readLength = {0};
        subscriber.drainToAccumulator(subscription, 1, buffer -> readLength[0] += buffer.limit());
        assertEquals(fileLength, readLength[0]);

    }

    private static Path createFileOfLength(Path parentDir, String identifier, int fileLength) throws IOException {
        Path filePath = createFilePath(parentDir, identifier);
        try (OutputStream fileStream = Files.newOutputStream(filePath)) {
            byte[] buffer = ByteBufferUtils.byteArrayOfLength(8192);
            for (int writtenLength = 0; writtenLength < fileLength; writtenLength += buffer.length) {
                int remainingLength = fileLength - writtenLength;
                byte[] effectiveBuffer = remainingLength < buffer.length
                        ? ByteBufferUtils.byteArrayOfLength(remainingLength)
                        : buffer;
                fileStream.write(effectiveBuffer);
            }
        }
        return filePath;
    }

    private static Path createFile(Path parentDir, String identifier, byte[] fileBytes) throws IOException {
        Path filePath = createFilePath(parentDir, identifier);
        Files.write(filePath, fileBytes);
        return filePath;
    }

    private static Path createFilePath(Path parentDir, String identifier) {
        String fileName = identifier.replaceAll("\\W*", "");
        return parentDir.resolve(fileName);
    }

}
