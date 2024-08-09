/*
 * Copyright (c) 2024, Red Hat, Inc.
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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import tests.Helper;
import tests.JImageValidator;

public abstract class AbstractLinkableRuntimeTest {

    protected static final boolean DEBUG = true;

    public void run() throws Exception {
        Helper helper = Helper.newHelper(true /* JDK linkable runtime tests */);
        if (helper == null) {
            System.err.println(AbstractLinkableRuntimeTest.class.getSimpleName() +
                               ": Test not run");
            return;
        }
        runTest(helper);
        System.out.println(getClass().getSimpleName() + " PASSED!");
    }

    /** main test entrypoint **/
    abstract void runTest(Helper helper) throws Exception;

    /**
     * Ensure 'java --list-modules' lists the correct set of modules in the given
     * image.
     *
     * @param jlinkImage
     * @param expectedModules
     */
    protected void verifyListModules(Path image,
            List<String> expectedModules) throws Exception {
        OutputAnalyzer out = runJavaCmd(image, List.of("--list-modules"));
        List<String> actual = parseListMods(out.getStdout());
        Collections.sort(actual);
        if (!expectedModules.equals(actual)) {
            throw new AssertionError("Different modules! Expected " + expectedModules + " got: " + actual);
        }
    }

    protected OutputAnalyzer runJavaCmd(Path image, List<String> options) throws Exception {
        Path targetJava = image.resolve("bin").resolve(getJava());
        List<String> cmd = new ArrayList<>();
        cmd.add(targetJava.toString());
        for (String opt: options) {
            cmd.add(opt);
        }
        List<String> javaCmd = Collections.unmodifiableList(cmd);
        OutputAnalyzer out;
        try {
            out = ProcessTools.executeCommand(javaCmd.toArray(new String[0]));
        } catch (Throwable e) {
            throw new Exception("Process failed to execute", e);
        }
        if (out.getExitValue() != 0) {
            if (DEBUG) {
                System.err.println("Process stdout was: ");
                System.err.println(out.getStdout());
                System.err.println("Process stderr was: ");
                System.err.println(out.getStderr());
            }
            throw new AssertionError("'" + javaCmd.stream().collect(Collectors.joining(" ")) + "'"
                    + " expected to succeed!");
        }
        return out;
    }

    protected Path createJavaImageRuntimeLink(BaseJlinkSpec baseSpec) throws Exception {
        // create a base image only containing the jdk.jlink module and its transitive closure
        Path runtimeJlinkImage = createRuntimeLinkImage(baseSpec);

        // On Windows jvm.dll is in 'bin' after the jlink
        Path libjvm = Path.of((isWindows() ? "bin" : "lib"), "server", System.mapLibraryName("jvm"));
        JlinkSpecBuilder builder = new JlinkSpecBuilder();
        // And expect libjvm (not part of the jimage) to be present in the resulting image
        builder.expectedFile(libjvm.toString())
               .helper(baseSpec.getHelper())
               .name(baseSpec.getName())
               .validatingModule(baseSpec.getValidatingModule())
               .imagePath(runtimeJlinkImage)
               .expectedLocation("/java.base/java/lang/String.class");
        for (String m: baseSpec.getModules()) {
            builder.addModule(m);
        }
        for (String extra: baseSpec.getExtraOptions()) {
            builder.extraJlinkOpt(extra);
        }
        return jlinkUsingImage(builder.build());
    }

    protected Path jlinkUsingImage(JlinkSpec spec) throws Exception {
        return jlinkUsingImage(spec, new RuntimeLinkOutputAnalyzerHandler());
    }

    protected Path jlinkUsingImage(JlinkSpec spec, OutputAnalyzerHandler handler) throws Exception {
        return jlinkUsingImage(spec, handler, new DefaultSuccessExitPredicate());
    }

    protected Path jlinkUsingImage(JlinkSpec spec, OutputAnalyzerHandler handler, Predicate<OutputAnalyzer> exitChecker) throws Exception {
        String jmodLessGeneratedImage = "target-jmodless-" + spec.getName();
        Path targetImageDir = spec.getHelper().createNewImageDir(jmodLessGeneratedImage);
        Path targetJlink = spec.getImageToUse().resolve("bin").resolve(getJlink());
        String[] jlinkCmdArray = new String[] {
                targetJlink.toString(),
                "--output", targetImageDir.toString(),
                "--verbose",
                "--add-modules", spec.getModules().stream().collect(Collectors.joining(","))
        };
        List<String> jlinkCmd = new ArrayList<>();
        jlinkCmd.addAll(Arrays.asList(jlinkCmdArray));
        if (spec.getExtraJlinkOpts() != null && !spec.getExtraJlinkOpts().isEmpty()) {
            jlinkCmd.addAll(spec.getExtraJlinkOpts());
        }
        if (spec.getModulePath() != null) {
            for (String mp: spec.getModulePath()) {
                jlinkCmd.add("--module-path");
                jlinkCmd.add(mp);
            }
        }
        jlinkCmd = Collections.unmodifiableList(jlinkCmd); // freeze
        System.out.println("DEBUG: jmod-less jlink command: " + jlinkCmd.stream().collect(
                                                    Collectors.joining(" ")));
        OutputAnalyzer analyzer = null;
        try {
            analyzer = ProcessTools.executeProcess(jlinkCmd.toArray(new String[0]));
        } catch (Throwable t) {
            throw new AssertionError("Executing process failed!", t);
        }
        if (!exitChecker.test(analyzer)) {
            if (DEBUG) {
                System.err.println("Process stdout was: ");
                System.err.println(analyzer.getStdout());
                System.err.println("Process stderr was: ");
                System.err.println(analyzer.getStderr());
            }
            // if the exit checker failed, we expected the other outcome
            // i.e. fail for success and success for fail.
            boolean successExit = analyzer.getExitValue() == 0;
            String msg = String.format("Expected jlink to %s given a jmodless image. Exit code was: %d",
                                       (successExit ? "fail" : "pass"), analyzer.getExitValue());
            throw new AssertionError(msg);
        }
        handler.handleAnalyzer(analyzer); // Give tests a chance to process in/output

        // validate the resulting image; Includes running 'java -version', only do this
        // if the jlink succeeded.
        if (analyzer.getExitValue() == 0) {
            JImageValidator validator = new JImageValidator(spec.getValidatingModule(), spec.getExpectedLocations(),
                    targetImageDir.toFile(), spec.getUnexpectedLocations(), Collections.emptyList(), spec.getExpectedFiles());
            validator.validate(); // This doesn't validate locations
            if (!spec.getExpectedLocations().isEmpty() || !spec.getUnexpectedLocations().isEmpty()) {
                JImageValidator.validate(targetImageDir.resolve("lib").resolve("modules"), spec.getExpectedLocations(), spec.getUnexpectedLocations());
            }
        }
        return targetImageDir;
    }

    /**
     * Prepares the test for execution. This assumes the current jimage is
     * runtime-linkable. However, since the 'jmods' dir is present (default jmods
     * module path), it needs to get removed to provoke a runtime link.
     *
     * @param baseSpec
     * @return A path to a JDK image which is prepared for runtime linking.
     * @throws Exception
     */
    protected Path createRuntimeLinkImage(BaseJlinkSpec baseSpec) throws Exception {
        Path runtimeJlinkImage = baseSpec.getHelper().createNewImageDir(baseSpec.getName() + "-jlink");
        copyJDKTreeWithoutJmods(runtimeJlinkImage);
        // Verify the base image is actually without packaged modules
        if (Files.exists(runtimeJlinkImage.resolve("jmods"))) {
            throw new AssertionError("Must not contain 'jmods' directory");
        }
        return runtimeJlinkImage;
    }

    private void copyJDKTreeWithoutJmods(Path runtimeJlinkImage) throws Exception {
        Files.createDirectory(runtimeJlinkImage);
        String javaHome = System.getProperty("java.home");
        Path root = Path.of(javaHome);
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir,
                    BasicFileAttributes attrs) throws IOException {
                Objects.requireNonNull(dir);
                Path relative = root.relativize(dir);
                if (relative.getFileName().equals(Path.of("jmods"))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                // Create dir in destination location
                Path targetDir = runtimeJlinkImage.resolve(relative);
                if (!Files.exists(targetDir)) {
                    Files.createDirectory(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Path relative = root.relativize(file);
                Files.copy(file, runtimeJlinkImage.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });

    }

    private List<String> parseListMods(String output) throws Exception {
        List<String> outputLines = new ArrayList<>();
        try (Scanner lineScan = new Scanner(output)) {
            while (lineScan.hasNextLine()) {
                outputLines.add(lineScan.nextLine());
            }
        }
        return outputLines.stream()
                .map(a -> { return a.split("@", 2)[0];})
                .filter(a -> !a.isBlank())
                .collect(Collectors.toList());
    }

    private String getJlink() {
        return getBinary("jlink");
    }

    private String getJava() {
        return getBinary("java");
    }

    private String getBinary(String binary) {
        return isWindows() ? binary + ".exe" : binary;
    }

    protected static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    static class BaseJlinkSpec {
        final Helper helper;
        final String name;
        final String validatingModule;
        final List<String> modules;
        final List<String> extraOptions;

        BaseJlinkSpec(Helper helper, String name, String validatingModule,
                List<String> modules, List<String> extraOptions) {
            this.helper = helper;
            this.name = name;
            this.modules = modules;
            this.extraOptions = extraOptions;
            this.validatingModule = validatingModule;
        }

        public String getValidatingModule() {
            return validatingModule;
        }

        public Helper getHelper() {
            return helper;
        }

        public String getName() {
            return name;
        }

        public List<String> getModules() {
            return modules;
        }

        public List<String> getExtraOptions() {
            return extraOptions;
        }
    }

    static class BaseJlinkSpecBuilder {
        Helper helper;
        String name;
        String validatingModule;
        List<String> modules = new ArrayList<>();
        List<String> extraOptions = new ArrayList<>();

        BaseJlinkSpecBuilder addModule(String module) {
            modules.add(module);
            return this;
        }

        BaseJlinkSpecBuilder addExtraOption(String option) {
            extraOptions.add(option);
            return this;
        }

        BaseJlinkSpecBuilder helper(Helper helper) {
            this.helper = helper;
            return this;
        }

        BaseJlinkSpecBuilder name(String name) {
            this.name = name;
            return this;
        }

        BaseJlinkSpecBuilder validatingModule(String module) {
            this.validatingModule = module;
            return this;
        }

        BaseJlinkSpec build() {
            if (name == null) {
                throw new IllegalStateException("Name must be set");
            }
            if (helper == null) {
                throw new IllegalStateException("helper must be set");
            }
            if (validatingModule == null) {
                throw new IllegalStateException("the module which should get validated must be set");
            }
            return new BaseJlinkSpec(helper, name, validatingModule, modules, extraOptions);
        }
    }

    static class JlinkSpec {
        final Path imageToUse;
        final Helper helper;
        final String name;
        final List<String> modules;
        final String validatingModule;
        final List<String> expectedLocations;
        final List<String> unexpectedLocations;
        final String[] expectedFiles;
        final List<String> extraJlinkOpts;
        final List<String> modulePath;

        JlinkSpec(Path imageToUse, Helper helper, String name, List<String> modules,
                String validatingModule, List<String> expectedLocations,
                List<String> unexpectedLocations, String[] expectedFiles,
                List<String> extraJlinkOpts,
                List<String> modulePath) {
            this.imageToUse = imageToUse;
            this.helper = helper;
            this.name = name;
            this.modules = modules;
            this.validatingModule = validatingModule;
            this.expectedLocations = expectedLocations;
            this.unexpectedLocations = unexpectedLocations;
            this.expectedFiles = expectedFiles;
            this.extraJlinkOpts = extraJlinkOpts;
            this.modulePath = modulePath;
        }

        public Path getImageToUse() {
            return imageToUse;
        }

        public Helper getHelper() {
            return helper;
        }

        public String getName() {
            return name;
        }

        public List<String> getModules() {
            return modules;
        }

        public String getValidatingModule() {
            return validatingModule;
        }

        public List<String> getExpectedLocations() {
            return expectedLocations;
        }

        public List<String> getUnexpectedLocations() {
            return unexpectedLocations;
        }

        public String[] getExpectedFiles() {
            return expectedFiles;
        }

        public List<String> getExtraJlinkOpts() {
            return extraJlinkOpts;
        }

        public List<String> getModulePath() {
            return modulePath;
        }
    }

    static class JlinkSpecBuilder {
        Path imageToUse;
        Helper helper;
        String name;
        List<String> modules = new ArrayList<>();
        String validatingModule;
        List<String> expectedLocations = new ArrayList<>();
        List<String> unexpectedLocations = new ArrayList<>();
        List<String> expectedFiles = new ArrayList<>();
        List<String> extraJlinkOpts = new ArrayList<>();
        List<String> modulePath = new ArrayList<>();

        JlinkSpec build() {
            if (imageToUse == null) {
                throw new IllegalStateException("No image to use for jlink specified!");
            }
            if (helper == null) {
                throw new IllegalStateException("No helper specified!");
            }
            if (name == null) {
                throw new IllegalStateException("No name for the image location specified!");
            }
            if (validatingModule == null) {
                throw new IllegalStateException("No module specified for after generation validation!");
            }
            return new JlinkSpec(imageToUse,
                                 helper,
                                 name,
                                 modules,
                                 validatingModule,
                                 expectedLocations,
                                 unexpectedLocations,
                                 expectedFiles.toArray(new String[0]),
                                 extraJlinkOpts,
                                 modulePath);
        }

        JlinkSpecBuilder imagePath(Path image) {
            this.imageToUse = image;
            return this;
        }

        JlinkSpecBuilder helper(Helper helper) {
            this.helper = helper;
            return this;
        }

        JlinkSpecBuilder name(String name) {
            this.name = name;
            return this;
        }

        JlinkSpecBuilder addModule(String module) {
            modules.add(module);
            return this;
        }

        JlinkSpecBuilder validatingModule(String module) {
            this.validatingModule = module;
            return this;
        }

        JlinkSpecBuilder addModulePath(String modulePath) {
            this.modulePath.add(modulePath);
            return this;
        }

        JlinkSpecBuilder expectedLocation(String location) {
            expectedLocations.add(location);
            return this;
        }

        JlinkSpecBuilder unexpectedLocation(String location) {
            unexpectedLocations.add(location);
            return this;
        }

        JlinkSpecBuilder expectedFile(String file) {
            expectedFiles.add(file);
            return this;
        }

        JlinkSpecBuilder extraJlinkOpt(String opt) {
            extraJlinkOpts.add(opt);
            return this;
        }
    }

    static abstract class OutputAnalyzerHandler {

        public abstract void handleAnalyzer(OutputAnalyzer out);

    }

    static class RuntimeLinkOutputAnalyzerHandler extends OutputAnalyzerHandler {

        @Override
        public void handleAnalyzer(OutputAnalyzer out) {
            out.shouldContain("Linking based on the current run-time image.");
        }

    }

    static class DefaultSuccessExitPredicate implements Predicate<OutputAnalyzer> {

        @Override
        public boolean test(OutputAnalyzer t) {
            return t.getExitValue() == 0;
        }

    }
}
