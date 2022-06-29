/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import jdk.jpackage.internal.IOUtils;


final public class FileAssociations {
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
        return Path.of(getMime().replace('/', '-') + IOUtils.getSuffix(icon));
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
            return createTestRuns().addTestRunForFilenames("test").testRuns;
        });
    }

    public static TestRunsBuilder createTestRuns() {
        return new TestRunsBuilder();
    }

    static class TestRun {
        Iterable<String> getFileNames() {
            return testFileNames;
        }

        void openFiles(List<Path> testFiles) throws IOException {
            switch (invocationType) {
                case DesktopOpenAssociatedFile:
                    TKit.trace(String.format("Use desktop to open [%s] file", testFiles.get(0)));
                    Desktop.getDesktop().open(testFiles.get(0).toFile());
                    break;

                case WinCommandLine:
                case WinDesktopOpenContextMenu:
                    // TBD: implement
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
        WinDesktopOpenContextMenu
    }

    private Path file;
    final private String suffixName;
    private String description;
    private Path icon;
    private Collection<TestRun> testRuns;
}
