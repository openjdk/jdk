/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8199871
 * @summary pack200 and unpack200 should print out deprecate warning
 * @modules jdk.pack
 * @compile -XDignore.symbol.file Utils.java
 * @run testng DeprecatePack200
 */

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class DeprecatePack200 {
    final static String PACK200_CMD = Utils.getPack200Cmd();
    final static String UNPACK200_CMD = Utils.getUnpack200Cmd();
    final static Predicate<String> PACK200_MSG = Pattern.compile(
            "Warning: The pack200(\\.exe)?? tool is deprecated, and is planned for removal in a future JDK release.")
            .asMatchPredicate();
    final static Predicate<String> UNPACK200_MSG = Pattern.compile(
            "Warning: The unpack200(\\.exe)?? tool is deprecated, and is planned for removal in a future JDK release.")
            .asMatchPredicate();

    @DataProvider(name="tools")
    public static final Object[][] provide() { return cases; }

    private static final Object[][] cases = {
        { PACK200_MSG, 1, List.of(PACK200_CMD) },
        { PACK200_MSG, 1, List.of(PACK200_CMD, "-V") },
        { PACK200_MSG, 2, List.of(PACK200_CMD, "--help") },
        { PACK200_MSG, 0, List.of(PACK200_CMD, "-XDsuppress-tool-removal-message") },
        { PACK200_MSG, 0, List.of(PACK200_CMD, "--version", "-XDsuppress-tool-removal-message") },
        { PACK200_MSG, 0, List.of(PACK200_CMD, "-h", "-XDsuppress-tool-removal-message") },

        { UNPACK200_MSG, 1, List.of(UNPACK200_CMD) },
        { UNPACK200_MSG, 1, List.of(UNPACK200_CMD, "-V") },
        { UNPACK200_MSG, 1, List.of(UNPACK200_CMD, "--help") },
        { UNPACK200_MSG, 0, List.of(UNPACK200_CMD, "-XDsuppress-tool-removal-message") },
        { UNPACK200_MSG, 0, List.of(UNPACK200_CMD, "--version", "-XDsuppress-tool-removal-message") },
        { UNPACK200_MSG, 0, List.of(UNPACK200_CMD, "-h", "-XDsuppress-tool-removal-message") }
    };

    @Test(dataProvider = "tools")
    public void CheckWarnings(Predicate<String> msg, long count, List<String> cmd) {
        List<String> output = Utils.runExec(cmd, null, true);
        assertEquals(output.stream().filter(msg).count(), count);
    }
}
