/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.Slot;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.MsiDatabase;
import jdk.jpackage.test.MsiDatabase.UIAlterations;
import jdk.jpackage.test.MsiDatabase.ControlEvent;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.WindowsHelper;

/**
 * Test combinations of --win-dir-chooser, --win-shortcut-prompt, --win-with-ui,
 * and --license parameters.
 */

/*
 * @test
 * @summary jpackage with --win-dir-chooser, --win-shortcut-prompt, --with-with-ui and --license parameters
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @build WinInstallerUiTest
 * @requires (os.family == "windows")
 * @requires (jpackage.test.SQETest != null)
 * @run main/othervm/timeout=720 -Xmx512m  jdk.jpackage.test.Main
 *  --jpt-run=WinInstallerUiTest
 *  --jpt-exclude=(dir_chooser)
 *  --jpt-exclude=(license)
 *  --jpt-exclude=+ui
 */

/*
 * @test
 * @summary jpackage with --win-dir-chooser, --win-shortcut-prompt, --with-with-ui and --license parameters
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @build WinInstallerUiTest
 * @requires (os.family == "windows")
 * @requires (jpackage.test.SQETest == null)
 * @run main/othervm/timeout=720 -Xmx512m  jdk.jpackage.test.Main
 *  --jpt-run=WinInstallerUiTest
 */

public class WinInstallerUiTest {

    @Test
    @ParameterSupplier
    public void test(TestSpec spec) {
        spec.run();
    }

    public static void updateExpectedMsiTables() {
        for (var spec : testCases()) {
            spec.createTest(true).addBundleVerifier(cmd -> {
                spec.save(WindowsHelper.getUIAlterations(cmd));
            }).run(Action.CREATE);
        }
    }

    record TestSpec(
            boolean withDirChooser,
            boolean withLicense,
            boolean withShortcutPrompt,
            boolean withUi) {

        TestSpec {
            if (!withDirChooser && !withLicense && !withShortcutPrompt && !withUi) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public String toString() {
            var tokens = new ArrayList<String>();
            if (withDirChooser) {
                tokens.add("dir_chooser");
            }

            if (withShortcutPrompt) {
                tokens.add("shortcut_prompt");
            }

            if (withLicense) {
                tokens.add("license");
            }

            if (withUi) {
                tokens.add("ui");
            }

            return tokens.stream().sorted().collect(Collectors.joining("+"));
        }

        TestSpec copyWithUi(boolean withUi) {
            return new TestSpec(withDirChooser, withLicense, withShortcutPrompt, withUi);
        }

        TestSpec copyWithUi() {
            return copyWithUi(true);
        }

        TestSpec copyWithoutUi() {
            return copyWithUi(false);
        }

        void run() {
            createTest(false).forTypes(PackageType.WIN_MSI).addBundleVerifier(cmd -> {
                var expectedFilesDir = expectedFilesDir();

                var expectedInstallUISequence = Files.readAllLines(expectedFilesDir.resolve(INSTALL_UI_SEQUENCE_FILE));
                var expectedControlEvents = Files.readAllLines(expectedFilesDir.resolve(CONTROL_EVENTS_FILE));

                var uiAlterations = WindowsHelper.getUIAlterations(cmd);

                var actualInstallUISequence = actionSequenceToMarkdownTable(uiAlterations.installUISequence());
                var actualControlEvents = controlEventsToMarkdownTable(uiAlterations.controlEvents());

                TKit.assertStringListEquals(expectedInstallUISequence, actualInstallUISequence,
                        String.format("Check alterations to the `InstallUISequence` MSI table match the contents of [%s] file",
                                expectedFilesDir.resolve(INSTALL_UI_SEQUENCE_FILE)));

                TKit.assertStringListEquals(expectedControlEvents, actualControlEvents,
                        String.format("Check alterations to the `ControlEvents` MSI table match the contents of [%s] file",
                                expectedFilesDir.resolve(CONTROL_EVENTS_FILE)));
            }).run();
        }

        PackageTest createTest(boolean onlyMsi) {
            return new PackageTest()
                    .forTypes(onlyMsi ? Set.of(PackageType.WIN_MSI) : PackageType.WINDOWS)
                    .configureHelloApp()
                    .addInitializer(JPackageCommand::setFakeRuntime)
                    .addInitializer(this::setPackageName)
                    .mutate(test -> {
                        if (withDirChooser) {
                            test.addInitializer(cmd -> cmd.addArgument("--win-dir-chooser"));
                        }

                        if (withShortcutPrompt) {
                            test.addInitializer(cmd -> {
                                cmd.addArgument("--win-shortcut-prompt");
                                cmd.addArgument("--win-menu");
                                cmd.addArgument("--win-shortcut");
                            });
                        }

                        if (withLicense) {
                            setLicenseFile(test);
                        }

                        if (withUi) {
                            test.addInitializer(cmd -> cmd.addArgument("--win-with-ui"));
                        }
                    });
        }

        private void setPackageName(JPackageCommand cmd) {
            StringBuilder sb = new StringBuilder(cmd.name());
            sb.append("With");
            if (withDirChooser) {
                sb.append("Dc"); // DirChooser
            }
            if (withShortcutPrompt) {
                sb.append("Sp"); // ShortcutPrompt
            }
            if (withLicense) {
                sb.append("L"); // License
            }
            if (withUi) {
                sb.append("Ui"); // UI
            }
            cmd.setArgumentValue("--name", sb.toString());
        }

        void save(UIAlterations uiAlterations) {
            var expectedFilesDir = expectedFilesDir();

            write(expectedFilesDir.resolve(INSTALL_UI_SEQUENCE_FILE),
                    actionSequenceToMarkdownTable(uiAlterations.installUISequence()));

            write(expectedFilesDir.resolve(CONTROL_EVENTS_FILE),
                    controlEventsToMarkdownTable(uiAlterations.controlEvents()));
        }

        private Path expectedFilesDir() {
            if ((withDirChooser || withShortcutPrompt || withLicense) && withUi) {
                return copyWithoutUi().expectedFilesDir();
            } else {
                return EXPECTED_MSI_TABLES_ROOT.resolve(toString());
            }
        }

        private void write(Path file, List<String> lines) {
            try {
                Files.createDirectories(file.getParent());
                Files.write(file, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private static List<String> toMarkdownTable(List<String> header, Stream<String[]> content) {
            return Stream.of(
                    Stream.<String[]>of(header.toArray(String[]::new)),
                    Stream.<String[]>of(Collections.nCopies(header.size(), "---").toArray(String[]::new)),
                    content
            ).flatMap(x -> x).map(row -> {
                return Stream.of(row).map(v -> {
                    // Escape the pipe (|) character.
                    return v.replaceAll(Pattern.quote("|"), "&#124;");
                }).collect(Collectors.joining(" | ", "| ", " |"));
            }).toList();
        }

        private static List<String> actionSequenceToMarkdownTable(Collection<MsiDatabase.Action> actions) {
            return toMarkdownTable(
                    List.of("Action", "Condition"),
                    actions.stream().map(action -> {
                        return toStringArray(action.action(), action.condition());
                    })
            );
        }

        private static List<String> controlEventsToMarkdownTable(Collection<ControlEvent> controlEvents) {
            return toMarkdownTable(
                    List.of("Dialog", "Control", "Event", "Argument", "Condition", "Ordering"),
                    controlEvents.stream().map(controlEvent -> {
                        return toStringArray(
                                controlEvent.dialog(),
                                controlEvent.control(),
                                controlEvent.event(),
                                controlEvent.argument(),
                                controlEvent.condition(),
                                Integer.toString(controlEvent.ordering()));
                    })
            );
        }

        private static String[] toStringArray(String... items) {
            return items;
        }

        private static final String CONTROL_EVENTS_FILE = "ControlEvents.md";
        private static final String INSTALL_UI_SEQUENCE_FILE = "InstallUISequence.md";
    }

    public static Collection<?> test() {
        return Stream.concat(
                testCases().stream().filter(Predicate.not(TestSpec::withUi)).map(TestSpec::copyWithUi),
                testCases().stream()
        ).map(v -> {
            return new Object[] {v};
        }).toList();
    }

    private static Collection<TestSpec> testCases() {
        var testCases = new ArrayList<TestSpec>();

        for (var withDirChooser : List.of(true, false)) {
            for (var withLicense : List.of(true, false)) {
                for (var withShortcutPrompt : List.of(true, false)) {
                    if (!withDirChooser && !withLicense && !withShortcutPrompt) {
                        // Duplicates SimplePackageTest
                        continue;
                    }

                    testCases.add(new TestSpec(withDirChooser, withLicense, withShortcutPrompt, false));
                }
            }
        }

        // Enforce UI
        testCases.add(new TestSpec(false, false, false, true));

        return testCases;
    }

    private static void setLicenseFile(PackageTest test) {
        var inputLicenseFile = Slot.<Path>createEmpty();

        test.addRunOnceInitializer(() -> {
            var dir = TKit.createTempDirectory("license-dir");
            inputLicenseFile.set(dir.resolve(LICENSE_FILE.getFileName()));
            Files.copy(LICENSE_FILE, inputLicenseFile.get());
        }).addInitializer(cmd -> {
            cmd.setArgumentValue("--license-file", inputLicenseFile.get());
        });
    }

    private static final Path LICENSE_FILE = TKit.TEST_SRC_ROOT.resolve(Path.of("resources", "license.txt"));

    private static final Path EXPECTED_MSI_TABLES_ROOT = TKit.TEST_SRC_ROOT.resolve(
            Path.of("resources", WinInstallerUiTest.class.getSimpleName()));
}
