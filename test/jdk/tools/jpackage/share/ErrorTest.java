/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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


import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.RunnablePackageTest;
import jdk.jpackage.test.TKit;
import static jdk.internal.util.OperatingSystem.WINDOWS;

/*
 * @test
 * @summary Test jpackage output for erroneous input
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile ErrorTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=ErrorTest
 *  --jpt-before-run=jdk.jpackage.test.JPackageCommand.useExecutableByDefault
 */

/*
 * @test
 * @summary Test jpackage output for erroneous input
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile ErrorTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=ErrorTest
 *  --jpt-before-run=jdk.jpackage.test.JPackageCommand.useToolProviderByDefault
 */

public final class ErrorTest {

    public static Collection input() {
        return List.of(new Object[][]{
            // non-existent arg
            {"Hello",
                    new String[]{"--no-such-argument"},
                    null,
                    JPackageStringBundle.MAIN.cannedFormattedString("ERR_InvalidOption", "--no-such-argument")},
            // no main jar
            {"Hello",
                    null,
                    new String[]{"--main-jar"},
                    JPackageStringBundle.MAIN.cannedFormattedString("ERR_NoEntryPoint")},
            // no main-class
            {"Hello",
                    null,
                    new String[]{"--main-class"},
                    JPackageStringBundle.MAIN.cannedFormattedString("error.no-main-class-with-main-jar", "hello.jar"),
                    JPackageStringBundle.MAIN.cannedFormattedString("error.no-main-class-with-main-jar.advice", "hello.jar")},
            // non-existent main jar
            {"Hello",
                    new String[]{"--main-jar", "non-existent.jar"},
                    null,
                    JPackageStringBundle.MAIN.cannedFormattedString("error.main-jar-does-not-exist", "non-existent.jar")},
            // non-existent runtime
            {"Hello",
                    new String[]{"--runtime-image", "non-existent.runtime"},
                    null,
                    JPackageStringBundle.MAIN.cannedFormattedString("message.runtime-image-dir-does-not-exist", "runtime-image", "non-existent.runtime")},
            // non-existent resource-dir
            {"Hello",
                    new String[]{"--resource-dir", "non-existent.dir"},
                    null,
                    JPackageStringBundle.MAIN.cannedFormattedString("message.resource-dir-does-not-exist", "resource-dir", "non-existent.dir")},
            // invalid type
            {"Hello",
                    new String[]{"--type", "invalid-type"},
                    null,
                    JPackageStringBundle.MAIN.cannedFormattedString("ERR_InvalidInstallerType", "invalid-type")},
            // no --input
            {"Hello",
                    null,
                    new String[]{"--input"},
                    JPackageStringBundle.MAIN.cannedFormattedString("ERR_MissingArgument", "--input")},
            // no --module-path
            {"com.other/com.other.Hello",
                    null,
                    new String[]{"--module-path"},
                    JPackageStringBundle.MAIN.cannedFormattedString("ERR_MissingArgument", "--runtime-image or --module-path")},
        });
    }

    @Test
    @ParameterSupplier("input")
    public static void test(String javaAppDesc, String[] jpackageArgs,
            String[] removeArgs, CannedFormattedString... expectedErrors) {
        // Init default jpackage test command line.
        var cmd = JPackageCommand.helloAppImage(javaAppDesc);

        defaultInit(cmd, expectedErrors);

        // Add arguments if requested.
        Optional.ofNullable(jpackageArgs).ifPresent(cmd::addArguments);

        // Remove arguments if requested.
        Optional.ofNullable(removeArgs).map(List::of).ifPresent(
                args -> args.forEach(cmd::removeArgumentWithValue));

        cmd.execute(1);
    }

    @Test(ifOS = WINDOWS)
    public static void testWinService() {

        CannedFormattedString[] expectedErrors = new CannedFormattedString[] {
            JPackageStringBundle.MAIN.cannedFormattedString("error.missing-service-installer"),
            JPackageStringBundle.MAIN.cannedFormattedString("error.missing-service-installer.advice")
        };

        new PackageTest().configureHelloApp()
                .addInitializer(cmd -> {
                    defaultInit(cmd, expectedErrors);
                    cmd.addArgument("--launcher-as-service");
                })
                .setExpectedExitCode(1)
                .run(RunnablePackageTest.Action.CREATE);
    }

    private static void defaultInit(JPackageCommand cmd, CannedFormattedString... expectedErrors) {

        // Disable default logic adding `--verbose` option
        // to jpackage command line.
        // It will affect jpackage error messages if the command line is malformed.
        cmd.ignoreDefaultVerbose(true);

        // Ignore external runtime as it will interfer
        // with jpackage arguments in this test.
        cmd.ignoreDefaultRuntime(true);

        // Configure jpackage output verifier to look up the list of provided
        // errors in the order they are specified.
        cmd.validateOutput(Stream.of(expectedErrors)
                .map(CannedFormattedString::getValue)
                .map(TKit::assertTextStream)
                .reduce(TKit.TextStreamVerifier::andThen).get());
    }
}
