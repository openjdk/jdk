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
 * @bug 8342550 8349873
 * @summary Three-letter time zone IDs should output a deprecated warning
 *          message.
 * @library /test/lib
 * @build jdk.test.lib.process.ProcessTools
 * @run junit ThreeLetterZoneID
 */
import java.util.TimeZone;
import jdk.test.lib.process.ProcessTools;

import org.junit.jupiter.api.Test;

public class ThreeLetterZoneID {
    private static final String WARNING =
        "WARNING: Use of the three-letter time zone ID \"PST\" is deprecated and it will be removed in a future release";

    public static void main(String... args) {
        if (args.length > 0) {
            TimeZone.getTimeZone("PST");
        } else {
            TimeZone.getDefault();
        }
    }

    @Test
    public void testExplicitGetTimeZone() throws Exception {
        ProcessTools.executeTestJava("ThreeLetterZoneID", "dummy").stderrShouldMatch(WARNING);
    }

    @Test
    public void testSysProp() throws Exception {
        ProcessTools.executeTestJava("-Duser.timezone=PST", "ThreeLetterZoneID").stderrShouldMatch(WARNING);
    }
}
