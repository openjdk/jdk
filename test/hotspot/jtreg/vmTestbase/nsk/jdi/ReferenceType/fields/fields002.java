/*
 * Copyright (c) 2000, 2026, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdi.ReferenceType.fields;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

import com.sun.jdi.*;
import java.util.*;
import java.io.*;

/**
 * This test checks the method <code>fields()</code>
 * of the JDI interface <code>ReferenceType</code> of com.sun.jdi package
 */

public class fields002 {
    static ArgumentHandler argsHandler;
    static Log test_log_handler;

    /** The main class names of the debugger & debugee applications. */
    private final static String
        package_prefix = "nsk.jdi.ReferenceType.fields.",
//        package_prefix = "",    //  for DEBUG without package
        thisClassName = package_prefix + "fields002",
        debugeeName   = thisClassName + "a";

    /** Debugee's classes for check **/
    private final static String class_for_check = package_prefix + "fields002aClassForCheck";

    private final static String classLoaderName = package_prefix + "fields002aClassLoader";
    private final static String classFieldName = "loadedClass";

    public static void main (String argv[]) {
        int result = run(argv,System.out);
        if (result != 0) {
            throw new RuntimeException("TEST FAILED with result " + result);
        }

    }

    /**
     * JCK-like entry point to the test: perform testing, and
     * return exit code 0 (PASSED) or either 2 (FAILED).
     */
    public static int run (String argv[], PrintStream out) {

        int v_test_result = new fields002().runThis(argv,out);
        if ( v_test_result == 2/*STATUS_FAILED*/ ) {
            test_log_handler.complain("\n==> nsk/jdi/ReferenceType/fields/fields002 test FAILED");
        }
        else {
            test_log_handler.display("\n==> nsk/jdi/ReferenceType/fields/fields002 test PASSED");
        }
        return v_test_result;
    }

    /**
     * Non-static variant of the method <code>run(args,out)</code>
     */
    private int runThis (String argv[], PrintStream out) {

        argsHandler = new ArgumentHandler(argv);
        test_log_handler = new Log(out, argsHandler);
        Binder binder    = new Binder(argsHandler, test_log_handler);

        test_log_handler.display("==> nsk/jdi/ReferenceType/fields/fields002 test LOG:");
        test_log_handler.display("==> test checks fields() method of ReferenceType interface ");
        test_log_handler.display("    of the com.sun.jdi package for not prepared class\n");

        String debugee_launch_command = debugeeName;

        Debugee debugee = binder.bindToDebugee(debugee_launch_command);
        IOPipe pipe = new IOPipe(debugee);

        debugee.redirectStderr(out);
        test_log_handler.display("--> fields002: fields002a debugee launched");
        debugee.resume();

        String line = pipe.readln();
        if (line == null) {
            test_log_handler.complain("##> fields002: UNEXPECTED debugee's signal (not \"ready\") - " + line);
            return 2/*STATUS_FAILED*/;
        }
        if (!line.equals("ready")) {
            test_log_handler.complain("##> fields002: UNEXPECTED debugee's signal (not \"ready\") - " + line);
            return 2/*STATUS_FAILED*/;
        }
        else {
            test_log_handler.display("--> fields002: debugee's \"ready\" signal recieved!");
        }

        test_log_handler.display("--> fields002: check ReferenceType.fields() method for not prepared "
            + class_for_check + " class...");
        boolean class_not_found_error = false;
        boolean fields_method_error = false;

        while ( true ) {  // test body
            ReferenceType loaderRefType = debugee.classByName(classLoaderName);
            if (loaderRefType == null) {
                test_log_handler.complain("##> Could NOT FIND custom class loader: " + classLoaderName);
                class_not_found_error = true;
                break;
            }

            Field classField = loaderRefType.fieldByName(classFieldName);
            Value classValue = loaderRefType.getValue(classField);

            ClassObjectReference classObjRef = null;
            try {
                classObjRef = (ClassObjectReference)classValue;
            } catch (Exception e) {
                test_log_handler.complain("##> Unexpected exception while getting ClassObjectReference : " + e);
                class_not_found_error = true;
                break;
            }

            ReferenceType refType = classObjRef.reflectedType();
            boolean isPrep = refType.isPrepared();
            if (isPrep) {
                test_log_handler.complain("##> fields002: FAILED: isPrepared() returns for " + class_for_check + " : " + isPrep);
                class_not_found_error = true;
                break;
            } else {
                test_log_handler.display("--> fields002: isPrepared() returns for " + class_for_check + " : " + isPrep);
            }

            List fields_list = null;
            try {
                fields_list = refType.fields();
                test_log_handler.complain("##> fields002: FAILED: NO any Exception thrown!");
                test_log_handler.complain("##>            expected Exception - com.sun.jdi.ClassNotPreparedException");
                fields_method_error = true;
            }
            catch (Exception expt) {
                if (expt instanceof com.sun.jdi.ClassNotPreparedException) {
                    test_log_handler.display("--> fields002: PASSED: expected Exception thrown - " + expt.toString());
                }
                else {
                    test_log_handler.complain("##> fields002: FAILED: unexpected Exception thrown - " + expt.toString());
                    test_log_handler.complain("##>            expected Exception - com.sun.jdi.ClassNotPreparedException");
                    fields_method_error = true;
                }
            }
            break;
        }
        int v_test_result = 0/*STATUS_PASSED*/;
        if ( class_not_found_error || fields_method_error ) {
            v_test_result = 2/*STATUS_FAILED*/;
        }

        test_log_handler.display("--> fields002: waiting for debugee finish...");
        pipe.println("quit");
        debugee.waitFor();

        int status = debugee.getStatus();
        if (status != 0/*STATUS_PASSED*/ + 95/*STATUS_TEMP*/) {
            test_log_handler.complain("##> fields002: UNEXPECTED Debugee's exit status (not 95) - " + status);
            v_test_result = 2/*STATUS_FAILED*/;
        }
        else {
            test_log_handler.display("--> fields002: expected Debugee's exit status - " + status);
        }

        return v_test_result;
    }
}
