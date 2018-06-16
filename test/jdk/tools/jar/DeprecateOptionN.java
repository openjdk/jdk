/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8199871
 * @modules jdk.jartool
 * @summary jar -n should print out deprecation warning
 * @run testng DeprecateOptionN
 */

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.spi.ToolProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class DeprecateOptionN {
    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
            .orElseThrow(() ->
                    new RuntimeException("jar tool not found")
            );

    protected static String jar(String... options) {
        StringWriter writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);

        JAR_TOOL.run(pw, pw, options);
        String output = writer.toString();
        System.err.println(output);
        return output;
    }

    @Test
    public void helpCompatWithWarning() {
        String output = jar("--help:compat");
        assertTrue(output.contains("this option is deprecated, and is planned for removal in a future JDK release"));
    }

    @Test
    public void helpExtraWithWarning() {
        String output = jar("--help-extra");
        assertTrue(output.contains("This option is deprecated, and is"));
        assertTrue(output.contains("planned for removal in a future JDK release"));
    }

    @Test
    public void normalizeWithWarning() throws IOException {
        File tmp = File.createTempFile("test", null);
        String output = jar("cnf", "test.jar", tmp.getAbsolutePath());
        tmp.delete();
        assertTrue(output.contains("Warning: The -n option is deprecated, and is planned for removal in a future JDK release"));
    }

    @Test
    public void NoWarningWithoutN() throws IOException {
        File tmp = File.createTempFile("test", null);
        String output = jar("cf", "test.jar", tmp.getAbsolutePath());
        tmp.delete();
        assertFalse(output.contains("Warning: The -n option is deprecated, and is planned for removal in a future JDK release"));
    }


    @Test
    public void SuppressWarning() throws IOException {
        File tmp = File.createTempFile("test", null);
        String output = jar("-c", "-n", "-XDsuppress-tool-removal-message",
                "-f", "test.jar", tmp.getAbsolutePath());
        tmp.delete();
        assertFalse(output.contains("Warning: The -n option is deprecated, and is planned for removal in a future JDK release"));
    }
}
