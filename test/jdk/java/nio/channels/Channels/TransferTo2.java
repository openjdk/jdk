/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.OutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static java.lang.String.format;

/*
 * @test
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run junit/othervm/timeout=180 TransferTo2
 * @bug 8278268
 * @summary Tests FileChannel.transferFrom() optimized case
 * @key randomness
 */
public class TransferTo2 extends TransferToBase {

    /*
     * Provides test scenarios, i.e., combinations of input and output streams
     * to be tested.
     */
    public static Stream<Arguments> streamCombinations() {
        return List.of
            (// tests FileChannel.transferFrom(SelectableChannelOutput) optimized case
             Arguments.of(selectableChannelInput(), fileChannelOutput()),

            // tests FileChannel.transferFrom(ReadableByteChannelInput) optimized case
             Arguments.of(readableByteChannelInput(), fileChannelOutput()))
            .stream();
    }

    /*
     * Input streams to be tested.
     */
    public static Stream<Arguments> inputStreamProviders() {
        return List.of(Arguments.of(selectableChannelInput()),
                       Arguments.of(readableByteChannelInput())).stream();
    }

    /*
     * Testing API compliance: input stream must throw NullPointerException
     * when parameter "out" is null.
     */
    @ParameterizedTest
    @MethodSource("inputStreamProviders")
    public void testNullPointerException(InputStreamProvider inputStreamProvider) {
        assertNullPointerException(inputStreamProvider);
    }

    /*
     * Testing API compliance: complete content of input stream must be
     * transferred to output stream.
     */
    @ParameterizedTest
    @MethodSource("streamCombinations")
    public void testStreamContents(InputStreamProvider inputStreamProvider,
            OutputStreamProvider outputStreamProvider) throws Exception {
        assertStreamContents(inputStreamProvider, outputStreamProvider);
    }

    /*
     * Creates a provider for an input stream which wraps a selectable channel
     */
    private static InputStreamProvider selectableChannelInput() {
        return bytes -> {
            Pipe pipe = Pipe.open();
            new Thread(() -> {
                try (OutputStream os = Channels.newOutputStream(pipe.sink())) {
                    os.write(bytes);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
            return Channels.newInputStream(pipe.source());
        };
    }

}
