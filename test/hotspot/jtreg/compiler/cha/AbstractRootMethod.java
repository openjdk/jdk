/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @requires !vm.graal.enabled
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.misc
 *          java.base/jdk.internal.vm.annotation
 * @library /test/lib /
 * @compile Utils.java
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+PrintCompilation -XX:+PrintInlining -Xlog:dependencies=debug -verbose:class -XX:CompileCommand=quiet
 *                   -XX:CompileCommand=compileonly,*::m
 *                   -XX:CompileCommand=compileonly,*::test -XX:CompileCommand=dontinline,*::test
 *                   -Xbatch -Xmixed -XX:+WhiteBoxAPI
 *                   -XX:-TieredCompilation
 *                   -XX:-StressMethodHandleLinkerInlining
 *                      compiler.cha.AbstractRootMethod
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+PrintCompilation -XX:+PrintInlining -Xlog:dependencies=debug -verbose:class -XX:CompileCommand=quiet
 *                   -XX:CompileCommand=compileonly,*::m
 *                   -XX:CompileCommand=compileonly,*::test -XX:CompileCommand=dontinline,*::test
 *                   -Xbatch -Xmixed -XX:+WhiteBoxAPI
 *                   -XX:+TieredCompilation -XX:TieredStopAtLevel=1
 *                   -XX:-StressMethodHandleLinkerInlining
 *                      compiler.cha.AbstractRootMethod
 */
package compiler.cha;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static compiler.cha.Utils.*;

public class AbstractRootMethod {
    public static void main(String[] args) {
        run(AbstractClass.class);
        run(AbstractInterface.class);

        // Implementation limitation: CHA is not performed by C1 during inlining through MH linkers.
        if (!jdk.test.whitebox.code.Compiler.isC1Enabled()) {
            run(AbstractClass.TestMH.class, AbstractClass.class);
            run(AbstractInterface.TestMH.class, AbstractInterface.class);
        }

        System.out.println("TEST PASSED");
    }

    public static class AbstractClass extends ATest<AbstractClass.C> {
        public AbstractClass() {
            super(C.class, D.class);
        }

        interface I1 { Object m(); }
        interface I2 { default Object m() { return "I2.m"; } }

        static abstract class C            { public abstract Object m(); }

        static abstract class D  extends C {
            final Object ret = CORRECT;
            public Object m() {
                return ret;
            }
        }

        static abstract class E1 extends C { /* empty */ }
        static abstract class E2 extends C { public abstract Object m(); }
        static abstract class E3 extends C { public Object m() { return "E3.m"; } }

        static abstract class F1 extends C implements I1 { }
        static abstract class F2 extends C implements I2 { }

        static          class G  extends C { public Object m() { return CORRECT; } }

        @Override
        public Object test(C obj) {
            return obj.m(); // invokevirtual C.m()
        }

        @Override
        public void checkInvalidReceiver() {
            // nothing to do: concrete class types are enforced by the verifier
        }

        @TestCase
        public void test() {
            // 0. Trigger compilation of a megamorphic call site
            compile(megamorphic()); // Dn <: D.m <: C.m ABSTRACT
            assertCompiled();

            // Dependency: type = unique_concrete_method, context = C, method = D.m

            // 1. No invalidation: abstract classes don't participate in CHA.
            initialize(E1.class,  // ABSTRACT E1            <: C.m ABSTRACT
                       E2.class,  // ABSTRACT E2.m ABSTRACT <: C.m ABSTRACT
                       E3.class,  // ABSTRACT E3.m          <: C.m ABSTRACT
                       F1.class,  // ABSTRACT F1            <: C.m ABSTRACT, I1.m ABSTRACT
                       F2.class); // ABSTRACT F2            <: C.m ABSTRACT, I2.m DEFAULT
            assertCompiled();

            // 2. Dependency invalidation: G.m <: C.m ABSTRACT
            load(G.class);
            assertCompiled();

            // 3. Dependency invalidation: G.m <: C.m ABSTRACT
            initialize(G.class);
            assertNotCompiled();

            // 4. Recompilation: no inlining, no dependencies
            compile(megamorphic());
            call(new C() { public Object m() { return CORRECT; } }); //  Cn.m <: C.m ABSTRACT
            call(new G() { public Object m() { return CORRECT; } }); //  Gn <: G.m <: C.m ABSTRACT
            assertCompiled();
        }

        public static class TestMH extends AbstractClass {
            static final MethodHandle TEST_MH = findVirtualHelper(C.class, "m", Object.class, MethodHandles.lookup());

            @Override
            public Object test(C obj) {
                try {
                    return TEST_MH.invokeExact(obj); // invokevirtual C.m()
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
        }
    }

    public static class AbstractInterface extends ATest<AbstractInterface.C> {
        public AbstractInterface() {
            super(C.class, D.class);
        }

        interface I1 { Object m(); }
        interface I2 extends I { default Object m() { return "I2.m"; } }

        interface I { Object m(); }

        static abstract class C implements I { /* inherited from I */}

        static abstract class D  extends C {
            final Object ret = CORRECT;
            public Object m() {
                return ret;
            }
        }

        static abstract class E1 extends C { /* empty */ }
        static abstract class E2 extends C { public abstract Object m(); }
        static abstract class E3 extends C { public Object m() { return "E3.m"; } }

        static abstract class F1 extends C implements I1 { }
        static abstract class F2 extends C implements I2 { }

        static          class G  extends C { public Object m() { return CORRECT; } }

        @Override
        public Object test(C obj) {
            return obj.m(); // invokevirtual C.m()
        }

        @Override
        public void checkInvalidReceiver() {
            // nothing to do: concrete class types are enforced by the verifier
        }

        @TestCase
        public void test() {
            // 0. Trigger compilation of a megamorphic call site
            compile(megamorphic()); // Dn <: D.m <: C <: I.m ABSTRACT
            assertCompiled();

            // Dependency: type = unique_concrete_method, context = C, method = D.m

            // 1. No invalidation: abstract classes don't participate in CHA.
            initialize(E1.class,  // ABSTRACT E1            <: C <: I.m ABSTRACT
                       E2.class,  // ABSTRACT E2.m ABSTRACT <: C <: I.m ABSTRACT
                       E3.class,  // ABSTRACT E3.m          <: C <: I.m ABSTRACT
                       F1.class,  // ABSTRACT F1            <: C <: I.m ABSTRACT, I1.m ABSTRACT
                       F2.class); // ABSTRACT F2            <: C <: I.m ABSTRACT, I2.m DEFAULT
            assertCompiled();

            // 2. Dependency invalidation: G.m <: C <: I.m ABSTRACT
            load(G.class);
            assertCompiled();

            // 3. Dependency invalidation: G.m <: C <: I.m ABSTRACT
            initialize(G.class);
            assertNotCompiled();

            // 4. Recompilation: no inlining, no dependencies
            compile(megamorphic());
            call(new C() { public Object m() { return CORRECT; } }); //  Cn.m <: C <: I.m ABSTRACT
            call(new G() { public Object m() { return CORRECT; } }); //  Gn <: G.m <: C <: I.m ABSTRACT
            assertCompiled();
        }

        public static class TestMH extends AbstractInterface {
            static final MethodHandle TEST_MH = findVirtualHelper(C.class, "m", Object.class, MethodHandles.lookup());

            @Override
            public Object test(C obj) {
                try {
                    return TEST_MH.invokeExact(obj); // invokevirtual C.m()
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
        }
    }
}
