/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.jvm;

import java.util.ArrayList;
import java.util.List;
import jdk.jfr.jvm.AfterShutdown;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
/**
 * @test TestAfterShutdown
 * @requires vm.flagless
 * @summary Checks that API interactions with JFR after shutdown works as expected
 * @requires vm.hasJFR
 * @library /test/lib
 * @build jdk.jfr.jvm.AfterShutdown
 * @run main/othervm -Xlog:jfr jdk.jfr.jvm.TestAfterShutdown
 */
public class TestAfterShutdown {
    public static void main(String... args) throws Exception {
        var pb = ProcessTools.createTestJavaProcessBuilder(AfterShutdown.class.getName());
        var result = ProcessTools.executeProcess(pb);
        if (result.getExitValue() != 0) {
            result.reportDiagnosticSummary();
            throw new Exception("Unexpected behavior when doing API operations after shutdown.");
        }
        result.shouldNotContain("FAIL");
    }
}