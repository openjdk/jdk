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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;

import static java.nio.file.StandardOpenOption.*;

import org.testng.annotations.Test;

/*
 * @test
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run testng/othervm/timeout=180 TransferTo_2GB_transferFrom
 * @bug 8278268
 * @summary Tests if ChannelInputStream.transferFrom correctly
 *     transfers 2GB+ using FileChannel.transferFrom(ReadableByteChannel).
 * @key randomness
 */
public class TransferTo_2GB_transferFrom extends TransferToBase {

    /*
     * Special test for stream-to-file transfer of more than 2 GB. This test
     * covers multiple iterations of FileChannel.transferFrom(ReadableByteChannel),
     * which ChannelInputStream.transferFrom() only applies in this particular
     * case, and cannot get tested using a single byte[] due to size limitation
     * of arrays.
     */
    @Test
    public void testMoreThanTwoGB() throws IOException {
        testMoreThanTwoGB("From",
                (sourceFile, targetFile) -> {
                    try {
                        return Channels.newInputStream(
                            Channels.newChannel(new BufferedInputStream(Files.newInputStream(sourceFile))));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                (sourceFile, targetFile) -> {
                    try {
                        return Channels.newOutputStream(FileChannel.open(targetFile, WRITE));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                },
                (inputStream, outputStream) -> {
                    try {
                        return inputStream.transferTo(outputStream);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
        );
    }

}
