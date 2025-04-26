/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.XmlUtils;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Parameters;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;

/*
 * @test usage of scripts from resource dir
 * @summary jpackage with
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @requires (os.family == "windows")
 * @compile -Xlint:all -Werror WinScriptTest.java
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=WinScriptTest
 */

public class WinScriptTest {

    @Test
    public static void test8355651() {
        new PackageTest()
                .configureHelloApp()
                .addInitializer(JPackageCommand::setFakeRuntime)
                .addInitializer(cmd -> {
                    final var expectedConfigFiles = Stream.concat(
                            Stream.of("bundle.wxf", "ui.wxf", "overrides.wxi", "main.wxs"),
                            Stream.of("de", "en", "ja", "zh_CN").map(loc -> {
                                return "MsiInstallerStrings_" + loc + ".wxl";
                            })
                    );

                    final var resourceDir = TKit.createTempDirectory("resources");
                    cmd.addArguments("--resource-dir", resourceDir);

                    createScript(cmd, "post-image", Stream.concat(
                            Stream.of(
                                    "var fs = new ActiveXObject('Scripting.FileSystemObject')",
                                    "var configDir = fs.GetParentFolderName(WScript.ScriptFullName)"
                            ),
                            expectedConfigFiles.map(str -> {
                                return String.format("WScript.Echo('Probe: ' + configDir + '\\\\%s'); fs.GetFile(configDir + '\\\\%s')", str, str);
                            })
                    ).toList());
                }).run(Action.CREATE);
    }

    @Parameters
    public static List<Object[]> test() {
        final List<Object[]> data = new ArrayList<>();
        for (final var type : PackageType.WINDOWS) {
            for (final var exitCode : List.of(0, 10)) {
                data.add(new Object[] {type, exitCode});
            }
        }
        return data;
    }

    @Test
    @ParameterSupplier
    public static void test(PackageType packageType, int wsfExitCode) throws IOException {

        final var test = new PackageTest()
                .forTypes(packageType)
                .configureHelloApp()
                .addInitializer(cmd -> {
                    cmd.setFakeRuntime().saveConsoleOutput(true);
                });

        final ScriptData appImageScriptData;
        if (wsfExitCode != 0 && packageType == PackageType.WIN_EXE) {
            appImageScriptData = new ScriptData(PackageType.WIN_MSI, 0);
        } else {
            appImageScriptData = new ScriptData(PackageType.WIN_MSI, wsfExitCode);
        }

        final ScriptData msiScriptData = new ScriptData(PackageType.WIN_EXE, wsfExitCode);

        test.setExpectedExitCode(wsfExitCode == 0 ? 0 : 1);

        final Path tempDir = TKit.createTempDirectory("resources");

        test.addInitializer(cmd -> {
            cmd.addArguments("--resource-dir", tempDir);

            appImageScriptData.createScript(cmd);
            msiScriptData.createScript(cmd);
        });

        switch (packageType) {
            case WIN_MSI:
                test.addBundleVerifier((cmd, result) -> {
                    appImageScriptData.assertJPackageOutput(result.getOutput());
                });
                break;

            case WIN_EXE:
                test.addBundleVerifier((cmd, result) -> {
                    appImageScriptData.assertJPackageOutput(result.getOutput());
                    msiScriptData.assertJPackageOutput(result.getOutput());
                });
                break;

            default:
                throw new UnsupportedOperationException();
        }

        test.run(Action.CREATE);
    }

    private static class ScriptData {
        ScriptData(PackageType scriptType, int wsfExitCode) {
            if (scriptType == PackageType.WIN_MSI) {
                echoText = "post app image wsf";
                envVarName = "JpAppImageDir";
                scriptSuffixName = "post-image";
            } else {
                echoText = "post msi wsf";
                envVarName = "JpMsiFile";
                scriptSuffixName = "post-msi";
            }
            this.wsfExitCode = wsfExitCode;
        }

        void assertJPackageOutput(List<String> output) {
            TKit.assertTextStream(String.format("    jp: %s", echoText))
                    .predicate(String::equals)
                    .apply(output);

            String cwdPattern = String.format("    jp: CWD(%s)=", envVarName);
            TKit.assertTextStream(cwdPattern)
                    .predicate(String::startsWith)
                    .apply(output);
            String cwd = output.stream().filter(line -> line.startsWith(
                    cwdPattern)).findFirst().get().substring(cwdPattern.length());

            String envVarPattern = String.format("    jp: %s=", envVarName);
            TKit.assertTextStream(envVarPattern)
                    .predicate(String::startsWith)
                    .apply(output);
            String envVar = output.stream().filter(line -> line.startsWith(
                    envVarPattern)).findFirst().get().substring(envVarPattern.length());

            TKit.assertTrue(envVar.startsWith(cwd), String.format(
                    "Check value of %s environment variable [%s] starts with the current directory [%s] set for %s script",
                    envVarName, envVar, cwd, echoText));
        }

        void createScript(JPackageCommand cmd) throws IOException {
            WinScriptTest.createScript(cmd, scriptSuffixName, List.of(
                    "var shell = new ActiveXObject('WScript.Shell')",
                    "WScript.Echo('jp: " + envVarName + "=' + shell.ExpandEnvironmentStrings('%" + envVarName + "%'))",
                    "WScript.Echo('jp: CWD(" + envVarName + ")=' + shell.CurrentDirectory)",
                    String.format("WScript.Echo('jp: %s')", echoText),
                    String.format("WScript.Quit(%d)", wsfExitCode)
            ));
        }

        private final int wsfExitCode;
        private final String scriptSuffixName;
        private final String echoText;
        private final String envVarName;
    }

    private static void createScript(JPackageCommand cmd, String scriptSuffixName, List<String> js) throws IOException {
        XmlUtils.createXml(Path.of(cmd.getArgumentValue("--resource-dir"),
                String.format("%s-%s.wsf", cmd.name(), scriptSuffixName)), xml -> {
            xml.writeStartElement("job");
            xml.writeAttribute("id", "main");
            xml.writeStartElement("script");
            xml.writeAttribute("language", "JScript");
            xml.writeCData(String.join("\n", js));
            xml.writeEndElement();
            xml.writeEndElement();
        });
    }
}
