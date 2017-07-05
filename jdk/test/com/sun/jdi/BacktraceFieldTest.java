/*
 * Copyright (c) 2001, 2002, Oracle and/or its affiliates. All rights reserved.
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
 *  @test
 *  @bug 4446677
 *  @summary debuggee crashes when debugging under jbuilder
 *
 *  @author jjh
 *
 *  @run build TestScaffold VMConnection TargetListener TargetAdapter
 *  @run compile -g BacktraceFieldTest.java
 *  @run main BacktraceFieldTest
 */

/*
 * The fix for this bug filters out the backtrace field from the list
 * of fields for java.lang.Throwable.
 * This test verifies that this really happens, and also verifies that the fix
 * doesn't incorrectly discard other fields.
 */

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;

/********** target program **********/

class Testy {
    /*
     * This is used to verify that the fix doesn't filter out fields that it
     * shouldn't.  7 is an abitrary number, and this isn't a definitive
     * test; the fix could conceivably filter out the 89th field of a class
     * named Foo.
     * To verify that this part of this test works, first uncomment the field8
     * line and verify that the test fails, and then rename a field to xxx and
     * verify that the test fails.
     */
    int field1;
    int field2;
    int field3;
    int field4;
    int field5;
    int field6;
    final static int field7 = 7;  // Value is the number of fields.
    //int field8;

    Testy() {
    }
}


class BacktraceFieldTarg {
    public static void gus() {
    }

    public static void main(String[] args) {
        Testy myTesty = new Testy();
        try {
            throw new RuntimeException("jjException");
        } catch (Exception ee) {
            gus();
            System.out.println("debuggee: Exception: " + ee);
        }
    }
}

/********** test program **********/

public class BacktraceFieldTest extends TestScaffold {
    ThreadReference mainThread;

    BacktraceFieldTest (String args[]) {
        super(args);
    }

    public static void main(String[] args)      throws Exception {
        new BacktraceFieldTest(args).startTests();
    }

    /********** test core **********/

    protected void runTests() throws Exception {
        /*
         * Get to the top of gus()
         * to determine mainThread
         */
        BreakpointEvent bpe = startTo("BacktraceFieldTarg", "gus", "()V");
        mainThread = bpe.thread();

        /*
         * We are now one frame below the exception frame that contains
         * our ee var.
         */
        StackFrame myFrame = mainThread.frame(1);

        LocalVariable lv = myFrame.visibleVariableByName("ee");
        println("BT: lv = " + lv);
        println("BT: lvType = " + lv.typeName());

        List allFields = ((ReferenceType)(lv.type())).allFields();
        println("BT: allFields = " + allFields);

        /*
         * Search through the fields of ee to verify that
         * java.lang.Throwable.backtrace isn't there.
         */
        Iterator iter = allFields.iterator();
        while(iter.hasNext()) {
            Field ff = (Field)iter.next();
            if (ff.toString().equals("java.lang.Throwable.backtrace")) {
                failure("ERROR: java.lang.Throwable.backtrace field not filtered out.");

                /*
                 * If you want to experience the segv this bug causes, change
                 * this test to 1 == 1 and run it with jdk 1.4, build 74 or earlier
                 */
                if (1 == 0) {
                    // The following code will show the segv that this can cause.
                    ObjectReference myVal = (ObjectReference)myFrame.getValue(lv);
                    println("BT: myVal = " + myVal);

                    ArrayReference backTraceVal = null;
                    backTraceVal = (ArrayReference)myVal.getValue(ff);
                    println("BT: backTraceVal = " + backTraceVal);

                    ArrayReference secondVal = (ArrayReference)backTraceVal.getValue(1);
                    println("BT: secondVal = " + secondVal);

                    Object x2Val = (Object)secondVal.getValue(0);
                    println("BT: x2Val = " + x2Val);

                    ArrayReference firstVal = (ArrayReference)backTraceVal.getValue(0);
                    println("BT: firstVal = " + firstVal);

                    // The segv happens here.
                    Object xVal = (Object)firstVal.getValue(0);
                    println("BT: xVal = " + xVal);
                }
                break;
            }
        }

        // Next, verify that we don't accidently discard a field that we shouldn't

        if (!testFailed) {
            lv = myFrame.visibleVariableByName("myTesty");

            allFields = ((ReferenceType)(lv.type())).allFields();
            println("BT: allFields = " + allFields);

            if (allFields.size() != Testy.field7) {
                failure("ERROR: wrong number of fields; expected " + Testy.field7 + ", Got " + allFields.size());
            } else {
                iter = allFields.iterator();
                while(iter.hasNext()) {
                    String fieldName = ((Field)iter.next()).toString();
                    if (!fieldName.startsWith("Testy.field", 0)) {
                        failure("ERROR: Found bogus field: " + fieldName.toString());
                    }
                }
            }
        }

        listenUntilVMDisconnect();

        /*
         * deal with results of test
         * if anything has called failure("foo") testFailed will be true
         */
        if (!testFailed) {
            println("BacktraceFieldTest: passed");
        } else {
            throw new Exception("BacktraceFieldTest: failed");
        }
    }
}
