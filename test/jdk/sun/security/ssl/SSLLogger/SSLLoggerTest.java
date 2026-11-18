/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8361344
 * @summary Verify SSLLogger map entry formatting and throwable logging
 * @modules java.base/sun.security.ssl:+open
 * @run testng/othervm -Djavax.net.debug=all SSLLoggerTest
 */

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.System.Logger.Level;
import java.util.ListResourceBundle;
import java.util.Map;
import java.util.ResourceBundle;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import sun.security.ssl.SSLLogger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class SSLLoggerTest {
    private static final String NL = System.lineSeparator();
    private System.Logger logger;

    private PrintStream originalErr;
    private PrintStream capturedErr;
    private ByteArrayOutputStream capturedBytes;


    @BeforeClass
    public void initializeLogger() throws Exception {
        // access SSLLogger's private console logger
        var field = SSLLogger.class.getDeclaredField("logger");
        field.setAccessible(true);
        logger = (System.Logger) field.get(null);
        assertTrue(logger instanceof SSLLogger, "SSL logger not retrieved");
    }

    @BeforeMethod
    public void setUp() {
        // capture SSLLogger output in a fresh buffer for each test run
        originalErr = System.err;
        capturedBytes = new ByteArrayOutputStream();
        capturedErr = new PrintStream(capturedBytes, true, UTF_8);
        System.setErr(capturedErr);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (originalErr != null) {
            System.setErr(originalErr);
        }
        if (capturedErr != null) {
            capturedErr.close();
        }
    }

    @DataProvider(name = "mapEntries")
    public Object[][] mapEntries() {
        // description, entry value, expected formatted entry
        return new Object[][] {
                { "string", "value", "  \"key\": \"value\"" },
                { "empty string", "", "  \"key\": \"\"" },
                { "empty string array", new String[0],
                        "  \"key\": [" + NL + "        ]" },
                { "multiple strings", new String[] { "one", "two" },
                        "  \"key\": [" + NL
                                + "        \"one\"," + NL
                                + "        \"two\"" + NL + "        ]" },
                { "byte array", new byte[] { 0, 0x0a, (byte) 0xff },
                        "  \"key\": \"000AFF\"" },
                { "byte", (byte) 0x0a, "  \"key\": \"0a\"" },
                { "other object", Integer.valueOf(42), "  \"key\": \"42\"" }
        };
    }

    @DataProvider(name = "logEntries")
    public Object[][] logEntries() {
        ResourceBundle bundle = new ListResourceBundle() {
            @Override
            protected Object[][] getContents() {
                return new Object[][] { { "bundle", "translated message" } };
            }
        };

        // level, bundle, log message, throwable, expected
        return new Object[][] {
                { Level.INFO, null, "info",
                        new RuntimeException("info exception"),
                        "java.lang.RuntimeException: info exception" },
                { Level.WARNING, null, "warning",
                        new IllegalStateException("warning exception"),
                        "java.lang.IllegalStateException: warning exception" },
                { Level.ERROR, null, "inner cause",
                        new RuntimeException("outer exception",
                                new IllegalArgumentException("inner exception")),
                        "Caused by: java.lang.IllegalArgumentException: inner exception" },
                { Level.INFO, bundle, "bundle",
                        new RuntimeException("bundle exception"),
                        "java.lang.RuntimeException: bundle exception" }
        };
    }

    @Test(dataProvider = "mapEntries")
    public void testFormatMapEntry(String name, Object value, String expected) {
        // standard usage via SSLLogger.info calls formatMapEntry
        SSLLogger.info(name, Map.entry("key", value));
        capturedErr.flush();
        String log = capturedBytes.toString(UTF_8);

        // check the message and formatted map entry at the end of the log
        String suffix = "|" + name + " (\n" + expected + "\n)\n";
        assertTrue(log.endsWith(suffix),
                name + ": expected suffix [" + suffix + "]\nActual: [" + log + "]");
    }

    @Test(dataProvider = "logEntries")
    public void testLogThrowable(Level level, ResourceBundle rb,
                                     String message, Throwable thrwbl,
                                     String expected) {
        logger.log(level, rb, message, thrwbl);
        capturedErr.flush();
        String log = capturedBytes.toString(UTF_8);

        assertTrue(log.startsWith("javax.net.ssl|" + level + "|"), log);
        assertTrue(log.contains("|" + message + " (\n"), log);
        assertTrue(log.contains("\"throwable\" : {"), log);
        assertTrue(log.contains(expected),
                    "Expected [" + expected + "] but got [" + log + "]");
    }

    @Test
    public void testThrowableLoggingOff() {
        assertFalse(logger.isLoggable(Level.OFF));
        logger.log(Level.OFF, null, "off",
                new RuntimeException("exception"));
        capturedErr.flush();
        assertEquals(capturedBytes.toString(UTF_8), "",
                "The log call with throwable should not log at Level.OFF");
    }
}
