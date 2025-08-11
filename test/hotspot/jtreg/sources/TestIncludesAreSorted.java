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

/*
 * @test
 * @bug 8343802
 * @summary Tests that HotSpot C++ files have sorted includes
 * @build SortIncludes
 * @run main TestIncludesAreSorted
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class TestIncludesAreSorted {

    /**
     * The directories under {@code <jdk>/src/hotspot} to process. This list can be expanded over
     * time until the point where it's simply "." (i.e. all HotSpot source files are compliant and
     * can be checked).
     */
    private static final String[] HOTSPOT_SOURCES_TO_CHECK = {
                    "share/adlc",
                    "share/c1",
                    "share/ci",
                    "share/compiler",
                    "share/jvmci",
                    "share/libadt",
                    "share/metaprogramming",
                    "share/oops",
                    "share/opto",
                    "share/services",
                    "share/utilities"
    };

    /**
     * Gets the absolute path to {@code <jdk>/src/hotspot} by searching up the file system starting
     * at {@code dir}.
     */
    private static Path getHotSpotSrcDir(Path dir) {
        while (dir != null) {
            Path path = dir.resolve("src").resolve("hotspot");
            if (Files.exists(path)) {
                return path;
            }
            dir = dir.getParent();
        }
        throw new RuntimeException("Could not locate the src/hotspot directory by searching up from " + dir);
    }

    public static void main(String[] ignore) throws IOException {
        Path testSrcDir = Paths.get(System.getProperty("test.src"));
        Path root = getHotSpotSrcDir(testSrcDir);
        String[] args = Stream.of(HOTSPOT_SOURCES_TO_CHECK)//
                        .map(root::resolve)
                        .map(Path::toString)
                        .toArray(String[]::new);
        try {
            SortIncludes.main(args);
        } catch (SortIncludes.UnsortedIncludesException e) {
            String msg = String.format("""
                            %s

                            This should be fixable by running:

                                java %s.java --update %s


                            Note that non-space characters after the closing " or > of an include statement
                            can be used to prevent re-ordering of the include. For example:

                            #include "e.hpp"
                            #include "d.hpp"
                            #include "c.hpp" // do not reorder
                            #include "b.hpp"
                            #include "a.hpp"

                            will be reformatted as:

                            #include "d.hpp"
                            #include "e.hpp"
                            #include "c.hpp" // do not reorder
                            #include "a.hpp"
                            #include "b.hpp"

                            """,
                    e.getMessage(),
                    testSrcDir.resolve(SortIncludes.class.getSimpleName()),
                    String.join(" ", args));
            throw new RuntimeException(msg);
        }
    }
}
