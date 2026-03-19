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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public final class FileAssociations {
    public FileAssociations(String faSuffixName) {
        suffixName = faSuffixName;
        setDescription("jpackage test extension");
    }

    private Path createPropertiesFile() {
        Map<String, String> entries = new TreeMap<>(Map.of(
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

        var path = TKit.createTempFile("fa.properties");
        TKit.createPropertiesFile(path, entries);

        return path;
    }

    public FileAssociations setDescription(String v) {
        description = v;
        return this;
    }

    public FileAssociations setIcon(Path v) {
        icon = v;
        return this;
    }

    String getSuffix() {
        return "." + suffixName;
    }

    String getMime() {
        return "application/x-jpackage-" + suffixName;
    }

    boolean hasIcon() {
        return icon != null;
    }

    public void applyTo(PackageTest test) {
        test.notForTypes(PackageType.MAC_DMG, () -> {
            test.addInitializer(cmd -> {
                cmd.addArguments("--file-associations", createPropertiesFile());
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

    record TestRun(Path fileName, InvocationType invocationType) {

        TestRun {
            Objects.requireNonNull(fileName);
            Objects.requireNonNull(invocationType);

            if (fileName.getNameCount() != 1 || fileName.isAbsolute()) {
                throw new IllegalArgumentException();
            }
        }

        List<String> openFile(Path testFile) throws IOException {

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
    }

    public static final class TestRunsBuilder {

        private TestRunsBuilder() {
        }

        public TestRunsBuilder setCurrentInvocationType(InvocationType v) {
            curInvocationType = v;
            return this;
        }

        public TestRunsBuilder addTestRunForFilenames(String fileName) {
            testRuns.add(new TestRun(Path.of(fileName), curInvocationType));
            return this;
        }

        public void applyTo(FileAssociations fa) {
            fa.testRuns = List.copyOf(testRuns);
        }

        private InvocationType curInvocationType = InvocationType.DesktopOpenAssociatedFile;
        private List<TestRun> testRuns = new ArrayList<>();
    }

    public enum InvocationType {
        DesktopOpenAssociatedFile,
        WinCommandLine,
        WinDesktopOpenShortcut
    }

    record FileAssociationDescriptor(
            String launcherName,
            Optional<String> description,
            String mimeType,
            Optional<String> extension) {

        FileAssociationDescriptor {
            Objects.requireNonNull(launcherName);
            Objects.requireNonNull(description);
            Objects.requireNonNull(mimeType);
            Objects.requireNonNull(extension);
        }

        FileAssociationDescriptor copyWithDescription(String description) {
            return new FileAssociationDescriptor(launcherName, Optional.of(description), mimeType, extension);
        }

        static FileAssociationDescriptor create(String launcherName, PropertyFile props) {
            return new FileAssociationDescriptor(
                    launcherName,
                    props.findProperty("description"),
                    props.findProperty("mime-type").orElseThrow(),
                    props.findProperty("extension"));
        }

        private static <T> Comparator<Optional<T>> optionalComparator(Comparator<T> valueComparator) {
            return Comparator
                    .comparing(Optional<T>::isPresent)
                    .thenComparing(opt -> {
                        return opt.orElse(null);
                    }, Comparator.nullsLast(valueComparator));
        }

        static final Comparator<FileAssociationDescriptor> COMPARATOR = Comparator
                .comparing(FileAssociationDescriptor::launcherName)
                .thenComparing(FileAssociationDescriptor::mimeType)
                .thenComparing(FileAssociationDescriptor::extension, optionalComparator(String::compareTo))
                .thenComparing(FileAssociationDescriptor::description, optionalComparator(String::compareTo));
    }

    static void vallidateFileAssociations(JPackageCommand cmd) {
        var comm = Comm.compare(Set.copyOf(declaredFileAssociations(cmd)), Set.copyOf(definedFileAssociations(cmd)));
        if (!Stream.of(comm.unique1(), comm.unique2(), comm.common()).allMatch(Collection::isEmpty)) {
            trace(comm.common(), "Expected file associations (size=%d):");
            trace(comm.unique1(), "Missing file associations (size=%d):");
            trace(comm.unique2(), "Unexpected file associations (size=%d):");
            TKit.trace("DONE");
            TKit.assertTrue(comm.uniqueEmpty(), "Check file associations are as expected");
        }
    }

    private static void trace(Collection<FileAssociationDescriptor> fas, String format) {
        Objects.requireNonNull(fas);
        Objects.requireNonNull(format);
        if (!fas.isEmpty()) {
            TKit.trace(String.format(format, fas.size()));
            fas.stream().sorted(FileAssociationDescriptor.COMPARATOR).forEach(fa -> {
                var tokens = new ArrayList<String>();
                tokens.add("launcher=[" + fa.launcherName() + "]");
                tokens.add("mime=[" + fa.mimeType() + "]");
                fa.extension().ifPresent(extension -> {
                    tokens.add("ext=[" + extension + "]");
                });
                fa.description().ifPresent(description -> {
                    tokens.add("description=[" + description + "]");
                });
                TKit.trace(String.format("  %s", tokens.stream().collect(Collectors.joining(", "))));
            });
        }
    }

    private static Collection<FileAssociationDescriptor> definedFileAssociations(JPackageCommand cmd) {
        if (cmd.isImagePackageType()) {
            return List.of();
        } else if (TKit.isWindows()) {
            return WindowsHelper.fileAssociations(cmd);
        } else if (TKit.isLinux()) {
            return LinuxHelper.fileAssociations(cmd);
        } else if (TKit.isOSX()) {
            return MacHelper.fileAssociations(cmd);
        } else {
            throw new AssertionError();
        }
    }

    private static Collection<FileAssociationDescriptor> declaredFileAssociations(JPackageCommand cmd) {
        return Stream.of(cmd.getAllArgumentValues("--file-associations")).map(Path::of).map(PropertyFile::new).map(props -> {
            var fa = FileAssociationDescriptor.create(cmd.mainLauncherName(), props);
            if (fa.description().isEmpty() && !TKit.isOSX()) {
                fa = fa.copyWithDescription(String.format("%s association", cmd.name()));
            }
            return fa;
        }).toList();
    }

    private final String suffixName;
    private String description;
    private Path icon;
    private Collection<TestRun> testRuns;
}
