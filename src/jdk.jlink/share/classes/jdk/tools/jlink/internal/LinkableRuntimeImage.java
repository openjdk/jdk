/*
 * Copyright (c) 2024, Red Hat, Inc.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.tools.jlink.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import jdk.tools.jlink.internal.runtimelink.ResourceDiff;

/**
 * Class that supports the feature of running jlink based on the current
 * run-time image.
 */
public class LinkableRuntimeImage {

    // meta-data files per module for supporting linking from the run-time image
    public static final String RESPATH_PATTERN = "jdk/tools/jlink/internal/runtimelink/fs_%s_files";
    // The diff files per module for supporting linking from the run-time image
    public static final String DIFF_PATTERN = "jdk/tools/jlink/internal/runtimelink/diff_%s";
    // meta data for upgradable files
    private static final String UPGRADEABLE_FILES_PATTERN = "jdk/tools/jlink/internal/runtimelink/upgrade_files_%s.conf";
    private static final Module JDK_JLINK_MOD = LinkableRuntimeImage.class.getModule();

    /**
     * In order to be able to show whether or not a runtime is capable of
     * linking from it in {@code jlink --help} we need to look for the delta
     * files in the {@code jdk.jlink} module. If present we have the capability.
     *
     * @return {@code true} iff this jlink is capable of linking from the
     *         run-time image.
     */
    public static boolean isLinkableRuntime() {
        try (InputStream in = getDiffInputStream("java.base")) {
            return in != null;
        } catch (IOException e) {
            // fall-through
        }
        return false;
    }

    private static InputStream getDiffInputStream(String module) throws IOException {
        String resourceName = String.format(DIFF_PATTERN, module);
        return JDK_JLINK_MOD.getResourceAsStream(resourceName);
    }

    private static Set<String> upgradeableFiles(String module) {
        String resourceName = String.format(UPGRADEABLE_FILES_PATTERN, module);
        InputStream filesIn = null;
        try {
            filesIn = JDK_JLINK_MOD.getResourceAsStream(resourceName);
        } catch (IOException e) {
            throw new AssertionError("Unexpected IO error getting res stream");
        }
        if (filesIn == null) {
            // no upgradeable files
            return Set.of();
        }
        Set<String> upgradeableFiles = new HashSet<>();
        final InputStream in = filesIn;
        try (in;
             Scanner scanner = new Scanner(filesIn)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.trim().startsWith("#")) {
                    // Skip comments
                    continue;
                }
                upgradeableFiles.add(line);
            }
        } catch (IOException e) {
            throw new AssertionError("Failure to retrieve upgradeable files for " +
                                     "module " + module, e);
        }
        return upgradeableFiles;
    }

    public static Archive newArchive(String module,
                                     Path path,
                                     boolean ignoreModifiedRuntime,
                                     TaskHelper taskHelper) {
        assert isLinkableRuntime();
        // Here we retrieve the per module difference file, which is
        // potentially empty, from the modules image and pass that on to
        // JRTArchive for further processing. When streaming resources from
        // the archive, the diff is being applied.
        List<ResourceDiff> perModuleDiff = null;
        try (InputStream in = getDiffInputStream(module)){
            perModuleDiff = ResourceDiff.read(in);
        } catch (IOException e) {
            throw new AssertionError("Failure to retrieve resource diff for " +
                                     "module " + module, e);
        }
        Set<String> upgradeableFiles = upgradeableFiles(module);
        return new JRTArchive(module,
                              path,
                              !ignoreModifiedRuntime,
                              perModuleDiff,
                              taskHelper,
                              upgradeableFiles);
    }


}
