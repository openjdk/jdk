/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *                                sun.hotspot.WhiteBox$WhiteBoxPermission
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+PrintCompilation -XX:+PrintInlining -XX:+TraceDependencies -verbose:class -XX:CompileCommand=quiet
 *                   -XX:CompileCommand=compileonly,*::test -XX:CompileCommand=compileonly,*::m -XX:CompileCommand=dontinline,*::test
 *                   -Xbatch -XX:+WhiteBoxAPI -Xmixed
 *                   -XX:-TieredCompilation
 *                      compiler.cha.StrengthReduceInterfaceCall
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+PrintCompilation -XX:+PrintInlining -XX:+TraceDependencies -verbose:class -XX:CompileCommand=quiet
 *                   -XX:CompileCommand=compileonly,*::test -XX:CompileCommand=compileonly,*::m -XX:CompileCommand=dontinline,*::test
 *                   -Xbatch -XX:+WhiteBoxAPI -Xmixed
 *                   -XX:+TieredCompilation -XX:TieredStopAtLevel=1
 *                      compiler.cha.StrengthReduceInterfaceCall
 */
package compiler.cha;

import jdk.internal.misc.Unsafe;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.vm.annotation.DontInline;
import sun.hotspot.WhiteBox;
import sun.hotspot.code.NMethod;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.Callable;

import static jdk.test.lib.Asserts.*;
import static jdk.internal.org.objectweb.asm.ClassWriter.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

public class StrengthReduceInterfaceCall {
    public static void main(String[] args) {
        run(ObjectToString.class);
        run(ObjectHashCode.class);
        run(TwoLevelHierarchyLinear.class);
        run(ThreeLevelHierarchyLinear.class);
        run(ThreeLevelHierarchyAbstractVsDefault.class);
        run(ThreeLevelDefaultHierarchy.class);
        run(ThreeLevelDefaultHierarchy1.class);
    }

    public static class ObjectToString extends ATest<ObjectToString.I> {
        public ObjectToString() { super(I.class, C.class); }

        interface J           { String toString(); }
        interface I extends J {}

        static class C implements I {}

        interface K1 extends I {}
        interface K2 extends I { String toString(); } // K2.tS() ABSTRACT
        // interface K3 extends I { default String toString() { return "K3"; } // K2.tS() DEFAULT

        static class D implements I { public String toString() { return "D"; }}

        static class DJ1 implements J {}
        static class DJ2 implements J { public String toString() { return "DJ2"; }}

        @Override
        public Object test(I i) { return ObjectToStringHelper.test(i); /* invokeinterface I.toString() */ }

        @TestCase
        public void testMono() {
            // 0. Trigger compilation of a monomorphic call site
            compile(monomophic()); // C1 <: C <: intf I <: intf J <: Object.toString()
            assertCompiled();

            // Dependency: none

            call(new C() { public String toString() { return "Cn"; }}); // Cn.tS <: C.tS <: intf I
            assertCompiled();
        }

        @TestCase
        public void testBi() {
            // 0. Trigger compilation of a bimorphic call site
            compile(bimorphic()); // C1 <: C <: intf I <: intf J <: Object.toString()
            assertCompiled();

            // Dependency: none

            call(new C() { public String toString() { return "Cn"; }}); // Cn.tS <: C.tS <: intf I
            assertCompiled();
        }

        @TestCase
        public void testMega() {
            // 0. Trigger compilation of a megamorphic call site
            compile(megamorphic()); // C1,C2,C3 <: C <: intf I <: intf J <: Object.toString()
            assertCompiled();

            // Dependency: none
            // compiler.cha.StrengthReduceInterfaceCall$ObjectToString::test (5 bytes)
            //     @ 1   compiler.cha.StrengthReduceInterfaceCall$ObjectToStringHelper::test (7 bytes)   inline (hot)
            //       @ 1   java.lang.Object::toString (36 bytes)   virtual call

            // No dependency - no invalidation
            repeat(100, () -> call(new C(){})); // Cn <: C <: intf I
            assertCompiled();

            initialize(K1.class,   // intf  K1             <: intf I <: intf J
                       K2.class,   // intf  K2.tS ABSTRACT <: intf I <: intf J
                       DJ1.class,  //      DJ1                       <: intf J
                       DJ2.class); //      DJ2.tS                    <: intf J
            assertCompiled();

            initialize(D.class); // D.tS <: intf I <: intf J
            assertCompiled();

            call(new C() { public String toString() { return "Cn"; }}); // Cn.tS <: C.tS <: intf I
            assertCompiled();
        }

        @Override
        public void checkInvalidReceiver() {
            shouldThrow(IncompatibleClassChangeError.class, () -> {
                I o = (I) unsafeCastMH(I.class).invokeExact(new Object()); // unrelated
                test(o);
            });
            assertCompiled();

            shouldThrow(IncompatibleClassChangeError.class, () -> {
                I j = (I) unsafeCastMH(I.class).invokeExact((Object)new J() {}); // super interface
                test(j);
            });
            assertCompiled();
        }
    }

    public static class ObjectHashCode extends ATest<ObjectHashCode.I> {
        public ObjectHashCode() { super(I.class, C.class); }

        interface J {}
        interface I extends J {}

        static class C implements I {}

        interface K1 extends I {}
        interface K2 extends I { int hashCode(); } // K2.hC() ABSTRACT
        // interface K3 extends I { default int hashCode() { return CORRECT; } // K2.hC() DEFAULT

        static class D implements I { public int hashCode() { return super.hashCode(); }}

        static class DJ1 implements J {}
        static class DJ2 implements J { public int hashCode() { return super.hashCode(); }}

        @Override
        public Object test(I i) {
            return ObjectHashCodeHelper.test(i); /* invokeinterface I.hashCode() */
        }

        @TestCase
        public void testMono() {
            // 0. Trigger compilation of a monomorphic call site
            compile(monomophic()); // C1 <: C <: intf I <: intf J <: Object.hashCode()
            assertCompiled();

            // Dependency: none

            call(new C() { public int hashCode() { return super.hashCode(); }}); // Cn.hC <: C.hC <: intf I
            assertCompiled();
        }

        @TestCase
        public void testBi() {
            // 0. Trigger compilation of a bimorphic call site
            compile(bimorphic()); // C1 <: C <: intf I <: intf J <: Object.toString()
            assertCompiled();

            // Dependency: none

            call(new C() { public int hashCode() { return super.hashCode(); }}); // Cn.hC <: C.hC <: intf I
            assertCompiled();
        }

        @TestCase
        public void testMega() {
            // 0. Trigger compilation of a megamorphic call site
            compile(megamorphic()); // C1,C2,C3 <: C <: intf I <: intf J <: Object.hashCode()
            assertCompiled();

            // Dependency: none

            // No dependency - no invalidation
            repeat(100, () -> call(new C(){})); // Cn <: C <: intf I
            assertCompiled();

            initialize(K1.class,   // intf  K1             <: intf I <: intf J
                       K2.class,   // intf  K2.hC ABSTRACT <: intf I <: intf J
                       DJ1.class,  //      DJ1                       <: intf J
                       DJ2.class); //      DJ2.hC                    <: intf J
            assertCompiled();

            initialize(D.class); // D.hC <: intf I <: intf J
            assertCompiled();

            call(new C() { public int hashCode() { return super.hashCode(); }}); // Cn.hC <: C.hC <: intf I
            assertCompiled();
        }

        @Override
        public void checkInvalidReceiver() {
            shouldThrow(IncompatibleClassChangeError.class, () -> {
                I o = (I) unsafeCastMH(I.class).invokeExact(new Object()); // unrelated
                test(o);
            });
            assertCompiled();

            shouldThrow(IncompatibleClassChangeError.class, () -> {
                I j = (I) unsafeCastMH(I.class).invokeExact((Object)new J() {}); // super interface
                test(j);
            });
            assertCompiled();
        }
    }

    public static class TwoLevelHierarchyLinear extends ATest<TwoLevelHierarchyLinear.I> {
        public TwoLevelHierarchyLinear() { super(I.class, C.class); }

        interface J { default Object m() { return WRONG; } }

        interface I extends J { Object m(); }
        static class C implements I { public Object m() { return CORRECT; }}

        interface K1 extends I {}
        interface K2 extends I { Object m(); }
        interface K3 extends I { default Object m() { return WRONG; }}

        static class D implements I { public Object m() { return WRONG;   }}

        static class DJ1 implements J {}
        static class DJ2 implements J { public Object m() { return WRONG; }}

        @DontInline
        public Object test(I i) {
            return i.m();
        }

        @TestCase
        public void testMega1() {
            // 0. Trigger compilation of a megamorphic call site
            compile(megamorphic()); // C1,C2,C3 <: C.m <: intf I.m ABSTRACT <: intf J.m ABSTRACT
            assertCompiled();

            // Dependency: type = unique_concrete_method, context = I, method = C.m

            checkInvalidReceiver(); // ensure proper type check is preserved

            // 1. No deoptimization/invalidation on not-yet-seen receiver
            repeat(100, () -> call(new C(){})); // Cn <: C.m <: intf I.m ABSTRACT <: intf J.m DEFAULT
            assertCompiled();

            // 2. No dependency invalidation on class loading of unrelated classes: different context
            initialize(K1.class,   // intf  K1            <: intf I.m ABSTRACT <: intf J.m DEFAULT
                       K2.class,   // intf  K2.m ABSTRACT <: intf I.m ABSTRACT <: intf J.m DEFAULT
                       DJ1.class,  //      DJ1                                 <: intf J.m DEFAULT
                       DJ2.class); //      DJ2.m                               <: intf J.m DEFAULT
            assertCompiled();

            // 3. Dependency invalidation on D <: I
            initialize(D.class); // D.m <: intf I.m ABSTRACT <: intf J.m DEFAULT
            assertNotCompiled();

            // 4. Recompilation: no inlining, no dependencies
            compile(megamorphic());
            call(new C() { public Object m() { return CORRECT; }}); // Cn.m <: C.m <: intf I.m ABSTRACT <: intf J.m DEFAULT
            assertCompiled();

            checkInvalidReceiver(); // ensure proper type check on receiver is preserved
        }

        @TestCase
        public void testMega2() {
            // 0. Trigger compilation of a megamorphic call site
            compile(megamorphic()); // C1,C2,C3 <: C.m <: intf I.m ABSTRACT <: intf J.m DEFAULT
            assertCompiled();

            // Dependency: type = unique_concrete_method, context = I, method = C.m

            checkInvalidReceiver(); // ensure proper type check on receiver is preserved

            // 1. Dependency invalidation
            initialize(K3.class); // intf K3.m DEFAULT <: intf I.m ABSTRACT <: intf J.m DEFAULT
            assertNotCompiled();

            // 2. Recompilation: still inlines
            // FIXME: no default method support in CHA yet
            compile(megamorphic());
            call(new K3() { public Object m() { return CORRECT; }}); // K3n.m <: intf K3.m DEFAULT <: intf I.m ABSTRACT <: intf J.m ABSTRACT
            assertNotCompiled();

            // 3. Recompilation: no inlining, no dependencies
            compile(megamorphic());
            call(new K3() { public Object m() { return CORRECT; }}); // Kn.m <: intf K3.m DEFAULT  <: intf I.m ABSTRACT <: intf J.m DEFAULT
            assertCompiled();

            checkInvalidReceiver(); // ensure proper type check on receiver is preserved
        }

        @Override
        public void checkInvalidReceiver() {
            shouldThrow(IncompatibleClassChangeError.class, () -> {
                I o = (I) unsafeCastMH(I.class).invokeExact(new Object()); // unrelated
                test(o);
            });
            assertCompiled();

            shouldThrow(IncompatibleClassChangeError.class, () -> {
                I j = (I) unsafeCastMH(I.class).invokeExact((Object)new J() {}); // super interface
                test(j);
            });
            assertCompiled();
        }
    }

    public static class ThreeLevelHierarchyLinear extends ATest<ThreeLevelHierarchyLinear.I> {
        public ThreeLevelHierarchyLinear() { super(I.class, C.class); }

        interface J           { Object m(); }
        interface I extends J {}

        interface K1 extends I {}
        interface K2 extends I { Object m(); }
        interface K3 extends I { default Object m() { return WRONG; }}

        static class C  implements I { public Object m() { return CORRECT; }}

        static class DI implements I { public Object m() { return WRONG;   }}
        static class DJ implements J { public Object m() { return WRONG;   }}

        @DontInline
        public Object test(I i) {
            return i.m(); // I <: J.m ABSTRACT
        }

        @TestCase
        public void testMega1() {
            // 0. Trigger compilation of a megamorphic call site
            compile(megamorphic()); // C1,C2,C3 <: C.m <: intf I <: intf J.m ABSTRACT
            assertCompiled();

            // Dependency: type = unique_concrete_method, context = I, method = C.m

            checkInvalidReceiver(); // ensure proper type check on receiver is preserved

            // 1. No deoptimization/invalidation on not-yet-seen receiver
            repeat(100, () -> call(new C(){})); // Cn <: C.m <: intf I
            assertCompiled(); // No deopt on not-yet-seen receiver

            // 2. No dependency invalidation: different context
            initialize(DJ.class,  //      DJ.m                    <: intf J.m ABSTRACT
                       K1.class,  // intf K1            <: intf I <: intf J.m ABSTRACT
                       K2.class); // intf K2.m ABSTRACT <: intf I <: intf J.m ABSTRACT
            assertCompiled();

            // 3. Dependency invalidation: DI.m <: I
            initialize(DI.class); //      DI.m          <: intf I <: intf J.m ABSTRACT
            assertNotCompiled();

            // 4. Recompilation w/o a dependency
            compile(megamorphic());
            call(new C() { public Object m() { return CORRECT; }}); // Cn.m <: C.m <: intf I <: intf J.m ABSTRACT
            assertCompiled(); // no dependency

            checkInvalidReceiver(); // ensure proper type check on receiver is preserved
        }

        @TestCase
        public void testMega2() {
            compile(megamorphic()); // C1,C2,C3 <: C.m <: intf I <: intf J.m ABSTRACT
            assertCompiled();

            // Dependency: type = unique_concrete_method, context = I, method = C.m

            checkInvalidReceiver(); // ensure proper type check on receiver is preserved

            // Dependency invalidation
            initialize(K3.class); // intf K3.m DEFAULT <: intf I;
            assertNotCompiled(); // FIXME: default methods in sub-interfaces shouldn't be taken into account by CHA

            // Recompilation with a dependency
            compile(megamorphic());
            assertCompiled();

            // Dependency: type = unique_concrete_method, context = I, method = C.m

            checkInvalidReceiver(); // ensure proper type check on receiver is preserved

            call(new K3() { public Object m() { return CORRECT; }}); // Kn.m <: K3.m DEFAULT <: intf I <: intf J.m ABSTRACT
            assertNotCompiled();

            // Recompilation w/o a dependency
            compile(megamorphic());
            // Dependency: none
            checkInvalidReceiver(); // ensure proper type check on receiver is preserved
            call(new C() { public Object m() { return CORRECT; }}); // Cn.m <: C.m <: intf I <: intf J.m ABSTRACT
            assertCompiled();
        }

        @Override
        public void checkInvalidReceiver() {
            shouldThrow(IncompatibleClassChangeError.class, () -> {
                I o = (I) unsafeCastMH(I.class).invokeExact(new Object()); // unrelated
                test(o);
            });
            assertCompiled();

            shouldThrow(IncompatibleClassChangeError.class, () -> {
                I j = (I) unsafeCastMH(I.class).invokeExact((Object)new J() { public Object m() { return WRONG; }}); // super interface
                test(j);
            });
            assertCompiled();
        }
    }

    public static class ThreeLevelHierarchyAbstractVsDefault extends ATest<ThreeLevelHierarchyAbstractVsDefault.I> {
        public ThreeLevelHierarchyAbstractVsDefault() { super(I.class, C.class); }

        interface J1                { default Object m() { return WRONG; } } // intf J1.m DEFAULT
        interface J2 extends J1     { Object m(); }                          // intf J2.m ABSTRACT <: intf J1
        interface I  extends J1, J2 {}                                       // intf  I.m OVERPASS <: intf J1,J2

        static class C  implements I { public Object m() { return CORRECT; }}

        @DontInline
        public Object test(I i) {
            return i.m(); // intf I.m OVERPASS
        }

        static class DI implements I { public Object m() { return WRONG;   }}

        static class DJ11 implements J1 {}
        static class DJ12 implements J1 { public Object m() { return WRONG; }}

        static class DJ2 implements J2 { public Object m() { return WRONG;   }}

        interface K11 extends J1 {}
        interface K12 extends J1 { Object m(); }
        interface K13 extends J1 { default Object m() { return WRONG; }}
        interface K21 extends J2 {}
        interface K22 extends J2 { Object m(); }
        interface K23 extends J2 { default Object m() { return WRONG; }}


        public void testMega1() {
            // 0. Trigger compilation of megamorphic call site
            compile(megamorphic()); // C1,C2,C3 <: C.m <: intf I.m OVERPASS <: intf J2.m ABSTRACT <: intf J1.m DEFAULT
            assertCompiled();

            // Dependency: type = unique_concrete_method, context = I, method = C.m

            checkInvalidReceiver(); // ensure proper type check on receiver is preserved

            // 1. No deopt/invalidation on not-yet-seen receiver
            repeat(100, () -> call(new C(){})); // Cn <: C.m <: intf I.m OVERPASS <: intf J2.m ABSTRACT <: intf J1.m DEFAULT
            assertCompiled();

            // 2. No dependency invalidation: different context
            initialize(K11.class, K12.class, K13.class,
                       K21.class, K22.class, K23.class);

            // 3. Dependency invalidation: Cn.m <: C <: I
            call(new C() { public Object m() { return CORRECT; }}); // Cn.m <: C.m <: intf I.m OVERPASS <: intf J2.m ABSTRACT <: intf J1.m DEFAULT
            assertNotCompiled();

            // 4. Recompilation w/o a dependency
            compile(megamorphic());
            call(new C() { public Object m() { return CORRECT; }});
            assertCompiled(); // no inlining

            checkInvalidReceiver(); // ensure proper type check on receiver is preserved
        }

        public void testMega2() {
            // 0. Trigger compilation of a megamorphic call site
            compile(megamorphic());
            assertCompiled();

            // Dependency: type = unique_concrete_method, context = I, method = C.m

            checkInvalidReceiver(); // ensure proper type check on receiver is preserved

            // 1. No dependency invalidation: different context
            initialize(DJ11.class,
                       DJ12.class,
                       DJ2.class);
            assertCompiled();

            // 2. Dependency invalidation: DI.m <: I
            initialize(DI.class);
            assertNotCompiled();

            // 3. Recompilation w/o a dependency
            compile(megamorphic());
            call(new C() { public Object m() { return CORRECT; }});
            assertCompiled(); // no inlining

            checkInvalidReceiver(); // ensure proper type check on receiver is preserved
        }

        @Override
        public void checkInvalidReceiver() {
            shouldThrow(IncompatibleClassChangeError.class, () -> {
                I o = (I) unsafeCastMH(I.class).invokeExact(new Object()); // unrelated
                test(o);
            });
            assertCompiled();

            shouldThrow(IncompatibleClassChangeError.class, () -> {
                I j = (I) unsafeCastMH(I.class).invokeExact((Object)new J1() {}); // super interface
                test(j);
            });
            assertCompiled();

            shouldThrow(IncompatibleClassChangeError.class, () -> {
                I j = (I) unsafeCastMH(I.class).invokeExact((Object)new J2() { public Object m() { return WRONG; }}); // super interface
                test(j);
            });
            assertCompiled();
        }
    }

    public static class ThreeLevelDefaultHierarchy extends ATest<ThreeLevelDefaultHierarchy.I> {
        public ThreeLevelDefaultHierarchy() { super(I.class, C.class); }

        interface J           { default Object m() { return WRONG; }}
        interface I extends J {}

        static class C  implements I { public Object m() { return CORRECT; }}

        interface K1 extends I {}
        interface K2 extends I { Object m(); }
        interface K3 extends I { default Object m() { return WRONG; }}

        static class DI implements I { public Object m() { return WRONG; }}
        static class DJ implements J { public Object m() { return WRONG; }}

        @DontInline
        public Object test(I i) {
            return i.m(); // no inlining since J.m is a default method
        }

        @TestCase
        public void testMega() {
            // 0. Trigger compilation of a megamorphic call site
            compile(megamorphic()); // C1,C2,C3 <: C.m <: intf I <: intf J.m ABSTRACT
            assertCompiled();

            // Dependency: none

            checkInvalidReceiver(); // ensure proper type check on receiver is preserved

            // 1. No deoptimization/invalidation on not-yet-seen receiver
            repeat(100, () -> call(new C() {}));
            assertCompiled();

            // 2. No dependency and no inlining
            initialize(DJ.class,  //      DJ.m                    <: intf J.m ABSTRACT
                       DI.class,  //      DI.m          <: intf I <: intf J.m ABSTRACT
                       K1.class,  // intf K1            <: intf I <: intf J.m ABSTRACT
                       K2.class); // intf K2.m ABSTRACT <: intf I <: intf J.m ABSTRACT
            assertCompiled();
        }

        @Override
        public void checkInvalidReceiver() {
            shouldThrow(IncompatibleClassChangeError.class, () -> {
                I o = (I) unsafeCastMH(I.class).invokeExact(new Object()); // unrelated
                test(o);
            });
            assertCompiled();

            shouldThrow(IncompatibleClassChangeError.class, () -> {
                I j = (I) unsafeCastMH(I.class).invokeExact((Object)new J() {}); // super interface
                test(j);
            });
            assertCompiled();
        }
    }

    public static class ThreeLevelDefaultHierarchy1 extends ATest<ThreeLevelDefaultHierarchy1.I> {
        public ThreeLevelDefaultHierarchy1() { super(I.class, C.class); }

        interface J1                { Object m();}
        interface J2 extends J1     { default Object m() { return WRONG; }  }
        interface I  extends J1, J2 {}

        static class C  implements I { public Object m() { return CORRECT; }}

        interface K1 extends I {}
        interface K2 extends I { Object m(); }
        interface K3 extends I { default Object m() { return WRONG; }}

        static class DI implements I { public Object m() { return WRONG; }}
        static class DJ1 implements J1 { public Object m() { return WRONG; }}
        static class DJ2 implements J2 { public Object m() { return WRONG; }}

        @DontInline
        public Object test(I i) {
            return i.m(); // no inlining since J.m is a default method
        }

        @TestCase
        public void testMega() {
            // 0. Trigger compilation of a megamorphic call site
            compile(megamorphic());
            assertCompiled();

            // Dependency: none

            checkInvalidReceiver(); // ensure proper type check on receiver is preserved

            // 1. No deoptimization/invalidation on not-yet-seen receiver
            repeat(100, () -> call(new C() {}));
            assertCompiled();

            // 2. No dependency, no inlining
            // CHA doesn't support default methods yet.
            initialize(DJ1.class,
                       DJ2.class,
                       DI.class,
                       K1.class,
                       K2.class,
                       K3.class);
            assertCompiled();
        }

        @Override
        public void checkInvalidReceiver() {
            shouldThrow(IncompatibleClassChangeError.class, () -> {
                I o = (I) unsafeCastMH(I.class).invokeExact(new Object()); // unrelated
                test(o);
            });
            assertCompiled();

            shouldThrow(IncompatibleClassChangeError.class, () -> {
                I j = (I) unsafeCastMH(I.class).invokeExact((Object)new J1() { public Object m() { return WRONG; } }); // super interface
                test(j);
            });
            assertCompiled();

            shouldThrow(IncompatibleClassChangeError.class, () -> {
                I j = (I) unsafeCastMH(I.class).invokeExact((Object)new J2() {}); // super interface
                test(j);
            });
            assertCompiled();
        }
    }

    /* =========================================================== */

    interface Action {
        int run();
    }

    public static final Unsafe U = Unsafe.getUnsafe();

    interface Test<T> {
        void call(T o);
        T receiver(int id);

        default Runnable monomophic() {
            return () -> {
                call(receiver(0)); // 100%
            };
        }

        default Runnable bimorphic() {
            return () -> {
                call(receiver(0)); // 50%
                call(receiver(1)); // 50%
            };
        }

        default Runnable polymorphic() {
            return () -> {
                for (int i = 0; i < 23; i++) {
                    call(receiver(0)); // 92%
                }
                call(receiver(1)); // 4%
                call(receiver(2)); // 4%
            };
        }

        default Runnable megamorphic() {
            return () -> {
                call(receiver(0)); // 33%
                call(receiver(1)); // 33%
                call(receiver(2)); // 33%
            };
        }

        default void initialize(Class<?>... cs) {
            for (Class<?> c : cs) {
                U.ensureClassInitialized(c);
            }
        }

        default void repeat(int cnt, Runnable r) {
            for (int i = 0; i < cnt; i++) {
                r.run();
            }
        }
    }

    public static abstract class ATest<T> implements Test<T> {
        public static final WhiteBox WB = WhiteBox.getWhiteBox();

        public static final Object CORRECT = new Object();
        public static final Object WRONG   = new Object();

        final Method TEST;
        private final Class<T> declared;
        private final Class<?> receiver;

        private final HashMap<Integer, T> receivers = new HashMap<>();

        public ATest(Class<T> declared, Class<?> receiver) {
            this.declared = declared;
            this.receiver = receiver;
            TEST = compute(() -> this.getClass().getDeclaredMethod("test", declared));
        }

        @DontInline
        public abstract Object test(T i);

        public abstract void checkInvalidReceiver();

        public T receiver(int id) {
            return receivers.computeIfAbsent(id, (i -> {
                try {
                    MyClassLoader cl = (MyClassLoader) receiver.getClassLoader();
                    Class<?> sub = cl.subclass(receiver, i);
                    return (T)sub.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new Error(e);
                }
            }));
        }


        public void compile(Runnable r) {
            while (!WB.isMethodCompiled(TEST)) {
                for (int i = 0; i < 100; i++) {
                    r.run();
                }
            }
            assertCompiled(); // record nmethod info
        }

        private NMethod prevNM = null;

        public void assertNotCompiled() {
            NMethod curNM = NMethod.get(TEST, false);
            assertTrue(prevNM != null); // was previously compiled
            assertTrue(curNM == null || prevNM.compile_id != curNM.compile_id); // either no nmethod present or recompiled
            prevNM = curNM; // update nmethod info
        }

        public void assertCompiled() {
            NMethod curNM = NMethod.get(TEST, false);
            assertTrue(curNM != null); // nmethod is present
            assertTrue(prevNM == null || prevNM.compile_id == curNM.compile_id); // no recompilations if nmethod present
            prevNM = curNM; // update nmethod info
        }

        @Override
        public void call(T i) {
            assertTrue(test(i) != WRONG);
        }
    }

    @Retention(value = RetentionPolicy.RUNTIME)
    public @interface TestCase {}

    static void run(Class<?> test) {
        try {
            for (Method m : test.getDeclaredMethods()) {
                if (m.isAnnotationPresent(TestCase.class)) {
                    System.out.println(m.toString());
                    ClassLoader cl = new MyClassLoader(test);
                    Class<?> c = cl.loadClass(test.getName());
                    c.getMethod(m.getName()).invoke(c.getDeclaredConstructor().newInstance());
                }
            }
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    static class ObjectToStringHelper {
        static Object test(Object o) {
            throw new Error("not used");
        }
    }
    static class ObjectHashCodeHelper {
        static int test(Object o) {
        throw new Error("not used");
    }
    }

    static final class MyClassLoader extends ClassLoader {
        private final Class<?> test;

        MyClassLoader(Class<?> test) {
            this.test = test;
        }

        static String intl(String s) {
            return s.replace('.', '/');
        }

        Class<?> subclass(Class<?> c, int id) {
            String name = c.getName() + id;
            Class<?> sub = findLoadedClass(name);
            if (sub == null) {
                ClassWriter cw = new ClassWriter(COMPUTE_MAXS | COMPUTE_FRAMES);
                cw.visit(52, ACC_PUBLIC | ACC_SUPER, intl(name), null, intl(c.getName()), null);

                { // Default constructor: <init>()V
                    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKESPECIAL, intl(c.getName()), "<init>", "()V", false);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                }

                byte[] classFile = cw.toByteArray();
                return defineClass(name, classFile, 0, classFile.length);
            }
            return sub;
        }

        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException
        {
            // First, check if the class has already been loaded
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                try {
                    c = getParent().loadClass(name);
                    if (name.endsWith("ObjectToStringHelper")) {
                        ClassWriter cw = new ClassWriter(COMPUTE_MAXS | COMPUTE_FRAMES);
                        cw.visit(52, ACC_PUBLIC | ACC_SUPER, intl(name), null, "java/lang/Object", null);

                        {
                            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "test", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
                            mv.visitCode();
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitMethodInsn(INVOKEINTERFACE, intl(test.getName()) + "$I", "toString", "()Ljava/lang/String;", true);
                            mv.visitInsn(ARETURN);
                            mv.visitMaxs(0, 0);
                            mv.visitEnd();
                        }

                        byte[] classFile = cw.toByteArray();
                        return defineClass(name, classFile, 0, classFile.length);
                    } else if (name.endsWith("ObjectHashCodeHelper")) {
                        ClassWriter cw = new ClassWriter(COMPUTE_MAXS | COMPUTE_FRAMES);
                        cw.visit(52, ACC_PUBLIC | ACC_SUPER, intl(name), null, "java/lang/Object", null);

                        {
                            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "test", "(Ljava/lang/Object;)I", null, null);
                            mv.visitCode();
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitMethodInsn(INVOKEINTERFACE, intl(test.getName()) + "$I", "hashCode", "()I", true);
                            mv.visitInsn(IRETURN);
                            mv.visitMaxs(0, 0);
                            mv.visitEnd();
                        }

                        byte[] classFile = cw.toByteArray();
                        return defineClass(name, classFile, 0, classFile.length);
                    } else if (c == test || name.startsWith(test.getName())) {
                        try {
                            String path = name.replace('.', '/') + ".class";
                            byte[] classFile = getParent().getResourceAsStream(path).readAllBytes();
                            return defineClass(name, classFile, 0, classFile.length);
                        } catch (IOException e) {
                            throw new Error(e);
                        }
                    }
                } catch (ClassNotFoundException e) {
                    // ClassNotFoundException thrown if class not found
                    // from the non-null parent class loader
                }

                if (c == null) {
                    // If still not found, then invoke findClass in order
                    // to find the class.
                    c = findClass(name);
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    public interface RunnableWithException {
        void run() throws Throwable;
    }

    public static void shouldThrow(Class<? extends Throwable> expectedException, RunnableWithException r) {
        try {
            r.run();
            throw new AssertionError("Exception not thrown: " + expectedException.getName());
        } catch(Throwable e) {
            if (expectedException == e.getClass()) {
                // success: proper exception is thrown
            } else {
                throw new Error(expectedException.getName() + " is expected", e);
            }
        }
    }

    public static MethodHandle unsafeCastMH(Class<?> cls) {
        try {
            MethodHandle mh = MethodHandles.identity(Object.class);
            return MethodHandles.explicitCastArguments(mh, mh.type().changeReturnType(cls));
        } catch (Throwable e) {
            throw new Error(e);
        }
    }

    static <T> T compute(Callable<T> c) {
        try {
            return c.call();
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
