/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.spi.ToolProvider;

// This program is executed by make/RunTests.gmk to support running HotSpot tests
// in the "AOT mode", for example:
//
//     make test JTREG=AOT_JDK=true TEST=open/test/hotspot/jtreg/runtime/invokedynamic
//
// All JDK classes touched by this program will be stored into a customized AOT cache.
// This is a larger set of classes than those stored in the JDK's default CDS archive.
// This customized cache can also have additional optimizations that are not
// enabled in the default CDS archive. For example, AOT-linked classes and lambda
// expressions. In the future, it can also contain AOT profiles and AOT compiled methods.
//
// We can use this customized AOT cache to run various HotSpot tests to improve
// coverage on AOT.
//
// Note that make/RunTests.gmk loads this class using an implicit classpath of ".", so
// this class will be excluded from the customized AOT cache. As a result,
// the customized AOT cache contains *only* classes from the JDK itself.

public class TestSetupAOT {
    public static void main(String[] args) throws Throwable {
        String[] tools = {
            "javac", "javap", "jlink", "jar",
        };
        // TODO: we should do more substantial work than just running with "--help".
        // E.g., use javac to compile a program.
        for (String tool : tools) {
            ToolProvider t = ToolProvider.findFirst(tool)
                .orElseThrow(() -> new RuntimeException(tool + " not found"));
            t.run(System.out, System.out, "--help");
        }
    }
}
