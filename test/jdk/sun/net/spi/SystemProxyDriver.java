/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 8305529
 * @summary Verifies that the sun.net.spi.DefaultProxySelector#select(URI) doesn't return a List
 *          with null elements in it
 * @modules java.base/sun.net.spi:+open
 * @library /test/lib
 * @build jdk.test.lib.process.ProcessTools SystemProxyTest
 * @run driver SystemProxyDriver
 */
public class SystemProxyDriver {
    // launches the SystemProxyTest as a separate process and verifies that the test passes
    public static void main(final String[] args) throws Exception {
        final String[] commandArgs = new String[]{
                "--add-opens",
                "java.base/sun.net.spi=ALL-UNNAMED",
                // trigger use of the http_proxy environment variable that we pass when launching
                // this Java program
                "-Djava.net.useSystemProxies=true",
                "SystemProxyTest"
        };
        final ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(commandArgs);
        pb.inheritIO();
        pb.environment().put("http_proxy", "foo://"); // intentionally use a value without host/port
        final Process p = pb.start();
        final int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Test failed, exitCode: " + exitCode);
        }
    }
}
