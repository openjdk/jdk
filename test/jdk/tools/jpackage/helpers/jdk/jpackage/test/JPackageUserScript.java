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
        this.suffix = suffix;
    }

    public void create(JPackageCommand cmd, List<String> script) {
        final var scriptPath = Path.of(cmd.getArgumentValue("--resource-dir"),
                String.format("%s-%s.wsf", cmd.name(), suffix));
        try {
            if (TKit.isWindows()) {
                XmlUtils.createXml(scriptPath, xml -> {
                    xml.writeStartElement("job");
                    xml.writeAttribute("id", "main");
                    xml.writeStartElement("script");
                    xml.writeAttribute("language", "JScript");
                    xml.writeCData(String.join("\n", script));
                    xml.writeEndElement();
                    xml.writeEndElement();
                });
            } else {
                Files.write(scriptPath, script);
            }
            TKit.traceFileContents(scriptPath, String.format("[%s] script", name()));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static PackageTest verifyConfigDir(PackageTest test) {

        final Map<PackageType, Path> capturedConfigDirs = new HashMap<>();

        return test.addInitializer(cmd -> {
            setupDirectory(cmd, "temp", "--temp");
            setupDirectory(cmd, "resources", "--resource-dir");

            final var configDirContets = TKit.createTempFile(addPkgTypeSuffix("config-listing", cmd) + ".txt").toAbsolutePath();

            capturedConfigDirs.put(cmd.packageType(), configDirContets);

            if (TKit.isWindows()) {
                POST_IMAGE.create(cmd, List.of(
                        "function listDir (dir) {",
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
                        "o.WriteLine(configDir.Path)",
                        "listDir(configDir)",
                        "o.Close()"
                ));
            } else {
                POST_IMAGE.create(cmd, List.of(
                        "!#/bin/sh",
                        "set -ex",
                        String.format("printf %s \"${0%/*}\" > '%s'", configDirContets),
                        String.format("find ${0%/*} >> '%s'", configDirContets)
                ));
            }
        }).addBundleVerifier(cmd -> {
            final var configDirContetsFile = capturedConfigDirs.get(cmd.packageType());
            TKit.trace(String.format("Process [%s] file...", configDirContetsFile));

            final var data = Files.readAllLines(configDirContetsFile);
            final var configDir = Path.of(data.getFirst());
            final var capturedConfigDirContents = data.stream().skip(1).map(Path::of).map(configDir::relativize).toList();

            // Verify new files are not created in the "config" directory after the script execution.
            TKit.assertDirectoryContent(configDir).removeAll(capturedConfigDirContents).match();
        });
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
