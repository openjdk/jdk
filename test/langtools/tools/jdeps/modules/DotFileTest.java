/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8173374
 * @summary Tests module dot graph
 * @modules java.desktop
 *          java.sql
 *          jdk.jdeps/com.sun.tools.jdeps
 * @run testng DotFileTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;

public class DotFileTest {
    private static final ToolProvider JDEPS = ToolProvider.findFirst("jdeps")
        .orElseThrow(() -> new RuntimeException("jdeps not found"));

    private static final Path DOTS_DIR = Paths.get("dots");
    private static final Path SPEC_DIR = Paths.get("spec");

    @DataProvider(name = "modules")
    public Object[][] modules() {
        return new Object[][]{
            {"java.desktop", Set.of("java.datatransfer -> java.base",
                                    "java.desktop -> java.datatransfer",
                                    "java.desktop -> java.prefs",
                                    "java.prefs -> java.xml",
                                    "java.xml -> java.base" )
            },
            { "java.sql",    Set.of("java.logging -> java.base",
                                    "java.transaction.xa -> java.base",
                                    "java.sql -> java.logging",
                                    "java.sql -> java.transaction.xa",
                                    "java.sql -> java.xml",
                                    "java.xml -> java.base" )
            }
        };
    }
    @DataProvider(name = "specVersion")
    public Object[][] specVersion() {
        return new Object[][]{
            {"java.desktop", Set.of("java.datatransfer -> java.base",
                                    "java.desktop -> java.datatransfer",
                                    "java.desktop -> java.xml",
                                    "java.xml -> java.base")
            },
            { "java.sql",    Set.of("java.logging -> java.base",
                                    "java.transaction.xa -> java.base",
                                    "java.sql -> java.logging",
                                    "java.sql -> java.transaction.xa",
                                    "java.sql -> java.xml",
                                    "java.xml -> java.base" )
            }
        };
    }

    @Test(dataProvider = "modules")
    public void test(String name, Set<String> edges) throws Exception {
        String[] options = new String[] {
            "-dotoutput", DOTS_DIR.toString(),
            "-s", "-m", name
        };
        assertTrue(JDEPS.run(System.out, System.out, options) == 0);

        Path path = DOTS_DIR.resolve(name + ".dot");
        assertTrue(Files.exists(path));
        Set<String> lines = Files.readAllLines(path).stream()
                                 .filter(l -> l.contains(" -> "))
                                 .map(this::split)
                                 .collect(Collectors.toSet());
        assertEquals(lines, edges);
    }

    @Test(dataProvider = "specVersion")
    public void testAPIOnly(String name, Set<String> edges) throws Exception {
        String[] options = new String[]{
            "-dotoutput", SPEC_DIR.toString(),
            "-s", "-apionly",
            "-m", name
        };
        assertTrue(JDEPS.run(System.out, System.out, options) == 0);

        Path path = SPEC_DIR.resolve(name + ".dot");
        assertTrue(Files.exists(path));
        Set<String> lines = Files.readAllLines(path).stream()
                                 .filter(l -> l.contains(" -> "))
                                 .map(this::split)
                                 .collect(Collectors.toSet());
        assertEquals(lines, edges);
    }

    static Pattern PATTERN = Pattern.compile(" *\"(\\S+)\" -> \"(\\S+)\" .*");
    String split(String line) {
        Matcher pm = PATTERN.matcher(line);
        assertTrue(pm.find());
        return String.format("%s -> %s", pm.group(1), pm.group(2));
    }
}
