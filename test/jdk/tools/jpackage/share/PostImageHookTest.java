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

import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageUserScript;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary jpackage with user-supplied post app image script
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror PostImageHookTest.java
 * @run main/othervm/timeout=720 -Xmx512m
 *  jdk.jpackage.test.Main
 *  --jpt-run=PostImageHookTest
 */

public class PostImageHookTest {

    enum Mode {
        APP,
        RUNTIME,
        EXTERNAL_APP_IMAGE
    }

    @Test
    @Parameter("APP")
    @Parameter("RUNTIME")
    @Parameter("EXTERNAL_APP_IMAGE")
    public static void test(Mode mode) {

        final var appImageCmd = JPackageCommand.helloAppImage()
                .setFakeRuntime().setArgumentValue("--dest", TKit.createTempDirectory("appimage"));

        appImageCmd.execute();

        final var test = new PackageTest();
        switch (mode) {
            case APP -> {
                test.configureHelloApp();
                test.addInitializer(cmd -> {
                    cmd.addArguments("--runtime-image", appImageCmd.appRuntimeDirectory());
                });
            }
            case RUNTIME -> {
                test.addInitializer(cmd -> {
                    cmd.removeArgumentWithValue("--input");
                    cmd.addArguments("--runtime-image", appImageCmd.appRuntimeDirectory());
                });
            }
            case EXTERNAL_APP_IMAGE -> {
                test.addInitializer(cmd -> {
                    cmd.removeArgumentWithValue("--input");
                    cmd.addArguments("--app-image", appImageCmd.outputBundle());
                });
            }
        }

        JPackageUserScript.verifyConfigDir(test).run(Action.CREATE);
    }
}
