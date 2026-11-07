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
 * @summary Test that newly allocated object are initially collectable.
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

    Object[] newObjectArray() {
        System.out.println("InvokeGcDisabledTarg::newObjectArray called");
        return new Object[0];
    }

    int[] newIntArray() {
        System.out.println("InvokeGcDisabledTarg::newIntArray called");
        return new int[0];
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

    private enum InvokeType {
        VIRTUAL_INVOKE_METHOD,
        STATIC_INVOKE_METHOD,
        NEW_INSTANCE
    }

    private void testInvoke(String invokeMethod, String methodName, String methodSig, InvokeType invokeType,
                            boolean throwsException) throws Exception {
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

        println("TEST: Verify invoke disables collection of allocated object");
        obj = invoke(method, invokeType, 0, throwsException);
        forceDebuggeeGC();
        verifyNotCollected(obj);

        println("TEST: Verify enableCollection allows allocated object to be collected");
        obj.disableCollection();
        obj.enableCollection();
        forceDebuggeeGC();
        verifyCollected(obj);
    }

    private enum AllocationType {
        ALLOCATE_STRING,
        ALLOCATE_OBJECT_ARRAY,
        ALLOCATE_PRIMITIVE_ARRAY
    }

    private ObjectReference newArray(String methodName, String methodSig) throws Exception {
        Method method = findMethod(targetClass, methodName, methodSig);
        if (method == null) {
            failure("FAILED: Can't find method: \"" + methodName + methodSig + "\" for class = " + targetClass);
            return null;
        }
        ObjectReference invokeResult = (ObjectReference)thisObject.invokeMethod(mainThread, method, emptyArgs, ObjectReference.INVOKE_SINGLE_THREADED);
        ArrayType type = (ArrayType)invokeResult.referenceType();
        return type.newInstance(3);
    }
    
    private void testAllocation(AllocationType allocType, String testDescription)
            throws Exception {
        ObjectReference obj = null;
        println("*************************************************************************");
        println("* TESTING " + testDescription);
        println("*************************************************************************");
        try {
            switch (allocType) {
            case ALLOCATE_STRING:
                obj = vm().mirrorOf("Test String");
                break;
            case ALLOCATE_OBJECT_ARRAY:
                obj = newArray("newObjectArray", "()[Ljava/lang/Object;");
                break;
            case ALLOCATE_PRIMITIVE_ARRAY:
                obj = newArray("newIntArray", "()[I");
                break;
            }
        } catch (Exception ee) {
            ee.printStackTrace();
            failure("Got Exception: " + ee);
            throw ee;
        }

        println("allocated object: " + obj);
        println("TEST: Verify allocated object has collection disabled by default");
        forceDebuggeeGC();
        verifyNotCollected(obj);
        println("TEST: Verify enableCollection allows allocated object to be collected");
        obj.disableCollection();
        obj.enableCollection();
        forceDebuggeeGC();
        verifyCollected(obj);
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

        // Resume all but the main thread. We don't want to be under a suspendAll.
        mainThread.suspend();
        vm().resume();

        /*
         * We test 3 invocation APIs to make sure the results have been pinned and
         * can't initially be collected:
         * -ObjectReference.invokeMethod(): We don't differentiate between virtual and
         *  non-virtual because it uses the same code paths.
         * -ClassType.invokeMethod(): Invocation of a static method.
         * -ClassType.newInstance(): Invocation of a constructor. We don't test
         *  InterfaceType.newInstance() because it uses the same code paths.
         *
         * Each of these APIs can throw an InvocationException, which contains
         * the ObjectReference of the exception thrown by the debuggee, so we also
         * need to test each of the above 3 APIs with an exception thrown to make
         * the exception can't initially be collected.
         */
        testInvoke("ObjectReference.invokeMethod()",
                   "newObject", "()Ljava/lang/Object;",
                   InvokeType.VIRTUAL_INVOKE_METHOD, false);
        testInvoke("ObjectReference.invokeMethod()",
                   "newObject", "()Ljava/lang/Object;",
                   InvokeType.VIRTUAL_INVOKE_METHOD, true);
        testInvoke("ClassType.invokeMethod()",
                   "staticNewObject", "()Ljava/lang/Object;",
                   InvokeType.STATIC_INVOKE_METHOD,false);
        testInvoke("ClassType.invokeMethod()",
                   "staticNewObject", "()Ljava/lang/Object;",
                   InvokeType.STATIC_INVOKE_METHOD, true);
        testInvoke("ClassType.newInstance()",
                   "<init>", "()V",
                   InvokeType.NEW_INSTANCE, false);
        testInvoke("ClassType.newInstance()",
                   "<init>", "(Z)V",
                   InvokeType.NEW_INSTANCE, true);

        testAllocation(AllocationType.ALLOCATE_STRING,          "String allocation");
        testAllocation(AllocationType.ALLOCATE_OBJECT_ARRAY,    "Object array allocation");
        testAllocation(AllocationType.ALLOCATE_PRIMITIVE_ARRAY, "Primitive array allocation");
       
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
