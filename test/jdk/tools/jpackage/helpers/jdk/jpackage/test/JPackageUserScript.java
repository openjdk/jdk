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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.XmlUtils;

public enum JPackageUserScript {
    POST_IMAGE("post-image"),
    POST_MSI("post-msi");

    JPackageUserScript(String suffix) {
        this.suffix = suffix + scriptFilenameExtension();
    }

    static String scriptFilenameExtension() {
        if (TKit.isWindows()) {
            return ".wsf";
        } else {
            return ".sh";
        }
    }

    public enum WinGlobals {
        JS_SHELL("var shell = new ActiveXObject('WScript.Shell')"),
        JS_FS("var fs = new ActiveXObject('Scripting.FileSystemObject')"),
        JS_LIST_DIR_RECURSIVE(List.of(
                "function listDir (dir, root) {",
                "    o.WriteLine((root === undefined) ? dir.Path : dir.Path.substring(1 + root.Path.length))",
                "    for(var e = new Enumerator(dir.Files); !e.atEnd(); e.moveNext()) {",
                "        o.WriteLine((root === undefined) ? e.item().Path : e.item().Path.substring(1 + root.Path.length))",
                "    }",
                "    for(var e = new Enumerator(dir.SubFolders); !e.atEnd(); e.moveNext()) {",
                "        listDir(e.item(), root)",
                "    }",
                "}"));

        WinGlobals(String expr) {
            this(List.of(expr));
        }

        WinGlobals(List<String> expr) {
            this.expr = expr;
        }

        public List<String> expr() {
            return expr;
        }

        public void appendTo(Consumer<Collection<String>> acc) {
            acc.accept(expr);
        }

        private final List<String> expr;
    }

    public void create(JPackageCommand cmd, List<String> script) {
        create(scriptPath(cmd), script);
    }

    public static class PackagingDirectoryVerifierBuilder {

        public PackagingDirectoryVerifierBuilder withUnchangedDirectory(Path v) {
            unchangedDirectories.add(verifyPath(v));
            return this;
        }

        public PackagingDirectoryVerifierBuilder withUnchangedDirectory(String v) {
            return withUnchangedDirectory(Path.of(v));
        }

        public PackagingDirectoryVerifierBuilder withEmptyDirectory(Path v) {
            emptyDirectories.add(verifyPath(v));
            return this;
        }

        public PackagingDirectoryVerifierBuilder withEmptyDirectory(String v) {
            return withEmptyDirectory(Path.of(v));
        }

        public PackagingDirectoryVerifierBuilder withNonexistantPath(Path v) {
            nonexistantPaths.add(verifyPath(v));
            return this;
        }

        public PackagingDirectoryVerifierBuilder withNonexistantPath(String v) {
            return withNonexistantPath(Path.of(v));
        }

        public PackageTest apply(PackageTest test) {
            return verifyPackagingDirectories(test, unchangedDirectories, emptyDirectories, nonexistantPaths);
        }

        private Path verifyPath(Path v) {
            if (v.isAbsolute() ) {
                throw new IllegalArgumentException();
            }
            return v;
        }

        private final List<Path> unchangedDirectories = new ArrayList<>();
        private final List<Path> emptyDirectories = new ArrayList<>();
        private final List<Path> nonexistantPaths = new ArrayList<>();
    }

    public static PackagingDirectoryVerifierBuilder verifyPackagingDirectories() {
        return new PackagingDirectoryVerifierBuilder();
    }

    public static PackageTest verifyPackagingDirectories(PackageTest test) {
        return new PackagingDirectoryVerifierBuilder().apply(test);
    }

    public interface EnvVarVerifier {
        boolean isEmpty();
        List<String> createScript();
        void verify(Map<String, Object> envVarsWithExpectedValues);
    }

    public interface ExpectedEnvVarValue {
        Object fromString(String v);
        Object value();

        static ExpectedEnvVarValue create(Object expectedValue, Function<String, Object> conv) {
            Objects.requireNonNull(expectedValue);
            Objects.requireNonNull(conv);
            return new ExpectedEnvVarValue() {

                @Override
                public Object fromString(String v) {
                    return conv.apply(v);
                }

                @Override
                public Object value() {
                    return expectedValue;
                }
            };
        }
    }

    public static final class EnvVarVerifierBuilder {

        public EnvVarVerifierBuilder outputDir(Path v) {
            outputDir = v;
            return this;
        }

        public EnvVarVerifierBuilder envVar(String v) {
            envVarNames.add(v);
            return this;
        }

        public EnvVarVerifier create() {
            return new DefaultEnvVarVerifier(envVarNames, Optional.ofNullable(outputDir).orElseGet(() -> {
                return TKit.createTempDirectory("env-vars");
            }).toAbsolutePath());
        }

        private final Set<String> envVarNames = new HashSet<>();
        private Path outputDir;
    }

    public static EnvVarVerifierBuilder verifyEnvVariables() {
        return new EnvVarVerifierBuilder();
    }

    private record DefaultEnvVarVerifier(Set<String> envVarNames, Path outputDir) implements EnvVarVerifier {
        DefaultEnvVarVerifier {
            Objects.requireNonNull(envVarNames);
            Objects.requireNonNull(outputDir);
        }

        @Override
        public boolean isEmpty() {
            return envVarNames.isEmpty();
        }

        @Override
        public List<String> createScript() {
            final List<String> script = new ArrayList<>();

            if (TKit.isWindows()) {
                script.addAll(WinGlobals.JS_SHELL.expr());
                script.addAll(WinGlobals.JS_FS.expr());
            }

            script.addAll(envVarNames.stream().sorted().map(envVarName -> {
                final var outputFile = outputDir.resolve(envVarName);
                if (TKit.isWindows()) {
                    return Stream.of(
                            String.format("WScript.Echo('Env var: %s')", envVarName),
                            "{",
                            String.format("    var o = fs.CreateTextFile('%s', true)", outputFile.toString().replace('\\', '/')),
                            String.format("    o.Write(shell.ExpandEnvironmentStrings('%%%s%%'))", envVarName),
                            "    o.Close()",
                            "}"
                    );
                } else {
                    return Stream.of(
                            String.format("printf 'Env var: %%s\\n' '%s'", envVarName),
                            String.format("printf '%%s' \"${%s}\" > '%s'", envVarName, outputFile)
                    );
                }
            }).flatMap(x -> x).toList());

            return script;
        }

        @Override
        public void verify(Map<String, Object> envVarsWithExpectedValues) {
            if (!Comm.compare(envVarNames, envVarsWithExpectedValues.keySet()).uniqueEmpty()) {
                throw new IllegalArgumentException();
            }
            try {
                for (final var envVarName : envVarNames.stream().sorted().toList()) {
                    Object actualValue = Files.readString(outputDir.resolve(envVarName));
                    Object expetedValue = envVarsWithExpectedValues.get(envVarName);
                    if (expetedValue instanceof ExpectedEnvVarValue ext) {
                        actualValue = ext.fromString((String)actualValue);
                        expetedValue = ext.value();
                    }
                    TKit.assertEquals(expetedValue, actualValue, String.format("Check value of [%s] environment variable", envVarName));
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    static void create(Path scriptFilePath, List<String> script) {
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
            TKit.traceFileContents(scriptFilePath, String.format("[%s] script", scriptFilePath.getFileName()));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    String scriptName(JPackageCommand cmd) {
        return String.format("%s-%s", cmd.name(), suffix);
    }

    private Path scriptPath(JPackageCommand cmd) {
        return Path.of(cmd.getArgumentValue("--resource-dir"), scriptName(cmd));
    }

    private static PackageTest verifyPackagingDirectories(PackageTest test,
            List<Path> additionalUnchangedDirectories, List<Path> additionalEmptyDirectories, List<Path> additionalNonexistantPaths) {

        final Map<PackageType, Path> capturedConfigDirs = new HashMap<>();
        final Map<PackageType, Path> capturedAppImageDirs = new HashMap<>();
        final var capturedAdditionalDirs = Stream.generate(() -> {
            return new HashMap<PackageType, Path>();
        }).limit(additionalUnchangedDirectories.size()).toList();

        return test.addInitializer(cmd -> {
            setupDirectory(cmd, "temp", "--temp");
            setupDirectory(cmd, "resources", "--resource-dir");

            final var configDirContets = TKit.createTempFile(addPkgTypeSuffix("config-listing", cmd) + ".txt").toAbsolutePath();
            capturedConfigDirs.put(cmd.packageType(), configDirContets);

            final var appImageDirContets = TKit.createTempFile(addPkgTypeSuffix("app-image-listing", cmd) + ".txt").toAbsolutePath();
            capturedAppImageDirs.put(cmd.packageType(), appImageDirContets);

            final var additionalDirContets = IntStream.range(0, additionalUnchangedDirectories.size()).mapToObj(i -> {
                return String.format("additional-dir-%d", i);
            }).map(name -> {
                return addPkgTypeSuffix(name, cmd) + ".txt";
            }).map(TKit::createTempFile).map(Path::toAbsolutePath).toList();
            for (int i = 0; i != additionalUnchangedDirectories.size(); i++) {
                capturedAdditionalDirs.get(i).put(cmd.packageType(), additionalDirContets.get(i));
            }

            final List<String> script = new ArrayList<>();
            if (TKit.isWindows()) {
                List.of(WinGlobals.JS_LIST_DIR_RECURSIVE, WinGlobals.JS_SHELL, WinGlobals.JS_FS).forEach(v -> {
                    v.appendTo(script::addAll);
                });

                script.addAll(List.of(
                        String.format("var o = fs.CreateTextFile('%s', true)", configDirContets.toString().replace('\\', '/')),
                        "var configDir = fs.GetFolder(fs.GetParentFolderName(WScript.ScriptFullName))",
                        "listDir(configDir)",
                        "o.Close()",
                        String.format("o = fs.CreateTextFile('%s', true)", appImageDirContets.toString().replace('\\', '/')),
                        "listDir(fs.GetFolder(shell.CurrentDirectory))",
                        "o.Close()"
                ));
            } else {
                script.addAll(List.of(
                        "set -e",
                        String.format("find \"${0%%/*}\" >> '%s'", configDirContets),
                        String.format("find \"$PWD\" >> '%s'", appImageDirContets)
                ));
            }

            script.addAll(IntStream.range(0, additionalUnchangedDirectories.size()).mapToObj(i -> {
                final var dirAsStr = additionalPathInScript(additionalUnchangedDirectories.get(i));
                final var captureFile = additionalDirContets.get(i);
                if (TKit.isWindows()) {
                    return Stream.of(
                            String.format("WScript.Echo('Save directory listing: ' + %s)", dirAsStr),
                            String.format("o = fs.CreateTextFile('%s', true)", captureFile.toString().replace('\\', '/')),
                            String.format("listDir(fs.GetFolder(%s))", dirAsStr),
                            "o.Close()"
                    );
                } else {
                    return Stream.of(
                            String.format("printf 'Save directory listing: %%s\\n' %s", dirAsStr),
                            String.format("find %s >> '%s'", dirAsStr, captureFile)
                    );
                }
            }).flatMap(x -> x).toList());

            script.addAll(additionalEmptyDirectories.stream().map(JPackageUserScript::additionalPathInScript).map(dirAsStr -> {
                if (TKit.isWindows()) {
                    return Stream.of(
                            String.format("WScript.Echo('Check directory: ' + %s)", dirAsStr),
                            "{",
                            "    WScript.Echo('  exists')",
                            String.format("    var f = fs.GetFolder(%s)", dirAsStr),
                            "    WScript.Echo('  is empty')",
                            "    if (f.SubFolders.Count != 0 || f.Files.Count != 0) WScript.Quit(1)",
                            "}"
                    );
                } else {
                    return Stream.of(
                            String.format("printf 'Check directory: %%s\\n' %s", dirAsStr),
                            "echo '  exists'",
                            String.format("[ -d %s ]", dirAsStr),
                            "echo '  is empty'",
                            "exec 3>&1",
                            String.format("[ -z \"$(find %s -mindepth 1 -maxdepth 1 3>&- | tee /dev/fd/3)\" ]", dirAsStr),
                            "exec 3>&-"
                    );
                }
            }).flatMap(x -> x).toList());

            script.addAll(additionalNonexistantPaths.stream().map(JPackageUserScript::additionalPathInScript).map(pathAsStr -> {
                if (TKit.isWindows()) {
                    return Stream.of(
                            String.format("WScript.Echo('Check nonexistant: ' + %s)", pathAsStr),
                            String.format("if (fs.FileExists(%s) || fs.FolderExists(%s)) WScript.Quit(1)", pathAsStr, pathAsStr)
                    );
                } else {
                    return Stream.of(
                            String.format("printf 'Check nonexistant: %%s\\n' %s", pathAsStr),
                            String.format("[ ! -e %s ]", pathAsStr)
                    );
                }
            }).flatMap(x -> x).toList());

            POST_IMAGE.create(cmd, script);
        }).addBundleVerifier(cmd -> {
            final var configDir = verifyDirectoryContents(capturedConfigDirs.get(cmd.packageType()));
            verifyDirectoryContents(capturedAppImageDirs.get(cmd.packageType()));
            for (final var v : capturedAdditionalDirs) {
                verifyDirectoryContents(v.get(cmd.packageType()));
            }
            additionalNonexistantPaths.forEach(path -> {
                TKit.assertPathExists(configDir.resolve(path), true);
            });
            additionalEmptyDirectories.forEach(path -> {
                TKit.assertDirectoryNotEmpty(configDir.resolve(path));
            });
        });
    }

    private static String additionalPathInScript(Path path) {
        path = path.normalize();
        if (TKit.isWindows()) {
            return String.format("configDir.Path + '/%s'", path.toString().replace('\\', '/'));
        } else {
            return String.format("\"${0%%/*}\"'/%s'", path);
        }
    }

    private static Path verifyDirectoryContents(Path fileWithExpectedDirContents) throws IOException {
        TKit.trace(String.format("Process [%s] file...", fileWithExpectedDirContents));

        final var data = Files.readAllLines(fileWithExpectedDirContents);
        final var dir = Path.of(data.getFirst());
        final var capturedDirContents = data.stream().skip(1).map(Path::of).map(dir::relativize).toList();

        // Verify new files are not created in the "config" directory after the script execution.
        TKit.assertDirectoryContentRecursive(dir).removeAll(capturedDirContents).match();

        return dir;
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
