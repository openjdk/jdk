/*
 * Copyright (c) 2026, Justus Garbe. All rights reserved.
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
 * @bug 8352077
 * @summary A breakpoint set while metaspace is exhausted must not be left half-installed.
 * @requires vm.jvmti & vm.compiler1.enabled & vm.flagless
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/native
 *      -agentlib:BreakpointMetaspaceOOM
 *      -Xbootclasspath/a:.
 *      -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -Xbatch
 *      -XX:MaxMetaspaceSize=32m
 *      BreakpointMetaspaceOOM
 */

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import java.util.function.IntUnaryOperator;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

import static java.lang.constant.ConstantDescs.*;

public class BreakpointMetaspaceOOM {

    static final int ERR_NONE = 0;             // JVMTI_ERROR_NONE
    static final int ERR_OUT_OF_MEMORY = 110;  // JVMTI_ERROR_OUT_OF_MEMORY

    static final int TIER_C1_PROFILED = 3;     // CompLevel_full_profile (C1 with full profiling)

    static final int CALLS = 20_000;
    static final int BRANCHES = 1_000;

    static native void prepare(Method m);
    static native int setBreakpoint();
    static native int breakpointHits();

    static final MethodTypeDesc APPLY_AS_INT = MethodTypeDesc.of(CD_int, CD_int);
    static final ClassDesc INT_UNARY_OPERATOR = ClassDesc.of("java.util.function.IntUnaryOperator");

    static final class GenLoader extends ClassLoader {
        GenLoader() { super(BreakpointMetaspaceOOM.class.getClassLoader()); }
        Class<?> define(String name, byte[] bytes) { return defineClass(name, bytes, 0, bytes.length); }
    }

    private static void noArgConstructor(ClassBuilder cb) {
        cb.withMethodBody(INIT_NAME, MTD_void, ClassFile.ACC_PUBLIC,
            b -> b.aload(0).invokespecial(CD_Object, INIT_NAME, MTD_void).return_());
    }

    private static byte[] incrementerBytes() {
        return ClassFile.of().build(ClassDesc.of("Target"), cb -> {
            cb.withSuperclass(CD_Object).withInterfaceSymbols(INT_UNARY_OPERATOR);
            noArgConstructor(cb);
            cb.withMethodBody("applyAsInt", APPLY_AS_INT, ClassFile.ACC_PUBLIC,
                b -> b.iload(1).iconst_1().iadd().ireturn());
        });
    }

    private static byte[] branchHeavyBytes() {
        return ClassFile.of().build(ClassDesc.of("Big"), cb -> {
            cb.withSuperclass(CD_Object).withInterfaceSymbols(ClassDesc.of("java.lang.Runnable"));
            noArgConstructor(cb);
            cb.withMethodBody("run", MTD_void, ClassFile.ACC_PUBLIC, b -> {
                for (int i = 0; i < BRANCHES; i++) {
                    Label fallThrough = b.newLabel();
                    b.iconst_0().ifne(fallThrough).labelBinding(fallThrough);
                }
                b.return_();
            });
        });
    }

    private static byte[] fillerBytes(String name, int methodCount) {
        return ClassFile.of().build(ClassDesc.of(name), cb -> {
            cb.withSuperclass(CD_Object);
            for (int i = 0; i < methodCount; i++) {
                cb.withMethodBody("f".concat(Integer.toString(i)), MTD_void,
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, b -> b.return_());
            }
        });
    }

    private static void exhaustMetaspace(GenLoader loader) {
        int index = 0;
        for (int methodCount : new int[] {1000, 100, 10, 1, 0}) {
            try {
                for (;;) {
                    String name = "Filler".concat(Integer.toString(index++));
                    loader.define(name, fillerBytes(name, methodCount));
                }
            } catch (OutOfMemoryError expected) {
                // Arena full at this size; shrink the class and keep going.
            }
        }
        System.out.println("Metaspace exhausted");
    }

    public static void main(String[] args) throws Exception {
        WhiteBox wb = WhiteBox.getWhiteBox();

        GenLoader loader = new GenLoader();
        IntUnaryOperator incrementer =
            (IntUnaryOperator) loader.define("Target", incrementerBytes()).getDeclaredConstructor().newInstance();
        Method targetMethod = incrementer.getClass().getDeclaredMethod("applyAsInt", int.class);
        Runnable branchy =
            (Runnable) loader.define("Big", branchHeavyBytes()).getDeclaredConstructor().newInstance();

        // Resolve and warm everything needed after the fill
        prepare(targetMethod);
        branchy.run();
        wb.isMethodCompiled(targetMethod);
        fillerBytes("Warmup", 1);
        Asserts.assertTrue(true);
        breakpointHits();

        // Fill a throwaway loader, kept apart from the target's, so it can be collected later
        // to lift the OOM state again.
        GenLoader fillers = new GenLoader();
        exhaustMetaspace(fillers);

        // Make the JIT attempt branchy's profiling allocation under the full arena, tripping
        // the sticky out-of-memory state that causes the breakpoint count to be dropped.
        for (int i = 0; i < CALLS; i++) {
            branchy.run();
        }

        int rc = setBreakpoint();
        System.out.print("SetBreakpoint returned ");
        System.out.println(rc);

        // Drop the fillers and collect them so the metaspace OOM state clears: MethodCounters
        // can now be built again (with a breakpoint count of zero -- the count was lost), the
        // method runs, and C1 compiles it at tier 3 over the stale 0xCA.
        fillers = null;
        wb.fullGC();
        System.out.println("Fillers unloaded");

        for (int i = 0; i < CALLS; i++) {
            incrementer.applyAsInt(i);
        }
        wb.enqueueMethodForCompilation(targetMethod, TIER_C1_PROFILED);

        // On the unfixed VM a compilation above aborts with the 0xCA crash (the call loop can
        // trigger it, or the explicit request) and we never get here, so jtreg records the
        // crash as a failure. Reaching this point means no crash:
        if (rc == ERR_OUT_OF_MEMORY) {
            // The VM reported the allocation failure and installed no breakpoint, so none may
            // have fired.
            Asserts.assertEquals(0, breakpointHits(), "breakpoint fired after SetBreakpoint reported OUT_OF_MEMORY");
        } else if (rc == ERR_NONE) {
            // The breakpoint was installed in full. A VM that tracks it correctly refuses to
            // compile the method, and every interpreted call must have hit it.
            Asserts.assertFalse(wb.isMethodCompiled(targetMethod), "a method with a live breakpoint was compiled");
            Asserts.assertEquals(CALLS, breakpointHits(), "breakpoint hit count did not match the number of calls");
        } else {
            Asserts.fail("SetBreakpoint returned an unexpected JVMTI error code");
        }
    }
}
