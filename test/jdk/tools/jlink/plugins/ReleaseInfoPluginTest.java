/*
 * Copyright (c) 2025, IBM Corporation. All rights reserved.
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

import jdk.test.lib.process.*;
import jdk.test.lib.Asserts;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Properties;

import jdk.tools.jlink.internal.LinkableRuntimeImage;

import tests.Helper;

/* @test
 * @bug 8372155
 * @summary Test the --release-info <file> plugin option
 * @library ../../lib
 * @library /test/lib
 * @modules jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jimage
 *          java.base/jdk.internal.jimage
 * @build tests.*
 * @run main/othervm ReleaseInfoPluginTest
 */

public class ReleaseInfoPluginTest {

    private static final String NON_ASCII = "ößäakl vendor oy";
    private static final String IMPL = "IMPLEMENTOR";
    private static final boolean LINKABLE_RUNTIME = LinkableRuntimeImage.isLinkableRuntime();

    public static void main(String[] args) throws Throwable {

        Helper helper = Helper.newHelper(LINKABLE_RUNTIME);
        if (helper == null) {
            System.err.println("Test not run");
            return;
        }
        var utf8File = Path.of("release-info-utf-8.txt");
        Files.writeString(utf8File, IMPL + "=\"" + NON_ASCII + "\"", StandardCharsets.UTF_8);
        Path image = helper.generateDefaultImage(
                             new String[] {
                               "--release-info", utf8File.toString()
                             }, "java.base").assertSuccess();

        // release file produced should have IMPLEMENTOR in
        // UTF-8 encoding
        Path release = image.resolve("release");
        Properties props = new Properties();
        try (Reader reader= Files.newBufferedReader(release)) {
            props.load(reader); // Load as UTF-8
        }
        String noQuotesMods = ((String)props.get("MODULES")).replace("\"", "");
        Asserts.assertEquals("java.base", noQuotesMods);
        String noQuotesActual = ((String)props.get(IMPL)).replace("\"", "");
        Asserts.assertEquals(NON_ASCII, noQuotesActual);
    }

}
