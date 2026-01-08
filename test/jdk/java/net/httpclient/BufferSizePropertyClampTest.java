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

import jdk.internal.net.http.common.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @bug 8367976
 * @summary Verifies that the `jdk.httpclient.bufsize` system property is
 *          clamped correctly
 *
 * @library /test/lib
 *
 * @comment `-Djdk.httpclient.HttpClient.log=errors` is needed to enable
 *          logging and verify that invalid input gets logged
 * @run junit/othervm
 *      -Djdk.httpclient.HttpClient.log=errors
 *      -Djdk.httpclient.bufsize=-1
 *      BufferSizePropertyClampTest
 * @run junit/othervm
 *      -Djdk.httpclient.HttpClient.log=errors
 *      -Djdk.httpclient.bufsize=0
 *      BufferSizePropertyClampTest
 * @run junit/othervm
 *      -Djdk.httpclient.HttpClient.log=errors
 *      -Djdk.httpclient.bufsize=16385
 *      BufferSizePropertyClampTest
 */

class BufferSizePropertyClampTest {

    /** Anchor to avoid the {@code Logger} instance get GC'ed */
    private static final Logger CLIENT_LOGGER =
            Logger.getLogger("jdk.httpclient.HttpClient");

    private static final List<String> CLIENT_LOGGER_MESSAGES =
            Collections.synchronizedList(new ArrayList<>());

    @BeforeAll
    static void registerLoggerHandler() {
        CLIENT_LOGGER.addHandler(new Handler() {

            @Override
            public void publish(LogRecord record) {
                var message = MessageFormat.format(record.getMessage(), record.getParameters());
                CLIENT_LOGGER_MESSAGES.add(message);
            }

            @Override
            public void flush() {
                // Do nothing
            }

            @Override
            public void close() {
                // Do nothing
            }

        });
    }

    @Test
    void test() {
        assertEquals(16384, Utils.BUFSIZE);
        assertEquals(
                1, CLIENT_LOGGER_MESSAGES.size(),
                "Unexpected number of logger messages: " + CLIENT_LOGGER_MESSAGES);
        var expectedMessage = "ERROR: Property value for jdk.httpclient.bufsize=" +
                System.getProperty("jdk.httpclient.bufsize") +
                " not in [1..16384]: using default=16384";
        assertEquals(expectedMessage, CLIENT_LOGGER_MESSAGES.getFirst().replaceAll(",", ""));
    }

}
