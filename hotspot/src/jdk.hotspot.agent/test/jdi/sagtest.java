/*
 * Copyright (c) 2002, 2004, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/**
 *  @test *  @bug 0000000
 *  @summary This is just an exercise of various JDI elements for use in
 *           testing the SA/JDI client
 *
 *  @author jjh
 *
 *  @run build TestScaffold VMConnection TargetListener TargetAdapter sagdoit
 *  @run compile -g -source 1.5 sagtarg.java
 *  @run main sagtest
 */
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;

    /********** target program **********/

// The target program is sagtarg.java

    /********** test program **********/

public class sagtest extends TestScaffold {
    ReferenceType targetClass;
    ThreadReference mainThread;

    sagtest (String args[]) {
        super(args);
    }

    public static void main(String[] args)      throws Exception {
        new sagtest(args).startTests();
    }

    /********** event handlers **********/


    /********** test core **********/

    protected void runTests() throws Exception {
        /*
         * Get to the top of main()
         * to determine targetClass and mainThread
         */
        BreakpointEvent bpe = startToMain("sagtarg");
        targetClass = bpe.location().declaringType();
        mainThread = bpe.thread();
        EventRequestManager erm = vm().eventRequestManager();
        stepOverLine(mainThread);  //stop on 18
        stepOverLine(mainThread);  //stop on 19
        stepOverLine(mainThread);  //stop on 20
        stepOverLine(mainThread);  //stop on 21
        stepOverLine(mainThread);  //stop on 22

        sagdoit mine = new sagdoit(vm());
        mine.doAll();

        if (!testFailed) {
            println("sagtest: passed");
        } else {
            throw new Exception("sagtest: failed");
        }
    }
}
