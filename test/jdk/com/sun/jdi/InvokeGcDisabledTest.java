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

/**
 * @test
 * @bug 8311176
 * @summary Test INVOKE_DISABLE_COLLECTION flag
 * @library /test/lib
 * @run build TestScaffold VMConnection TargetListener TargetAdapter jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run compile -g InvokeGcDisabledTest.java
 * @run driver InvokeGcDisabledTest
 * @run driver InvokeGcDisabledTest stress
 */
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;

import jdk.test.whitebox.WhiteBox;

    /********** target program **********/

class InvokeGcDisabledTarg {
    static boolean stressMode = false;

    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static volatile boolean stop = false;

    public static void main(String[] args){
        System.out.println("Howdy!");
        if (args.length == 1 && "stress".equals(args[0])) {
            System.out.println("debuggee stress mode");
            stressMode = true;
        }
        if (stressMode) {
            Thread gcThread = new Thread(() -> {
                    while (!stop) WB.fullGC();
            });
            gcThread.start();
        }
        (new InvokeGcDisabledTarg()).sayHi();
        stop = true;
    }

    void sayHi() {
    }

    InvokeGcDisabledTarg() {
        System.out.println("InvokeGcDisabledTarg::InvokeGcDisabledTarg called");
    }

    InvokeGcDisabledTarg(boolean ignore) {
        System.out.println("InvokeGcDisabledTarg::InvokeGcDisabledTarg for exception called");
        throw new RuntimeException("Exception from debuggee");
    }

    Object newObject() {
        System.out.println("InvokeGcDisabledTarg::newObject called");
        return new Object();
    }

    static Object staticNewObject() {
        System.out.println("InvokeGcDisabledTarg::staticNewObject called");
        return new Object();
    }

    void throwException() {
        System.out.println("InvokeGcDisabledTarg::throwException called");
        throw new RuntimeException("Exception from debuggee");
    }

    void throwStaticException() {
        System.out.println("InvokeGcDisabledTarg::throwStaticException called");
        throw new RuntimeException("Exception from debuggee");
    }

    void fullGC() {
        WB.fullGC();
    }

}

    /********** test program **********/

public class InvokeGcDisabledTest extends TestScaffold {
    static boolean stressMode = false;
    ClassType targetClass;
    ThreadReference mainThread;
    ObjectReference thisObject;
    List<Value> emptyArgs;
    List<Value> booleanArg;

    Method forceDebuggeeGCMethod = null;

    InvokeGcDisabledTest (String args[]) {
        super(args);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            if ("stress".equals(args[0])) {
                System.out.println("debugger stress mode");
                stressMode = true;
            } else {
                throw new RuntimeException("bad argument: " + args[0]);
            }
        }
        try {
            new InvokeGcDisabledTest(args).startTests();
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            throw t;
        }
    }

    /********** test assist **********/

    void forceDebuggeeGC() throws Exception {
        if (forceDebuggeeGCMethod == null) {
            forceDebuggeeGCMethod = findMethod(targetClass, "fullGC", "()V");
            if (forceDebuggeeGCMethod == null) {
                failure("FAILED: Can't find method: \"fullGC\" for class = " + targetClass);
                return;
            }
        }

        println("Forcing debuggee full GC");
        thisObject.invokeMethod(mainThread, forceDebuggeeGCMethod, emptyArgs, ObjectReference.INVOKE_SINGLE_THREADED);
    }

    ObjectReference invoke(Method method, InvokeType invokeType, int options, boolean throwsException)
            throws Exception {
        Value returnValue = null;
        options = options | ObjectReference.INVOKE_SINGLE_THREADED;

        try {
            switch (invokeType) {
            case VIRTUAL_INVOKE_METHOD:
                returnValue = thisObject.invokeMethod(mainThread, method, emptyArgs, options);
                break;
            case STATIC_INVOKE_METHOD:
                returnValue = targetClass.invokeMethod(mainThread, method, emptyArgs, options);
                break;
            case NEW_INSTANCE:
                returnValue = targetClass.newInstance(mainThread, method,
                                                      throwsException ? booleanArg : emptyArgs, options);
                break;
            }
        } catch (InvocationException ie) {
            if (!throwsException) {
                ie.printStackTrace();
                failure("Got Exception: " + ie);
                throw ie;
            } else {
                println("Got expected InvocationException: " + ie.exception());
                returnValue = ie.exception();
            }
        } catch (Exception ee) {
            ee.printStackTrace();
            failure("Got Exception: " + ee);
            throw ee;
        }
        println("        return val = " + returnValue);
        return (ObjectReference)returnValue;
    }

    void verifyCollected(ObjectReference obj) {
        println("Verifying object is collected: " + obj);
        if (!obj.isCollected()) {
            failure("FAILED: object not collected: " + obj);
        }
    }

    void verifyNotCollected(ObjectReference obj) {
        println("Verifying object is not collected: " + obj);
        if (obj.isCollected()) {
            failure("FAILED: object collected: " + obj);
        }
    }

    private void testInvoke(String invokeMethod, String methodName, String methodSig, InvokeType invokeType,
                            boolean throwsException, boolean stressMode) throws Exception {
        ObjectReference obj;
        Method method = findMethod(targetClass, methodName, methodSig);
        if (method == null) {
            failure("FAILED: Can't find method: \"" + methodName + methodSig + "\" for class = " + targetClass);
            return;
        }

        println("*************************************************************************");
        println("* TESTING " + invokeMethod +" on " + targetClass.name() + "." + methodName + methodSig);
        println("* throwsException=" + throwsException + " stressMode=" + stressMode);
        println("*************************************************************************");

        if (!stressMode) {
            // Theoretically this could generate an ObjectCollectedException, but shouldn't
            // unless we are running in stress mode to trigger a lot of GCs.
            println("TEST: Verify disableCollection works on allocated object");
            obj = invoke(method, invokeType, 0, throwsException);
            obj.disableCollection();
            forceDebuggeeGC();
            verifyNotCollected(obj);

            println("TEST: Verify enableCollection allows allocated object to be collected");
            obj.enableCollection();
            forceDebuggeeGC();
            verifyCollected(obj);
        }

        println("TEST: Verify INVOKE_DISABLE_COLLECTION disables collection of allocated object");
        obj = invoke(method, invokeType, ObjectReference.INVOKE_DISABLE_COLLECTION, throwsException);
        forceDebuggeeGC();
        verifyNotCollected(obj);

        println("TEST: Verify enableCollection allows allocated object to be collected");
        obj.enableCollection();
        forceDebuggeeGC();
        verifyCollected(obj);
    }

    private enum InvokeType {
        VIRTUAL_INVOKE_METHOD,
        STATIC_INVOKE_METHOD,
        NEW_INSTANCE
    }

    /********** test core **********/

    protected void runTests() throws Exception {
        ObjectReference obj;

        enableWhiteBoxAPI(); // Allow debuggee to use WhiteBoxAPI

        BreakpointEvent bpe = startTo("InvokeGcDisabledTarg", "sayHi", "()V");
        targetClass = (ClassType)bpe.location().declaringType();
        mainThread = bpe.thread();
        StackFrame frame = mainThread.frame(0);
        thisObject = frame.thisObject();

        emptyArgs = new ArrayList(0);
        booleanArg = Arrays.asList(new Value[]{vm().mirrorOf(true)});

        mainThread.suspend();
        vm().resume();

        /*
         * We test 3 invocation APIs to make sure that using the INVOKE_DISABLE_COLLECTION
         * flag prevents the method result from being collected.
         * -ObjectReference.invokeMethod(): We don't differentiate between virtual and
         *  non-virtual because it uses the same code paths.
         * -ClassType.invokeMethod(): Invocation of a static method.
         * -ClassType.newInstance(): Invocation of a constructor. We don't test
         *  InterfaceType.newInstance() because it uses the same code paths.
         *
         * Each of these APIs can throw an InvocationException, which contains an
         * the ObjectReference of the exception thrown by the debuggee, so we also
         * need to test each of the above 3 APIs with an exception thrown to make
         * sure the INVOKE_DISABLE_COLLECTION flag also works on the exception object.
         */

        testInvoke("ObjectReference.invokeMethod()",
                   "newObject", "()Ljava/lang/Object;",
                   InvokeType. VIRTUAL_INVOKE_METHOD, false, stressMode);
        testInvoke("ObjectReference.invokeMethod()",
                   "newObject", "()Ljava/lang/Object;",
                   InvokeType.VIRTUAL_INVOKE_METHOD, true, stressMode);
        testInvoke("ClassType.invokeMethod()",
                   "staticNewObject", "()Ljava/lang/Object;",
                   InvokeType.STATIC_INVOKE_METHOD,false, stressMode);
        testInvoke("ClassType.invokeMethod()",
                   "staticNewObject", "()Ljava/lang/Object;",
                   InvokeType.STATIC_INVOKE_METHOD, true, stressMode);
        testInvoke("ClassType.newInstance()",
                   "<init>", "()V",
                   InvokeType.NEW_INSTANCE, false, stressMode);
        testInvoke("ClassType.newInstance()",
                   "<init>", "(Z)V",
                   InvokeType.NEW_INSTANCE, true, stressMode);

        /*
         * resume the target so it can exit.
         */
        mainThread.resume();
        listenUntilVMDisconnect();

        /*
         * Deal with results of test.
         * Of anything has called failure("foo") testFailed will be true.
         */
        if (!testFailed) {
            println("InvokeGcDisabledTest: passed");
        } else {
            throw new Exception("InvokeGcDisabledTest: failed");
        }
    }
}
