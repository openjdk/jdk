/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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


import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Comm;
import jdk.jpackage.test.HelloApp;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary jpackage application version testing
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror JLinkOptionsTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=JLinkOptionsTest
 */

public final class JLinkOptionsTest {

    public static Collection<?> input() {
        return List.of(new Object[][]{
            // default but with strip-native-commands removed
            {"Hello", new String[]{
                    "--jlink-options",
                    "--strip-debug --no-man-pages --no-header-files",
                    },
                    // non modular should have everything
                    new String[]{"jdk.jartool", "jdk.unsupported"},
                    null,
                    },

            // multiple jlink-options
            {"com.other/com.other.Hello", new String[]{
                    "--jlink-options",
                    "--strip-debug --no-man-pages --no-header-files",
                    "--jlink-options",
                    "--verbose --bind-services --limit-modules java.smartcardio,jdk.crypto.cryptoki,java.desktop",
                    },
                    // with limit-modules and bind-services should have them in the result
                    new String[]{"java.smartcardio", "jdk.crypto.cryptoki"},
                    null,
                    },

            // bind-services
            {"Hello", new String[]{
                    "--jlink-options",
                    "--bind-services --limit-modules jdk.jartool,jdk.unsupported,java.desktop",
                    },
                    // non modular should have at least the module limits
                    new String[]{"jdk.jartool", "jdk.unsupported"},
                    null,
                    },

            // jlink-options --bind-services
            {"com.other/com.other.Hello", new String[]{
                    "--jlink-options",
                    "--bind-services --limit-modules java.smartcardio,jdk.crypto.cryptoki,java.desktop",
                    },
                    // with bind-services should have some services
                    new String[]{"java.smartcardio", "jdk.crypto.cryptoki"},
                    null,
                    },

            // limit modules
            {"com.other/com.other.Hello", new String[]{
                    "--jlink-options",
                    "--limit-modules java.base,java.datatransfer,java.xml,java.prefs,java.desktop,com.other",
                    },
                    // should have whatever it needs
                    new String[]{"java.base", "com.other"},
                    // should not have whatever it doesn't need
                    new String[]{"jdk.jpackage"},
                    },

            // bind-services and limit-options
            {"com.other/com.other.Hello", new String[]{
                    "--jlink-options",
                    "--bind-services",
                    "--jlink-options",
                    "--limit-modules java.base,java.datatransfer,java.xml,java.prefs,java.desktop,com.other,java.smartcardio",
                    },
                    // with bind-services should have some services
                    new String[]{"java.smartcardio"},
                    // but not limited
                    new String[]{"jdk.crypto.cryptoki"},
                    },

        });
    }

    @Test
    @ParameterSupplier("input")
    public void test(String javaAppDesc, String[] jpackageArgs, String[] required, String[] prohibited) {
        final var cmd = createJPackageCommand(javaAppDesc).addArguments(jpackageArgs);

        cmd.executeAndAssertHelloAppImageCreated();

        List<String> release = cmd.readRuntimeReleaseFile();
        List<String> mods = List.of(release.get(1));
        if (required != null) {
            for (String s : required) {
                TKit.assertTextStream(s).label("mods").apply(mods);
            }
        }
        if (prohibited != null) {
            for (String s : prohibited) {
                TKit.assertTextStream(s).label("mods").negate().apply(mods);
            }
        }
    }

    @Test
    public void testNoBindServicesByDefault() {
        final var defaultModules = getModulesInRuntime("--limit-modules java.smartcardio,jdk.crypto.cryptoki,java.desktop");
        final var modulesWithBindServices = getModulesInRuntime("--bind-services --limit-modules java.smartcardio,jdk.crypto.cryptoki,java.desktop");

        final var moduleComm = Comm.compare(defaultModules, modulesWithBindServices);

        TKit.assertStringListEquals(List.of(), moduleComm.unique1().stream().toList(),
                "Check '--bind-services' option doesn't remove modules");
        // with the limited set of modules, we expect that jdk.crypto.cryptoki be added through --bind-services
        TKit.assertNotEquals("", moduleComm.unique2().stream().sorted().collect(Collectors.joining(",")),
                "Check '--bind-services' option adds modules");
    }

    private final JPackageCommand createJPackageCommand(String javaAppDesc) {
        return JPackageCommand.helloAppImage(javaAppDesc);
    }

    private final Set<String> getModulesInRuntime(String ... jlinkOptions) {
        final var cmd = createJPackageCommand(PRINT_ENV_APP + "*");
        if (jlinkOptions.length != 0) {
            cmd.addArguments("--jlink-options");
            cmd.addArguments(jlinkOptions);
        }

        cmd.executeAndAssertImageCreated();

        final var output = HelloApp.assertApp(cmd.appLauncherPath())
                .saveOutput(true).execute("--print-modules").getFirstLineOfOutput();

        return Stream.of(output.split(",")).collect(Collectors.toSet());
    }

    private static final Path PRINT_ENV_APP = TKit.TEST_SRC_ROOT.resolve("apps/PrintEnv.java");
}
