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

import java.io.OutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.lang.String.format;

/*
 * @test
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run testng/othervm/timeout=180 TransferTo2
 * @bug 8278268
 * @summary Tests FileChannel.transferFrom() optimized case
 * @key randomness
 */
public class TransferTo2 extends TransferToBase {

    /*
     * Provides test scenarios, i.e., combinations of input and output streams
     * to be tested.
     */
    @DataProvider
    public static Object[][] streamCombinations() {
        return new Object[][] {
            // tests FileChannel.transferFrom(SelectableChannelOutput) optimized case
            {selectableChannelInput(), fileChannelOutput()},

            // tests FileChannel.transferFrom(ReadableByteChannelInput) optimized case
            {readableByteChannelInput(), fileChannelOutput()},
        };
    }

    /*
     * Input streams to be tested.
     */
    @DataProvider
    public static Object[][] inputStreamProviders() {
        return new Object[][] {
            {selectableChannelInput()},
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
