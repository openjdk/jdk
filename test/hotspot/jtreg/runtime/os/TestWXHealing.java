/*
 * Copyright (c) 2025 IBM Corporation. All rights reserved.
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
 * @requires os.family == "mac"
 * @requires os.arch == "aarch64"
 * @summary Run shell with -XX:+StressWXHealing. This tests most of
 * the triggers for WX mode.
 * @library /test/lib
 * @compile WXHealing.java
 * @run main TestWXHealing
 */

import java.util.regex.*;
import jdk.test.lib.process.*;

import static java.nio.charset.StandardCharsets.*;

public class TestWXHealing {

    public static void main(String[] args) throws Throwable {
        String[] opts = {"-XX:+UnlockDiagnosticVMOptions",
                         "-XX:+TraceWXHealing", "-XX:+StressWXHealing", "WXHealing"};
        var process = ProcessTools.createTestJavaProcessBuilder(opts).start();
        String output = new String(process.getInputStream().readAllBytes(), UTF_8);
        System.out.println(output);
        if (output.contains("MAP_JIT write protection does not work on this system")) {
            System.out.println("Test was not run because MAP_JIT write protection does not work on this system");
        } else {
            var pattern = Pattern.compile("Healing WXMode WXArmedForWrite at 0x[0-9a-f]* to WXWrite  ");
            var matches = pattern.matcher(output).results().count();
            if (matches < 10) {
                throw new RuntimeException("Only " + matches + " healings in\n" + output);
            }
        }
    }
}
