/*
 * Copyright (c) 2024, Red Hat, Inc.
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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import tests.Helper;
import tests.JImageGenerator;


/*
 * @test
 * @summary Compare packaged-modules jlink with a run-time image based jlink to
 *          produce the same result
 * @requires (jlink.packagedModules & vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.jlink
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm/timeout=1200 -ea -esa -Xmx1g PackagedModulesVsRuntimeImageLinkTest
 */
public class PackagedModulesVsRuntimeImageLinkTest extends AbstractLinkableRuntimeTest {

    public static void main(String[] args) throws Exception {
        PackagedModulesVsRuntimeImageLinkTest test = new PackagedModulesVsRuntimeImageLinkTest();
        test.run();
    }

    @Override
    void runTest(Helper helper, boolean isLinkableRuntime) throws Exception {
        // create a java.se using jmod-less approach
        BaseJlinkSpecBuilder builder = new BaseJlinkSpecBuilder()
                .helper(helper)
                .name("java-se-jmodless")
                .addModule("java.se")
                .validatingModule("java.se");
        if (isLinkableRuntime) {
            builder.setLinkableRuntime();
        }
        Path javaSEruntimeLink = createJavaImageRuntimeLink(builder.build());
        // create a java.se using packaged modules (jmod-full)
        Path javaSEJmodFull = JImageGenerator.getJLinkTask()
                .output(helper.createNewImageDir("java-se-jmodfull"))
                .addMods("java.se").call().assertSuccess();

        compareRecursively(javaSEruntimeLink, javaSEJmodFull);
    }

    // Visit all files in the given directories checking that they're byte-by-byte identical
    private static void compareRecursively(Path javaSEJmodLess,
            Path javaSEJmodFull) throws IOException, AssertionError {
        FilesCapturingVisitor jmodFullVisitor = new FilesCapturingVisitor(javaSEJmodFull);
        FilesCapturingVisitor jmodLessVisitor = new FilesCapturingVisitor(javaSEJmodLess);
        Files.walkFileTree(javaSEJmodFull, jmodFullVisitor);
        Files.walkFileTree(javaSEJmodLess, jmodLessVisitor);
        List<String> jmodFullFiles = jmodFullVisitor.filesVisited();
        List<String> jmodLessFiles = jmodLessVisitor.filesVisited();
        Collections.sort(jmodFullFiles);
        Collections.sort(jmodLessFiles);

        if (jmodFullFiles.size() != jmodLessFiles.size()) {
            throw new AssertionError(String.format("Size of files different for jmod-less (%d) vs jmod-full (%d) java.se jlink", jmodLessFiles.size(), jmodFullFiles.size()));
        }
        String jimageFile = Path.of("lib").resolve("modules").toString();
        // Compare all files except the modules image
        for (int i = 0; i < jmodFullFiles.size(); i++) {
            String jmodFullPath = jmodFullFiles.get(i);
            String jmodLessPath = jmodLessFiles.get(i);
            if (!jmodFullPath.equals(jmodLessPath)) {
                throw new AssertionError(String.format("jmod-full path (%s) != jmod-less path (%s)", jmodFullPath, jmodLessPath));
            }
            if (jmodFullPath.equals(jimageFile)) {
                continue;
            }
            Path a = javaSEJmodFull.resolve(Path.of(jmodFullPath));
            Path b = javaSEJmodLess.resolve(Path.of(jmodLessPath));
            if (Files.mismatch(a, b) != -1L) {
                handleFileMismatch(a, b);
            }
        }
        // Compare jimage contents by iterating its entries and comparing their
        // paths and content bytes
        //
        // Note: The files aren't byte-by-byte comparable (probably due to string hashing
        // and offset differences in container bytes)
        Path jimageJmodLess = javaSEJmodLess.resolve(Path.of("lib")).resolve(Path.of("modules"));
        Path jimageJmodFull = javaSEJmodFull.resolve(Path.of("lib")).resolve(Path.of("modules"));
        List<String> jimageContentJmodLess = JImageHelper.listContents(jimageJmodLess);
        List<String> jimageContentJmodFull = JImageHelper.listContents(jimageJmodFull);
        assertSameContent("jmod-less", jimageContentJmodLess, "jmod-full", jimageContentJmodFull);
        // Both lists are same size, with same names, so enumerate either.
        for (int i = 0; i < jimageContentJmodFull.size(); i++) {
            if (!jimageContentJmodFull.get(i).equals(jimageContentJmodLess.get(i))) {
                throw new AssertionError(String.format("Jimage content differs at index %d: jmod-full was: '%s' jmod-less was: '%s'",
                                                       i,
                                                       jimageContentJmodFull.get(i),
                                                       jimageContentJmodLess.get(i)
                                                       ));
            }
            String loc = jimageContentJmodFull.get(i);
            if (isTreeInfoResource(loc)) {
                // Skip container bytes as those are offsets to the content
                // of the container which might be different between jlink runs.
                continue;
            }
            byte[] resBytesFull = JImageHelper.getLocationBytes(loc, jimageJmodFull);
            byte[] resBytesLess = JImageHelper.getLocationBytes(loc, jimageJmodLess);
            if (resBytesFull.length != resBytesLess.length || Arrays.mismatch(resBytesFull, resBytesLess) != -1) {
                throw new AssertionError("Content bytes mismatch for " + loc);
            }
        }
    }

    // Helper to assert the content of two jimage files are the same and provide
    // useful debug information otherwise.
    private static void assertSameContent(
            String lhsLabel, List<String> lhsNames, String rhsLabel, List<String> rhsNames) {

        List<String> lhsOnly =
                lhsNames.stream().filter(Predicate.not(Set.copyOf(rhsNames)::contains)).toList();
        List<String> rhsOnly =
                rhsNames.stream().filter(Predicate.not(Set.copyOf(lhsNames)::contains)).toList();
        if (!lhsOnly.isEmpty() || !rhsOnly.isEmpty()) {
            String message = String.format(
                    "jimage content differs for %s (%d) v. %s (%d)",
                    lhsLabel, lhsNames.size(), rhsLabel, rhsNames.size());
            if (!lhsOnly.isEmpty()) {
                message += "\nOnly in " + lhsLabel + ":\n\t" + String.join("\n\t", lhsOnly);
            }
            if (!rhsOnly.isEmpty()) {
                message += "\nOnly in " + rhsLabel + ":\n\t" + String.join("\n\t", rhsOnly);
            }
            throw new AssertionError(message);
        }
    }

    private static boolean isTreeInfoResource(String path) {
        return pathStartsWith(path, "/packages") || pathStartsWith(path, "/modules");
    }

    // Handle both "<prefix>" and "<prefix>/...".
    private static boolean pathStartsWith(String path, String prefix) {
        int plen = prefix.length();
        return path.startsWith(prefix) && (path.length() == plen || path.charAt(plen) == '/');
    }

    private static void handleFileMismatch(Path a, Path b) {
        throw new AssertionError("Files mismatch: " + a + " vs. " + b);
    }

    static class FilesCapturingVisitor extends SimpleFileVisitor<Path> {
        private final Path basePath;
        private final List<String> filePaths = new ArrayList<>();
        public FilesCapturingVisitor(Path basePath) {
            this.basePath = basePath;
        }

        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
            Path relative = basePath.relativize(path);
            filePaths.add(relative.toString());
            return FileVisitResult.CONTINUE;
        }

        List<String> filesVisited() {
            return filePaths;
        }
    }

}
