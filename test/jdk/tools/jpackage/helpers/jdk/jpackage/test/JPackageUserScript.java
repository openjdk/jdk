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
package jdk.jpackage.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jdk.jpackage.internal.util.XmlUtils;

public enum JPackageUserScript {
    POST_IMAGE("post-image"),
    POST_MSI("post-msi");

    JPackageUserScript(String suffix) {
        if (TKit.isWindows()) {
            this.suffix = suffix + ".wsf";
        } else {
            this.suffix = suffix + ".sh";
        }
    }

    public void create(JPackageCommand cmd, List<String> script) {
        create(scriptPath(cmd), script);
    }

    private void create(Path scriptFilePath, List<String> script) {
        try {
            if (TKit.isWindows()) {
                XmlUtils.createXml(scriptFilePath, xml -> {
                    xml.writeStartElement("job");
                    xml.writeAttribute("id", "main");
                    xml.writeStartElement("script");
                    xml.writeAttribute("language", "JScript");
                    xml.writeCData("\n" + String.join("\n", script) + "\n");
                    xml.writeEndElement();
                    xml.writeEndElement();
                });
            } else {
                Files.write(scriptFilePath, script);
            }
            TKit.traceFileContents(scriptFilePath, String.format("[%s] script", name()));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private Path scriptPath(JPackageCommand cmd) {
        return Path.of(cmd.getArgumentValue("--resource-dir"), String.format("%s-%s", cmd.name(), suffix));
    }

    public static PackageTest verifyDirectories(PackageTest test) {

        final Map<PackageType, Path> capturedConfigDirs = new HashMap<>();
        final Map<PackageType, Path> capturedAppImageDirs = new HashMap<>();

        return test.addInitializer(cmd -> {
            setupDirectory(cmd, "temp", "--temp");
            setupDirectory(cmd, "resources", "--resource-dir");

            final var configDirContets = TKit.createTempFile(addPkgTypeSuffix("config-listing", cmd) + ".txt").toAbsolutePath();
            capturedConfigDirs.put(cmd.packageType(), configDirContets);

            final var appImageDirContets = TKit.createTempFile(addPkgTypeSuffix("app-image-listing", cmd) + ".txt").toAbsolutePath();
            capturedAppImageDirs.put(cmd.packageType(), appImageDirContets);

            if (TKit.isWindows()) {
                POST_IMAGE.create(cmd, List.of(
                        "function listDir (dir) {",
                        "    o.WriteLine(dir.Path)",
                        "    for(var e = new Enumerator(dir.Files); !e.atEnd(); e.moveNext()) {",
                        "        o.WriteLine(e.item().Path)",
                        "    }",
                        "    for(var e = new Enumerator(dir.SubFolders); !e.atEnd(); e.moveNext()) {",
                        "        listDir(e.item())",
                        "    }",
                        "}",
                        "var fs = new ActiveXObject('Scripting.FileSystemObject')",
                        String.format("var o = fs.CreateTextFile('%s', true)", configDirContets.toString().replace('\\', '/')),
                        "var configDir = fs.GetFolder(fs.GetParentFolderName(WScript.ScriptFullName))",
                        "listDir(configDir)",
                        "o.Close()",
                        "var shell = new ActiveXObject('WScript.Shell')",
                        String.format("o = fs.CreateTextFile('%s', true)", appImageDirContets.toString().replace('\\', '/')),
                        "var appImageDir = fs.GetFolder(shell.CurrentDirectory)",
                        "o.WriteLine(appImageDir.Path)",
                        "listDir(appImageDir)",
                        "o.Close()"
                ));
            } else {
                POST_IMAGE.create(cmd, List.of(
                        "set -e",
                        String.format("find \"${0%%/*}\" >> '%s'", configDirContets),
                        String.format("pwd > '%s'", appImageDirContets),
                        String.format("find \"$PWD\" >> '%s'", appImageDirContets)
                ));
            }
        }).addBundleVerifier(cmd -> {
            verifyDirectoryContents(capturedConfigDirs.get(cmd.packageType()));
            verifyDirectoryContents(capturedAppImageDirs.get(cmd.packageType()));
        });
    }

    private static void verifyDirectoryContents(Path fileWithExpectedDirContents) throws IOException {
        TKit.trace(String.format("Process [%s] file...", fileWithExpectedDirContents));

        final var data = Files.readAllLines(fileWithExpectedDirContents);
        final var dir = Path.of(data.getFirst());
        final var capturedDirContents = data.stream().skip(1).map(Path::of).map(dir::relativize).toList();

        // Verify new files are not created in the "config" directory after the script execution.
        TKit.assertDirectoryContent(dir).removeAll(capturedDirContents).match();
    }

    private static Path setupDirectory(JPackageCommand cmd, String role, String argName) {
        if (!cmd.hasArgument(argName)) {
            cmd.setArgumentValue(argName, TKit.createTempDirectory(addPkgTypeSuffix(role, cmd)));
        }

        return Path.of(cmd.getArgumentValue(argName));
    }

    private static String addPkgTypeSuffix(String str, JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.NATIVE);
        Objects.requireNonNull(str);
        return String.format("%s-%s", str, cmd.packageType().getType());
    }

    private final String suffix;
}
