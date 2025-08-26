/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.PathUtils;


public final class FileAssociations {
    public FileAssociations(String faSuffixName) {
        suffixName = faSuffixName;
        setFilename("fa");
        setDescription("jpackage test extension");
    }

    private void createFile() {
        Map<String, String> entries = new HashMap<>(Map.of(
            "extension", suffixName,
            "mime-type", getMime()
        ));
        if (description != null) {
            entries.put("description", description);
        }
        if (icon != null) {
            if (TKit.isWindows()) {
                entries.put("icon", icon.toString().replace("\\", "/"));
            } else {
                entries.put("icon", icon.toString());
            }
        }
        TKit.createPropertiesFile(file, entries);
    }

    public FileAssociations setFilename(String v) {
        file = TKit.workDir().resolve(v + ".properties");
        return this;
    }

    public FileAssociations setDescription(String v) {
        description = v;
        return this;
    }

    public FileAssociations setIcon(Path v) {
        icon = v;
        return this;
    }

    Path getLinuxIconFileName() {
        if (icon == null) {
            return null;
        }
        return Path.of(getMime().replace('/', '-') + PathUtils.getSuffix(icon));
    }

    Path getPropertiesFile() {
        return file;
    }

    String getSuffix() {
        return "." + suffixName;
    }

    String getMime() {
        return "application/x-jpackage-" + suffixName;
    }

    public void applyTo(PackageTest test) {
        test.notForTypes(PackageType.MAC_DMG, () -> {
            test.addInitializer(cmd -> {
                createFile();
                cmd.addArguments("--file-associations", getPropertiesFile());
            });
            test.addHelloAppFileAssociationsVerifier(this);
        });
    }

    Iterable<TestRun> getTestRuns() {
        return Optional.ofNullable(testRuns).orElseGet(() -> {
            var builder = createTestRuns()
                    .setCurrentInvocationType(InvocationType.DesktopOpenAssociatedFile)
                    .addTestRunForFilenames("test_desktop_open_file");
            if (TKit.isWindows()) {
                builder.setCurrentInvocationType(InvocationType.WinCommandLine)
                        .addTestRunForFilenames("test_cmd_line")
                        .setCurrentInvocationType(InvocationType.WinDesktopOpenShortcut)
                        .addTestRunForFilenames("test_desktop_open_shortcut");
            }
            return builder.testRuns;
        });
    }

    public static TestRunsBuilder createTestRuns() {
        return new TestRunsBuilder();
    }

    static class TestRun {
        Iterable<String> getFileNames() {
            return testFileNames;
        }

        List<String> openFiles(List<Path> testFiles) throws IOException {
            // current supported invocation types work only on single files
            Path testFile = testFiles.get(0);

            // To test unicode arguments on Windows manually:
            // 1. add the following argument ("Hello" in Bulgarian) to the
            //    additionalArgs list: "Здравейте"
            // 2. in Control Panel -> Region -> Administrative -> Language for non-Unicode programs
            //    change the system locale to "Bulgarian (Bulgaria)"
            // 3. reboot Windows and re-run the test

            switch (invocationType) {
                case DesktopOpenAssociatedFile: {
                    TKit.trace(String.format("Use desktop to open [%s] file", testFile));
                    if (!HelloApp.CLEAR_JAVA_ENV_VARS) {
                        Desktop.getDesktop().open(testFile.toFile());
                    } else {
                        final var jsScript = TKit.createTempFile(Path.of("fa-scripts", testFile.getFileName().toString() + ".jsh"));
                        TKit.createTextFile(jsScript, List.of(
                                "import java.awt.Desktop",
                                "import java.io.File",
                                String.format("Desktop.getDesktop().open(new File(\"%s\"))", testFile.toString().replace('\\', '/')),
                                "/exit"));
                        final var exec = Executor.of(JavaTool.JSHELL.getPath().toString(), jsScript.toString());
                        HelloApp.configureEnvironment(exec).dumpOutput().execute();
                    }
                    return List.of(testFile.toString());
                }
                case WinCommandLine: {
                    List<String> additionalArgs = List.of("foo", "bar baz", "boo");
                    TKit.trace(String.format("Use command line to open [%s] file", testFile));
                    ArrayList<String> cmdLine = new ArrayList<>(List.of("cmd", "/c", testFile.toString()));
                    cmdLine.addAll(additionalArgs);
                    Executor.of(cmdLine.toArray(new String[0])).execute();
                    ArrayList<String> expectedArgs = new ArrayList<>(List.of(testFile.toString()));
                    expectedArgs.addAll(additionalArgs);
                    return expectedArgs;
                }
                case WinDesktopOpenShortcut: {
                    Path testDir = testFile.getParent();
                    List<String> additionalArgs = List.of("foo", "bar baz", "boo");
                    // create a shortcut and open it with desktop
                    final Path createShortcutVbs = testDir.resolve("createShortcut.vbs");
                    final Path shortcutLnk = testDir.resolve("shortcut.lnk");
                    StringBuilder shortcutArgs = new StringBuilder();
                    for (int i = 0; i < additionalArgs.size(); i++) {
                        String arg = additionalArgs.get(i);
                        if (arg.contains(" ")) {
                            shortcutArgs.append(String.format("\"\"%s\"\"", arg));
                        } else {
                            shortcutArgs.append(arg);
                        }
                        if (i < additionalArgs.size() - 1) {
                            shortcutArgs.append(" ");
                        }
                    }
                    TKit.createTextFile(createShortcutVbs, List.of(
                            "Dim sc, shell",
                            "Set shell = WScript.CreateObject (\"WScript.Shell\")",
                            String.format("Set sc = shell.CreateShortcut (\"%s\")", shortcutLnk),
                            String.format("sc.TargetPath = \"\"\"%s\"\"\"", testFile),
                            String.format("sc.Arguments = \"%s\"", shortcutArgs.toString()),
                            String.format("sc.WorkingDirectory = \"\"\"%s\"\"\"", testDir),
                            "sc.Save()"
                    ));
                    Executor.of("cscript", "/nologo", createShortcutVbs.toString()).execute();
                    TKit.assertFileExists(shortcutLnk);
                    TKit.trace(String.format("Use desktop to open [%s] file", shortcutLnk));
                    Desktop.getDesktop().open(shortcutLnk.toFile());
                    ArrayList<String> expectedArgs = new ArrayList<>(List.of(testFile.toString()));
                    expectedArgs.addAll(additionalArgs);
                    return expectedArgs;
                }
                default:
                    throw new IllegalStateException(String.format(
                            "Invalid invocationType: [%s]", invocationType));
            }
        }

        private TestRun(Collection<String> testFileNames,
                InvocationType invocationType) {

            Objects.requireNonNull(invocationType);

            if (testFileNames.size() == 0) {
                throw new IllegalArgumentException("Empty test file names list");
            }

            if (invocationType == InvocationType.DesktopOpenAssociatedFile && testFileNames.size() != 1) {
                throw new IllegalArgumentException("Only one file can be configured for opening with the desktop");
            }

            this.testFileNames = testFileNames;
            this.invocationType = invocationType;
        }

        private final Collection<String> testFileNames;
        private final InvocationType invocationType;
    }

    public static class TestRunsBuilder {
        public TestRunsBuilder setCurrentInvocationType(InvocationType v) {
            curInvocationType = v;
            return this;
        }

        public TestRunsBuilder addTestRunForFilenames(String ... filenames) {
            testRuns.add(new TestRun(List.of(filenames), curInvocationType));
            return this;
        }

        public void applyTo(FileAssociations fa) {
            fa.testRuns = testRuns;
        }

        private InvocationType curInvocationType = InvocationType.DesktopOpenAssociatedFile;
        private List<TestRun> testRuns = new ArrayList<>();
    }

    public static enum InvocationType {
        DesktopOpenAssociatedFile,
        WinCommandLine,
        WinDesktopOpenShortcut
    }

    private Path file;
    private final String suffixName;
    private String description;
    private Path icon;
    private Collection<TestRun> testRuns;
}
