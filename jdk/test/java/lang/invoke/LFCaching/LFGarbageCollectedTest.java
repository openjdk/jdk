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
 * @test LFGarbageCollectedTest
 * @bug 8046703
 * @summary Test verifies that lambda forms are garbage collected
 * @author kshefov
 * @ignore 8057020
 * @library /lib/testlibrary/jsr292 /lib/testlibrary
 * @build TestMethods
 * @build LambdaFormTestCase
 * @build LFGarbageCollectedTest
 * @run main/othervm/timeout=600 -DtestLimit=150 LFGarbageCollectedTest
 */

import java.lang.invoke.MethodHandle;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.InvocationTargetException;
import java.util.EnumSet;
import java.util.Map;

/**
 * Lambda forms garbage collection test class.
 */
public final class LFGarbageCollectedTest extends LambdaFormTestCase {

    /**
     * Constructor for a lambda forms garbage collection test case.
     *
     * @param testMethod A method from {@code j.l.i.MethodHandles} class that
     * returns a {@code j.l.i.MethodHandle} instance.
     */
    public LFGarbageCollectedTest(TestMethods testMethod) {
        super(testMethod);
    }

    @Override
    public void doTest() {
        try {
            Map<String, Object> data = getTestMethod().getTestCaseData();
            MethodHandle adapter;
            try {
                adapter = getTestMethod().getTestCaseMH(data, TestMethods.Kind.ONE);
            } catch (NoSuchMethodException ex) {
                throw new Error("Unexpected exception: ", ex);
            }
            Object lambdaForm = LambdaFormTestCase.INTERNAL_FORM.invoke(adapter);
            if (lambdaForm == null) {
                throw new Error("Unexpected error: Lambda form of the method handle is null");
            }
            ReferenceQueue rq = new ReferenceQueue();
            PhantomReference ph = new PhantomReference(lambdaForm, rq);
            lambdaForm = null;
            data = null;
            adapter = null;
            for (int i = 0; i < 1000 && !ph.isEnqueued(); i++) {
                System.gc();
            }
            if (!ph.isEnqueued()) {
                throw new AssertionError("Error: Lambda form is not garbage collected");
            }
        } catch (IllegalAccessException | IllegalArgumentException |
                InvocationTargetException ex) {
            throw new Error("Unexpected exception: ", ex);
        }
    }

    /**
     * Main routine for lambda forms garbage collection test.
     *
     * @param args Accepts no arguments.
     */
    public static void main(String[] args) {
        // The "identity", "constant", "arrayElementGetter" and "arrayElementSetter"
        // methods should be removed from this test,
        // because their lambda forms are stored in a static field and are not GC'ed.
        // There can be only a finite number of such LFs for each method,
        // so no memory leak happens.
        EnumSet<TestMethods> testMethods = EnumSet.complementOf(EnumSet.of(
                TestMethods.IDENTITY,
                TestMethods.CONSTANT,
                TestMethods.ARRAY_ELEMENT_GETTER,
                TestMethods.ARRAY_ELEMENT_SETTER));
        LambdaFormTestCase.runTests(LFGarbageCollectedTest::new, testMethods);
    }
}
