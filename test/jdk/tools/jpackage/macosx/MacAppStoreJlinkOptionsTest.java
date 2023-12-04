/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.Annotations.Test;

/**
 * Tests generation of app image with --mac-app-store and --jlink-options. jpackage should able
 * to generate app image if "--strip-native-commands" is specified for --jlink-options and should
 * fail if it is not specified.
 */

/*
 * @test
 * @summary jpackage with --mac-app-store and --jlink-options
 * @library ../helpers
 * @library /test/lib
 * @build jdk.jpackage.test.*
 * @build MacAppStoreJLinkOptionsTest
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @requires (os.family == "mac")
 * @run main/othervm/timeout=540 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=MacAppStoreJLinkOptionsTest
 */
public class MacAppStoreJLinkOptionsTest {

    @Test
    public static void testWithStripNativeCommands() throws Exception {
        JPackageCommand cmd = JPackageCommand.helloAppImage();
        cmd.addArguments("--mac-app-store", "--jlink-options",
                "--strip-debug --no-man-pages --no-header-files --strip-native-commands");

        cmd.executeAndAssertHelloAppImageCreated();
    }

    @Test
    public static void testWithoutStripNativeCommands() throws Exception {
        JPackageCommand cmd = JPackageCommand.helloAppImage();
        cmd.addArguments("--mac-app-store", "--jlink-options",
                "--strip-debug --no-man-pages --no-header-files");

        cmd.execute(1);
    }
}
