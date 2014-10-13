/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8058243
 * @summary Reduce size of bytecode for large switch statements
 * @library /tools/lib
 * @build ToolBox
 * @run main SwitchMetricTest
 */

import java.net.URL;
import java.util.List;

public class SwitchMetricTest {
    public static void main(String... args) throws Exception {
        new SwitchMetricTest().run();
    }

    // This code should produce a tableswitch
    class Test1 {
        int m(int x) {
            switch (x) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5: return 1;
            default: return 0;
            }
        }
    }

    // This code should produce a lookupswitch
    class Test2 {
        int m(int x) {
            switch (x) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 50: return 1;
            default: return 0;
            }
        }
    }

    void check(String classfile, String bytecode) throws Exception {
        ToolBox tb = new ToolBox();
        URL url = SwitchMetricTest.class.getResource(classfile);
        List<String> result = tb.new JavapTask()
                .options("-c")
                .classes(url.getFile())
                .run()
                .write(ToolBox.OutputKind.DIRECT)
                .getOutputLines(ToolBox.OutputKind.DIRECT);

        List<String> matches = tb.grep(bytecode, result);
        if (matches.isEmpty())
            throw new Exception(bytecode + " not found");
    }

    void run() throws Exception {
        check("SwitchMetricTest$Test1.class", "tableswitch");
        check("SwitchMetricTest$Test2.class", "lookupswitch");
    }
}
