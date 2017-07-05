/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/********** LINE NUMBER SENSITIVE! *****************************************************************/

/**
 *  @test
 *  @summary Test setting breakpoints on lambda calls
 *
 *  @author Staffan Larsen
 *
 *  @run build TestScaffold VMConnection TargetListener TargetAdapter
 *  @run compile -g LambdaBreakpointTest.java
 *  @run driver LambdaBreakpointTest
 */
import java.util.List;

import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.StepEvent;

 /********** target program **********/

class LambdaBreakpointTestTarg {

    static int[] breakpointLines = {
            62, 66, 63, 64, 65, 67
    };

    public static void main(String[] args) {
        test();
    }

    private static void test() {
        Runnable r = () -> {                          // B1: L62
            String from = "lambda";                   // B3: L63
            System.out.println("Hello from " + from); // B4: L64
        };                                            // B5: L65
        r.run();                                      // B2: L66
        System.out.println("Goodbye.");               // B6: L67
    }
}


 /********** test program **********/

public class LambdaBreakpointTest extends TestScaffold {

    LambdaBreakpointTest (String args[]) {
        super(args);
    }

    public static void main(String[] args)
        throws Exception
    {
        new LambdaBreakpointTest (args).startTests();
    }

    /********** test core **********/

    protected void runTests()
        throws Exception
    {
        startToMain("LambdaBreakpointTestTarg");

        // Put a breakpoint on each location in the order they should happen
        for (int line : LambdaBreakpointTestTarg.breakpointLines) {
            System.out.println("Running to line: " + line);
            BreakpointEvent be = resumeTo("LambdaBreakpointTestTarg", line);
            int stoppedAt = be.location().lineNumber();
            System.out.println("Stopped at line: " + stoppedAt);
            if (stoppedAt != line) {
                throw new Exception("Stopped on the wrong line: "
                        + stoppedAt + " != " + line);
            }
        }

        /*
         * resume the target listening for events
         */
        listenUntilVMDisconnect();
    }
}
