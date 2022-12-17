/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8060126
 * @summary Make sure that the tzdata version matches between the run-time and tests.
 */
import java.time.zone.ZoneRules;
import java.time.zone.ZoneRulesProvider;
import java.util.NavigableMap;

public class AssureTzdataVersion {
    public static void main(String... args) throws Exception {
        // get the tzdata version for the run-time
        NavigableMap<String, ZoneRules> map;
        map = ZoneRulesProvider.getVersions("America/Los_Angeles");
        if (map.isEmpty()) {
            throw new RuntimeException("Unknown runtime tzdata version");
        }
        String runtime = map.lastEntry().getKey();

        // get the tzdata version for regression tests
        String testdata = null;
        try (TextFileReader textreader = new TextFileReader("VERSION")) {
            testdata = textreader.getLine().substring("tzdata".length());
        }
        if (!testdata.equals(runtime)) {
            throw new RuntimeException("tzdata versions don't match: run-time=" + runtime
                                       + ", tests=" + testdata);
        }
    }
}
