/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @test
 * @bug 8341975 8351435
 * @summary Tests the default charset. It should honor `stdout.encoding`
 *          which should be the same as System.out.charset()
 * @modules jdk.internal.le
 * @run junit/othervm -Djdk.console=jdk.internal.le -Dstdout.encoding=UTF-8 DefaultCharsetTest
 * @run junit/othervm -Djdk.console=jdk.internal.le -Dstdout.encoding=ISO-8859-1 DefaultCharsetTest
 * @run junit/othervm -Djdk.console=jdk.internal.le -Dstdout.encoding=US-ASCII DefaultCharsetTest
 * @run junit/othervm -Djdk.console=jdk.internal.le -Dstdout.encoding=foo DefaultCharsetTest
 * @run junit/othervm -Djdk.console=jdk.internal.le DefaultCharsetTest
 */
public class DefaultCharsetTest {
    @Test
    public void testDefaultCharset() {
        var stdoutEncoding = System.getProperty("stdout.encoding");
        var sysoutCharset = System.out.charset();
        var consoleCharset = System.console().charset();
        System.out.println("""
                    stdout.encoding = %s
                    System.out.charset() = %s
                    System.console().charset() = %s
                """.formatted(stdoutEncoding, sysoutCharset.name(), consoleCharset.name()));
        assertEquals(consoleCharset, sysoutCharset,
            "Charsets for System.out and Console differ for stdout.encoding: %s".formatted(stdoutEncoding));
    }
}
