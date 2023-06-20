/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertThrows;

/*
 * @test
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run testng/othervm/timeout=180 TransferTo
 * @bug 8265891
 * @summary Tests Channels.newInputStream.transferTo
 * @key randomness
 */
public class TransferTo extends TransferToBase {

    /*
     * Provides test scenarios, i.e., combinations of input and output streams
     * to be tested.
     */
    @DataProvider
    public static Object[][] streamCombinations() {
        return new Object[][] {
            // should use FileChannel.transferTo(FileChannel)
            {fileChannelInput(), fileChannelOutput()},

            // should use FileChannel.transferTo(FileChannel)
            {fileChannelInput(), fileOutputStream()},

            // should use FileChannel.transferTo(SelectableChannel)
            {fileChannelInput(), selectableChannelOutput()},

            // should use FileChannel.transferTo(WritableByteChannel)
            {fileChannelInput(), writableByteChannelOutput()},

            // should use FileChannel.transferFrom(ReadableByteChannel)
            {readableByteChannelInput(), fileChannelOutput()},

            // should use FileChannel.transferFrom(ReadableByteChannel)
            {readableByteChannelInput(), fileOutputStream()},

            // tests InputStream.transferTo(OutputStream) default case
            {readableByteChannelInput(), defaultOutput()}
        };
    }

    /*
     * Input streams to be tested.
     */
    @DataProvider
    public static Object[][] inputStreamProviders() {
        return new Object[][] {
                {fileChannelInput()},
                {readableByteChannelInput()}
        };
    }

    /*
     * Testing API compliance: input stream must throw NullPointerException
     * when parameter "out" is null.
     */
    @Test(dataProvider = "inputStreamProviders")
    public void testNullPointerException(InputStreamProvider inputStreamProvider) {
        assertNullPointerException(inputStreamProvider);
    }

    /*
     * Testing API compliance: complete content of input stream must be
     * transferred to output stream.
     */
    @Test(dataProvider = "streamCombinations")
    public void testStreamContents(InputStreamProvider inputStreamProvider,
                                   OutputStreamProvider outputStreamProvider) throws IOException {
        assertStreamContents(inputStreamProvider, outputStreamProvider);
    }

    /*
     * Special test whether selectable channel based transfer throws blocking mode exception.
     */
    @Test
    public void testIllegalBlockingMode() throws IOException {
        Pipe pipe = Pipe.open();
        try {
            // testing arbitrary input (here: empty file) to non-blocking
            // selectable output
            try (FileChannel fc = FileChannel.open(Files.createTempFile(CWD, "testIllegalBlockingMode", null));
                 InputStream in = Channels.newInputStream(fc);
                 SelectableChannel sc = pipe.sink().configureBlocking(false);
                 OutputStream out = Channels.newOutputStream((WritableByteChannel) sc)) {

                // IllegalBlockingMode must be thrown when trying to perform
                // a transfer
                assertThrows(IllegalBlockingModeException.class, () -> in.transferTo(out));
            }

            // testing non-blocking selectable input to arbitrary output
            // (here: byte array)
            try (SelectableChannel sc = pipe.source().configureBlocking(false);
                InputStream in = Channels.newInputStream((ReadableByteChannel) sc);
                OutputStream out = new ByteArrayOutputStream()) {

                // IllegalBlockingMode must be thrown when trying to perform
                // a transfer
                assertThrows(IllegalBlockingModeException.class, () -> in.transferTo(out));
            }
        } finally {
            pipe.source().close();
            pipe.sink().close();
        }
    }

    /*
     * Creates a provider for an output stream which does not wrap a channel
     */
    private static OutputStreamProvider defaultOutput() {
        return supplier -> {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            supplier.accept(outputStream::toByteArray);
            return outputStream;
        };
    }

    /*
     * Creates a provider for an input stream which wraps a file channel
     */
    private static InputStreamProvider fileChannelInput() {
        return bytes -> {
            Path path = Files.createTempFile(CWD, "fileChannelInput", null);
            Files.write(path, bytes);
            FileChannel fileChannel = FileChannel.open(path);
            return Channels.newInputStream(fileChannel);
        };
    }

    /*
     * Creates a provider for an output stream which wraps a selectable channel
     */
    private static OutputStreamProvider selectableChannelOutput() {
        return supplier -> {
            Pipe pipe = Pipe.open();
            Future<byte[]> bytes = CompletableFuture.supplyAsync(() -> {
                try {
                    InputStream in = Channels.newInputStream(pipe.source());
                    return in.readAllBytes();
                } catch (IOException e) {
                    throw new AssertionError("Exception while asserting content", e);
                }
            });
            OutputStream out = Channels.newOutputStream(pipe.sink());
            supplier.accept(() -> {
                try {
                    out.close();
                    return bytes.get();
                } catch (IOException | InterruptedException | ExecutionException e) {
                    throw new AssertionError("Exception while asserting content", e);
                }
            });
            return out;
        };
    }

    /*
     * Creates a provider for an output stream that wraps a writable byte channel but is not a file channel
     */
    private static OutputStreamProvider writableByteChannelOutput() {
        return supplier -> {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            supplier.accept(outputStream::toByteArray);
            return Channels.newOutputStream(Channels.newChannel(outputStream));
        };
    }

    /**
     * Returns a provider for a FileOutputStream.
     */
    private static OutputStreamProvider fileOutputStream() {
        return supplier -> {
            Path path = Files.createTempFile(CWD, "fosOutput", null);
            supplier.accept(() -> {
                try {
                    return Files.readAllBytes(path);
                } catch (IOException e) {
                    throw new AssertionError("Failed to verify output file", e);
                }
            });
            return new FileOutputStream(path.toFile());
        };
    }

}
