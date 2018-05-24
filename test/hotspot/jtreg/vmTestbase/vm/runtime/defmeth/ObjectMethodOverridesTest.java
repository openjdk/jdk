/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

package vm.runtime.defmeth;

import nsk.share.TestFailure;
import nsk.share.test.TestBase;
import vm.runtime.defmeth.shared.DefMethTest;
import vm.runtime.defmeth.shared.data.*;
import static vm.runtime.defmeth.shared.data.method.body.CallMethod.Invoke.*;
import static vm.runtime.defmeth.shared.data.method.body.CallMethod.IndexbyteOp.*;
import vm.runtime.defmeth.shared.data.method.body.*;
import vm.runtime.defmeth.shared.builder.TestBuilder;

/**
 * Test that default methods don't override methods inherited from Object class.
 */
public class ObjectMethodOverridesTest extends DefMethTest {

    public static void main(String[] args) {
        TestBase.runTest(new ObjectMethodOverridesTest(), args);
    }

    /* protected Object clone() */
    public void testClone() throws Exception {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("clone", "()Ljava/lang/Object;")
                    .throw_(TestFailure.class)
                .build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I)
                .concreteMethod("m", "()V")
                    // force an invokevirtual MR
                    .invoke(CallMethod.Invoke.VIRTUAL,
                            b.clazzByName("C"), b.clazzByName("C"),
                            "clone", "()Ljava/lang/Object;", METHODREF)
                .build()
            .build();

        b.test().callSite(C, C, "m", "()V")
                .throws_(CloneNotSupportedException.class)
                .done()

        .run();
    }

    /* boolean equals(Object obj) */
    public void testEquals() throws Exception {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("equals", "(Ljava/lang/Object;)Z")
                    .throw_(TestFailure.class)
                .build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        ClassLoader cl = b.build();
        Object c = cl.loadClass("C").newInstance();

        c.equals(this);
    }

    /* void finalize() */
    public void testFinalize() throws Exception {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("finalize", "()V")
                    .throw_(TestFailure.class)
                .build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I)
                .concreteMethod("m", "()V")
                    // force an invokevirtual MR
                    .invoke(CallMethod.Invoke.VIRTUAL,
                            b.clazzByName("C"), b.clazzByName("C"), "finalize", "()V", METHODREF)
                .build()
            .build();

        b.test().callSite(C, C, "m", "()V")
                .ignoreResult()
                .done()

        .run();
    }

    /* final Class<?> getClass() */
    public void testGetClass() throws Exception {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("getClass", "()Ljava/lang/Class;")
                    .sig("()Ljava/lang/Class<*>;")
                    .throw_(TestFailure.class)
                .build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        b.test().loadClass(I).throws_(VerifyError.class).done()
        .run();
    }

    /* int hashCode() */
    public void testHashCode() throws Exception {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("hashCode", "()I")
                    .throw_(TestFailure.class)
                .build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        ClassLoader cl = b.build();
        Object c = cl.loadClass("C").newInstance();

        c.hashCode();
    }


    /* final void notify() */
    public void testNotify() throws Exception {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("notify", "()V")
                    .throw_(TestFailure.class)
                .build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        b.test().loadClass(I).throws_(VerifyError.class).done()
        .run();
    }

    /* void notifyAll() */
    public void testNotifyAll() throws Exception {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("notifyAll", "()V")
                    .throw_(TestFailure.class)
                .build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        b.test().loadClass(I).throws_(VerifyError.class).done()
        .run();
    }

    /* String toString() */
    public void testToString() throws Exception {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("toString()", "()Ljava/lang/String;")
                    .throw_(TestFailure.class)
                .build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        ClassLoader cl = b.build();
        Object c = cl.loadClass("C").newInstance();

        c.toString();
    }

    /* final void wait() */
    public void testWait() throws Exception {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("wait", "()V")
                    .throw_(TestFailure.class)
                .build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        b.test().loadClass(I).throws_(VerifyError.class).done()
        .run();
    }

    /* final void wait(long timeout) */
    public void testTimedWait() throws Exception {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("wait", "(J)V")
                    .throw_(TestFailure.class)
                .build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        b.test().loadClass(I).throws_(VerifyError.class).done()
        .run();
    }

    /* final void wait(long timeout, int nanos) */
    public void testTimedWait1() throws Exception {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("wait", "(JI)V")
                    .throw_(TestFailure.class)
                .build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        b.test().loadClass(I).throws_(VerifyError.class).done()
        .run();
    }
}
