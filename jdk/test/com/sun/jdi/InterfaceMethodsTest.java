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

/**
 *  @test
 *  @bug 8031195
 *  @summary  JDI: Add support for static and default methods in interfaces
 *
 *  @run build TestScaffold VMConnection TargetListener TargetAdapter
 *  @run build InterfaceMethodsTest
 *  @run driver InterfaceMethodsTest
 */
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import java.util.Collections;

public class InterfaceMethodsTest extends TestScaffold {
    private static final int RESULT_A = 1;
    private static final int RESULT_B = 1;
    private static final int RESULT_TARGET = 1;
    static interface InterfaceA {
        static int staticMethodA() {
            System.out.println("-InterfaceA: static interface method A-");
            return RESULT_A;
        }
        static int staticMethodB() {
            System.out.println("-InterfaceA: static interface method B-");
            return RESULT_A;
        }
        default int defaultMethodA() {
            System.out.println("-InterfaceA: default interface method A-");
            return RESULT_A;
        }
        default int defaultMethodB() {
            System.out.println("-InterfaceA: default interface method B-");
            return RESULT_A;
        }
        default int defaultMethodC() {
            System.out.println("-InterfaceA: default interface method C-");
            return RESULT_A;
        }

        int implementedMethod();
    }

    static interface InterfaceB extends InterfaceA {
        @Override
        default int defaultMethodC() {
            System.out.println("-InterfaceB: overridden default interface method C-");
            return RESULT_B;
        }
        default int defaultMethodD() {
            System.out.println("-InterfaceB: default interface method D-");
            return RESULT_B;
        }

        static int staticMethodB() {
            System.out.println("-InterfaceB: overridden static interface method B-");
            return RESULT_B;
        }

        static int staticMethodC() {
            System.out.println("-InterfaceB: static interface method C-");
            return RESULT_B;
        }
    }

    final static class TargetClass implements InterfaceB {
        public int classMethod() {
            System.out.println("-TargetClass: class only method-");
            return RESULT_TARGET;
        }

        @Override
        public int implementedMethod() {
            System.out.println("-TargetClass: implemented non-default interface method-");
            return RESULT_TARGET;
        }

        @Override
        public int defaultMethodB() {
            System.out.println("-TargetClass: overridden default interface method D");

            return RESULT_TARGET;
        }

        public static void main(String[] args) {
            TargetClass tc = new TargetClass();
            tc.doTests(tc);
        }

        private void doTests(TargetClass ref) {
            // break
        }
    }

    public InterfaceMethodsTest(String[] args) {
        super(args);
    }

    public static void main(String[] args) throws Exception {
        new InterfaceMethodsTest(args).startTests();
    }

    private static final String TEST_CLASS_NAME = InterfaceMethodsTest.class.getName().replace('.', '/');
    private static final String TARGET_CLASS_NAME = TargetClass.class.getName().replace('.', '/');
    private static final String INTERFACEA_NAME = InterfaceA.class.getName().replace('.', '/');
    private static final String INTERFACEB_NAME = InterfaceB.class.getName().replace('.', '/');

    protected void runTests() throws Exception {
        /*
         * Get to the top of main()
         * to determine targetClass and mainThread
         */
        BreakpointEvent bpe = startToMain(TARGET_CLASS_NAME);

        bpe = resumeTo(TARGET_CLASS_NAME, "doTests", "(L" + TARGET_CLASS_NAME +";)V");

        mainThread = bpe.thread();

        StackFrame frame = mainThread.frame(0);
        ObjectReference thisObject = frame.thisObject();
        ObjectReference ref = (ObjectReference)frame.getArgumentValues().get(0);

        ReferenceType targetClass = bpe.location().declaringType();
        testImplementationClass(targetClass, thisObject);

        testInterfaceA(ref);

        testInterfaceB(ref);

        /*
         * resume the target listening for events
         */
        listenUntilVMDisconnect();

        /*
         * deal with results of test
         * if anything has called failure("foo") testFailed will be true
         */
        if (!testFailed) {
            println("InterfaceMethodsTest: passed");
        } else {
            throw new Exception("InterfaceMethodsTest: failed");
        }
    }

    private void testInterfaceA(ObjectReference ref) {
        // Test non-virtual calls on InterfaceA

        ReferenceType ifaceClass = (ReferenceType)vm().classesByName(INTERFACEA_NAME).get(0);
        /* Default method calls */

        // invoke the InterfaceA's "defaultMethodA"
        testInvokePos(ifaceClass, ref, "defaultMethodA", "()I", vm().mirrorOf(RESULT_A));

        // invoke the InterfaceA's "defaultMethodB"
        testInvokePos(ifaceClass, ref, "defaultMethodB", "()I", vm().mirrorOf(RESULT_A));

        // invoke the InterfaceA's "defaultMethodC"
        testInvokePos(ifaceClass, ref, "defaultMethodC", "()I", vm().mirrorOf(RESULT_A));

        // "defaultMethodD" from InterfaceB is not accessible from here
        testInvokeNeg(ifaceClass, ref, "defaultMethodD", "()I", vm().mirrorOf(RESULT_B),
                "Attempted to invoke non-existing method");

        // trying to invoke the asbtract method "implementedMethod"
        testInvokeNeg(ifaceClass, ref, "implementedMethod", "()I", vm().mirrorOf(TARGET_CLASS_NAME),
                "Invocation of non-default methods is not supported");


        /* Static method calls */

        // invoke interface static method A
        testInvokePos(ifaceClass, null, "staticMethodA", "()I", vm().mirrorOf(RESULT_A));

        // try to invoke static method A on the instance
        testInvokePos(ifaceClass, ref, "staticMethodA", "()I", vm().mirrorOf(RESULT_A));

        // invoke interface static method B
        testInvokePos(ifaceClass, null, "staticMethodB", "()I", vm().mirrorOf(RESULT_A));

        // try to invoke static method B on the instance
        testInvokePos(ifaceClass, ref, "staticMethodB", "()I", vm().mirrorOf(RESULT_A));
    }

    private void testInterfaceB(ObjectReference ref) {
        // Test non-virtual calls on InterfaceB
        ReferenceType ifaceClass = (ReferenceType)vm().classesByName(INTERFACEB_NAME).get(0);

        /* Default method calls */

        // invoke the inherited "defaultMethodA"
        testInvokePos(ifaceClass, ref, "defaultMethodA", "()I", vm().mirrorOf(RESULT_A));

        // invoke the inherited "defaultMethodB"
        testInvokePos(ifaceClass, ref, "defaultMethodB", "()I", vm().mirrorOf(RESULT_A));

        // invoke the inherited and overridden "defaultMethodC"
        testInvokePos(ifaceClass, ref, "defaultMethodC", "()I", vm().mirrorOf(RESULT_B));

        // invoke InterfaceB only "defaultMethodD"
        testInvokePos(ifaceClass, ref, "defaultMethodD", "()I", vm().mirrorOf(RESULT_B));

        // "implementedMethod" is not present in InterfaceB
        testInvokeNeg(ifaceClass, ref, "implementedMethod", "()I", vm().mirrorOf(RESULT_TARGET),
                "Invocation of non-default methods is not supported");


        /* Static method calls*/

        // "staticMethodA" must not be inherited by InterfaceB
        testInvokeNeg(ifaceClass, null, "staticMethodA", "()I", vm().mirrorOf(RESULT_A),
                "Static interface methods are not inheritable");

        // however it is possible to call "staticMethodA" on the actual instance
        testInvokeNeg(ifaceClass, ref, "staticMethodA", "()I", vm().mirrorOf(RESULT_A),
                "Static interface methods are not inheritable");

        // "staticMethodB" is overridden in InterfaceB
        testInvokePos(ifaceClass, null, "staticMethodB", "()I", vm().mirrorOf(RESULT_B));

        // the instance invokes the overriden form of "staticMethodB" from InterfaceB
        testInvokePos(ifaceClass, ref, "staticMethodB", "()I", vm().mirrorOf(RESULT_B));

        // "staticMethodC" is present only in InterfaceB
        testInvokePos(ifaceClass, null, "staticMethodC", "()I", vm().mirrorOf(RESULT_B));

        // "staticMethodC" should be reachable from the instance too
        testInvokePos(ifaceClass, ref, "staticMethodC", "()I", vm().mirrorOf(RESULT_B));
    }

    private void testImplementationClass(ReferenceType targetClass, ObjectReference thisObject) {
        // Test invocations on the implementation object

        /* Default method calls */

        // "defaultMethodA" is accessible and not overridden
        testInvokePos(targetClass, thisObject, "defaultMethodA", "()I", vm().mirrorOf(RESULT_TARGET));

        // "defaultMethodB" is accessible and overridden in TargetClass
        testInvokePos(targetClass, thisObject, "defaultMethodB", "()I", vm().mirrorOf(RESULT_TARGET));

        // "defaultMethodC" is accessible and overridden in InterfaceB
        testInvokePos(targetClass, thisObject, "defaultMethodC", "()I", vm().mirrorOf(RESULT_TARGET));

        // "defaultMethodD" is accessible
        testInvokePos(targetClass, thisObject, "defaultMethodD", "()I", vm().mirrorOf(RESULT_TARGET));


        /* Non-default instance method calls */

        // "classMethod" declared in TargetClass is accessible
        testInvokePos(targetClass, thisObject, "classMethod", "()I", vm().mirrorOf(RESULT_TARGET));

        // the abstract "implementedMethod" has been implemented in TargetClass
        testInvokePos(targetClass, thisObject, "implementedMethod", "()I", vm().mirrorOf(RESULT_TARGET));


        /* Static method calls */

        // All the static methods declared by the interfaces are not reachable from the instance of the implementor class
        testInvokeNeg(targetClass, thisObject, "staticMethodA", "()I", vm().mirrorOf(RESULT_A),
                "Static interface methods are not inheritable");

        testInvokeNeg(targetClass, thisObject, "staticMethodB", "()I", vm().mirrorOf(RESULT_B),
                "Static interface methods are not inheritable");

        testInvokeNeg(targetClass, thisObject, "staticMethodC", "()I", vm().mirrorOf(RESULT_B),
                "Static interface methods are not inheritable");

        // All the static methods declared by the interfaces are not reachable through the implementor class
        testInvokeNeg(targetClass, null, "staticMethodA", "()I", vm().mirrorOf(RESULT_A),
                "Static interface methods are not inheritable");

        testInvokeNeg(targetClass, null, "staticMethodB", "()I", vm().mirrorOf(RESULT_B),
                "Static interface methods are not inheritable");

        testInvokeNeg(targetClass, null, "staticMethodC", "()I", vm().mirrorOf(RESULT_B),
                "Static interface methods are not inheritable");
    }

    private void testInvokePos(ReferenceType targetClass, ObjectReference ref, String methodName,
                               String methodSig, Value value) {
        logInvocation(ref, methodName, methodSig, targetClass);
        try {
            invoke(targetClass, ref, methodName, methodSig, value);
            System.err.println("--- PASSED");
        } catch (Exception e) {
            System.err.println("--- FAILED");
            failure("FAILED: Invocation failed with error message " + e.getLocalizedMessage());
        }
    }

    private void testInvokeNeg(ReferenceType targetClass, ObjectReference ref, String methodName,
                               String methodSig, Value value, String msg) {
        logInvocation(ref, methodName, methodSig, targetClass);
        try {
            invoke(targetClass, ref, methodName, methodSig, value);
            System.err.println("--- FAILED");
            failure("FAILED: " + msg);
        } catch (Exception e) {
            System.err.println("--- PASSED");

        }
    }

    private void invoke(ReferenceType targetClass, ObjectReference ref, String methodName,
                        String methodSig, Value value)
    throws Exception {
        Method method = getMethod(targetClass, methodName, methodSig);
        if (method == null) {
            throw new Exception("Can't find method: " + methodName  + " for class = " + targetClass);
        }

        println("Invoking " + (method.isAbstract() ? "abstract " : " ") + "method: " + method);

        Value returnValue = null;
        if (ref != null) {
            returnValue = invokeInstance(ref, method);
        } else {
            returnValue = invokeStatic(targetClass, method);
        }

        println("        return val = " + returnValue);
        // It has to be the same value as what we passed in!
        if (returnValue.equals(value)) {
            println("         " + method.name() + " return value matches: "
                    + value);
        } else {
            if (value != null) {
                throw new Exception(method.name() + " returned: " + returnValue +
                                    " expected: " + value );
            } else {
                println("         " + method.name() + " return value : " + returnValue);
            }

        }
    }

    private Value invokeInstance(ObjectReference ref, Method method) throws Exception {
        return ref.invokeMethod(mainThread, method, Collections.emptyList(), ObjectReference.INVOKE_NONVIRTUAL);
    }

    private Value invokeStatic(ReferenceType refType, Method method) throws Exception {
        if (refType instanceof ClassType) {
            return ((ClassType)refType).invokeMethod(mainThread, method, Collections.emptyList(), ObjectReference.INVOKE_NONVIRTUAL);
        } else {
            return ((InterfaceType)refType).invokeMethod(mainThread, method, Collections.emptyList(), ObjectReference.INVOKE_NONVIRTUAL);
        }
    }

    private Method getMethod(ReferenceType rt, String name, String signature) {
        if (rt == null) return null;
        Method m = findMethod(rt, name, signature);
        if (m == null) {
            if (rt instanceof ClassType) {
                for (Object ifc : ((ClassType)rt).interfaces()) {
                    m = getMethod((ReferenceType)ifc, name, signature);
                    if (m != null) {
                        break;
                    }
                }
                if (m == null) {
                    m = getMethod(((ClassType)rt).superclass(), name, signature);
                } else {
                    if (m.isStatic()) {
                        // interface static methods are not inherited
                        m = null;
                    }
                }
            } else if (rt instanceof InterfaceType) {
                for(Object ifc : ((InterfaceType)rt).superinterfaces()) {
                    m = getMethod((ReferenceType)ifc, name, signature);
                    if (m != null) {
                        if (m.isStatic()) {
                            // interface static methods are not inherited
                            m = null;
                        }
                        break;
                    }
                }
            }
        }

        return m;
    }

    private void logInvocation(ObjectReference ref, String methodName, String methodSig, ReferenceType targetClass) {
        if (ref != null) {
            System.err.println("Invoking: " + ref.referenceType().name() + "." +
                    methodName + methodSig + " with target of type " +
                    targetClass.name());
        } else {
            System.err.println("Invoking static : " + targetClass.name() + "." +
                    methodName + methodSig);
        }
    }
}



