/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;
import static jdk.jpackage.test.mock.CommandMock.ioerror;
import static jdk.jpackage.test.mock.CommandMock.succeed;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.cli.StandardBundlingOperation;
import jdk.jpackage.internal.model.AppImagePackageType;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.StandardPackageType;
import jdk.jpackage.test.Annotations;
import jdk.jpackage.test.HelloApp;
import jdk.jpackage.test.JUnitAdapter;
import jdk.jpackage.test.JavaAppDesc;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMock;
import jdk.jpackage.test.mock.CommandMockExit;
import jdk.jpackage.test.mock.Script;
import org.junit.jupiter.api.Test;


public class DefaultBundlingEnvironmentTest extends JUnitAdapter {

    @Test
    void testDefaultBundlingOperation() {

        var executed = new int[1];

        var descriptor = new BundlingOperationDescriptor(OperatingSystem.current(), "foo", "build");

        var env = new DefaultBundlingEnvironment(DefaultBundlingEnvironment.build().defaultOperation(() -> {
            executed[0] = executed[0] + 1;
            return Optional.of(descriptor);
        }));

        // Assert the default bundling operation supplier is not called in the ctor.
        assertEquals(0, executed[0]);

        // Assert the default bundling operation is as expected.
        assertEquals(descriptor, env.defaultOperation().orElseThrow());
        assertEquals(1, executed[0]);

        // Assert the default bundling operation supplier is called only once.
        assertEquals(descriptor, env.defaultOperation().orElseThrow());
        assertEquals(1, executed[0]);
    }

    /**
     * Tests that commands executed to initialize the system environment are
     * executed only once.
     * @throws IOException
     */
    @Annotations.Test
    @Annotations.ParameterSupplier
    public void testInitializedOnce(StandardBundlingOperation op) throws IOException {

        List<List<String>> executedCommands = Collections.synchronizedList(new ArrayList<>());

        var script = createMockScript(op);

        ToolProvider jpackage = MockUtils.buildJPackage()
                .os(op.os())
                .script(script)
                .listener(executedCommands::add).create();

        var inputDir = TKit.createTempDirectory("input");
        var appDesc = JavaAppDesc.parse(null);
        HelloApp.createBundle(appDesc, inputDir);

        //
        // The command line should fail as the main class name is not specified and it is not set in the main jar.
        //
        // Run native packaging twice.
        // It can execute commands required to configure the system environment in the first iteration.
        // It must not execute a single command in the second iteration.
        //
        // Run app image packaging once.
        // It must not execute a single command because app image packaging should not require native commands (Unless
        // it is macOS where it will sign the app image with an ad hoc signature
        // using the codesign tool. But: #1 - it is not a variable part of the system environment;
        // #2 - jpackage should bail out earlier).
        //

        final var type = op.packageTypeValue();
        final int iterationCount;
        if (op.packageType() instanceof AppImagePackageType) {
            iterationCount = 1;
        } else {
            iterationCount = 2;
        }

        for (var i = 0; i != iterationCount; i++) {
            var result = new Executor().toolProvider(jpackage).saveOutput().args(
                    "--type=" + type,
                    "--input", inputDir.toString(),
                    "--main-jar", appDesc.jarFileName()).execute();

            assertEquals(1, result.getExitCode());

            // Assert it bailed out with the expected error.
            assertEquals(List.of(
                    I18N.format("message.error-header", I18N.format("error.no-main-class-with-main-jar", appDesc.jarFileName())),
                    I18N.format("message.advice-header", I18N.format("error.no-main-class-with-main-jar.advice", appDesc.jarFileName()))
            ), result.stderr());

            TKit.trace("The list of executed commands:");
            executedCommands.forEach(cmdline -> {
                TKit.trace("  " + cmdline);
            });
            TKit.trace("Done");

            if (i == 0) {
                executedCommands.clear();
            }
        }

        assertEquals(List.of(), executedCommands);
        assertEquals(List.of(), script.incompleteMocks());
    }

    public static List<Object[]> testInitializedOnce() {
        return StandardBundlingOperation.ofPlatform(OperatingSystem.current())
                .filter(StandardBundlingOperation::isCreateBundle).map(v -> {
                    return new Object[] {v};
                }).toList();
    }

    private static Script createMockScript(StandardBundlingOperation op) {

        if (op.packageType() instanceof AppImagePackageType) {
            return Script.build().createSequence();
        }

        switch (op.os()) {
            case WINDOWS -> {
                return createWinMockScript();
            }
            case LINUX -> {
                return createLinuxMockScript(op.packageType());
            }
            case MACOS -> {
                return createMacMockScript();
            }
            default -> {
                throw new AssertionError();
            }
        }
    }

    private static Script createWinMockScript() {

        // Make "candle.exe" and "light.exe" always fail.
        var candle = ioerror("candle-mock");
        var light = ioerror("light-mock");

        // Make the "wix.exe" functional.
        var wix = CommandActionSpecs.build()
                .stdout("5.0.2+aa65968c")
                .exit(CommandMockExit.SUCCEED)
                .toCommandMockBuilder().name("wix-mock").create();

        var script = Script.build()
                .map(Script.cmdlineStartsWith("candle.exe"), candle)
                .map(Script.cmdlineStartsWith("light.exe"), light)
                .map(Script.cmdlineStartsWith("wix.exe"), wix)
                .createLoop();

        return script;
    }

    private static Script createMacMockScript() {

        @SuppressWarnings("unchecked")
        var setfilePaths = (List<Path>)toSupplier(() -> {
            return Class.forName(String.join(".",
                    DefaultBundlingEnvironmentTest.class.getPackageName(),
                    "MacDmgSystemEnvironment"
            )).getDeclaredField("SETFILE_KNOWN_PATHS").get(null);
        }).get();

        var script = Script.build();

        for (var setfilePath: setfilePaths) {
            script.map(Script.cmdlineStartsWith(setfilePath), ioerror(setfilePath.toString() + "-mock"));
        }

        script.map(Script.cmdlineStartsWith("/usr/bin/xcrun"), succeed("/usr/bin/xcrun-mock"));

        return script.createLoop();
    }

    private static Script createLinuxMockScript(PackageType pkgType) {

        final Map<String, CommandMock> mocks = new HashMap<>();

        var script = Script.build();

        final Set<String> debCommandNames = Set.of("dpkg", "dpkg-deb", "fakeroot");
        final Set<String> rpmCommandNames = Set.of("rpm", "rpmbuild");

        final Set<String> succeedCommandNames;
        switch (pkgType) {
            case StandardPackageType.LINUX_DEB -> {
                succeedCommandNames = debCommandNames;
                // Simulate "dpkg --print-architecture".
                var dpkg = CommandActionSpecs.build()
                        .stdout("foo-arch")
                        .exit(CommandMockExit.SUCCEED)
                        .toCommandMockBuilder().name("dpkg-mock").create();
                mocks.put("dpkg", dpkg);
            }
            case StandardPackageType.LINUX_RPM -> {
                succeedCommandNames = rpmCommandNames;
                // Simulate "rpmbuild --version" prints the minimal acceptable version.
                var rpmbuild = CommandActionSpecs.build()
                        .stdout("RPM version 4.10")
                        .exit(CommandMockExit.SUCCEED)
                        .toCommandMockBuilder().name("rpmbuild-mock").create();
                mocks.put("rpmbuild", rpmbuild);
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }

        script.map(Script.cmdlineStartsWith("ldd"), succeed("ldd-mock"));

        for (var commandName : succeedCommandNames) {
            if (!mocks.containsKey(commandName)) {
                mocks.put(commandName, succeed(commandName + "-mock"));
            }
        }

        Stream.of(debCommandNames, rpmCommandNames).flatMap(Set::stream).forEach(commandName -> {
            var mock = Optional.ofNullable(mocks.get(commandName)).orElseGet(() -> {
                return ioerror(commandName + "-mock");
            });
            script.map(Script.cmdlineStartsWith(commandName), mock);
        });

        return script.createLoop();
    }
}
