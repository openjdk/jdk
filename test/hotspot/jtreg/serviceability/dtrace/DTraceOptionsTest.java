/*
 * Copyright (c) 2022, Red Hat, Inc.  All rights reserved.
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
 * @test id=enabled
 * @bug 8281822
 * @summary Test DTrace options are accepted on suitable builds
 * @requires vm.flagless
 * @requires vm.hasDTrace
 *
 * @library /test/lib
 * @run driver DTraceOptionsTest true
 */

/*
 * @test id=disabled
 * @bug 8281822
 * @summary Test DTrace options are rejected on unsuitable builds
 * @requires vm.flagless
 * @requires !vm.hasDTrace
 *
 * @library /test/lib
 * @run driver DTraceOptionsTest disabled
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class DTraceOptionsTest {
    public static void main(String[] args) throws Throwable {
        boolean dtraceEnabled;
        if (args.length > 0) {
            dtraceEnabled = Boolean.parseBoolean(args[0]);
        } else {
            throw new IllegalArgumentException("Should provide the argument");
        }

        String[] options = {
            "ExtendedDTraceProbes",
            "DTraceMethodProbes",
            "DTraceAllocProbes",
            "DTraceMonitorProbes",
        };

        for (String opt : options) {
            var pb = ProcessTools.createJavaProcessBuilder("-XX:+" + opt, "-version");
            var oa = new OutputAnalyzer(pb.start());
            if (dtraceEnabled) {
                oa.shouldHaveExitValue(0);
            } else {
                oa.shouldNotHaveExitValue(0);
                oa.shouldContain(opt + " flag is not applicable for this configuration");
            }
        }
    }

}
