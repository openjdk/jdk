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

/*
 * @test id=0-fast
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 0:fast
 */

/*
 * @test id=1-fast
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 1:fast
 */

/*
 * @test id=2-fast
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 2:fast
 */

/*
 * @test id=3-fast
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 3:fast
 */

/*
 * @test id=4-fast
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 4:fast
 */

/*
 * @test id=5-fast
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 5:fast
 */

/*
 * @test id=6-fast
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 6:fast
 */

/*
 * @test id=0-nofast
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 0:nofast
 */

/*
 * @test id=1-nofast
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 1:nofast
 */

/*
 * @test id=2-nofast
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 2:nofast
 */

/*
 * @test id=3-nofast
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 3:nofast
 */

/*
 * @test id=4-nofast
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 4:nofast
 */

/*
 * @test id=5-nofast
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 5:nofast
 */

/*
 * @test id=6-nofast
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 6:nofast
 */

/*
 * @test id=0-fast-nointrinsics
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 0:fast:nointrinsics
 */

/*
 * @test id=0-nofast-nointrinsics
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 0:nofast:nointrinsics
 */

/*
 * @test id=1-fast-nointrinsics
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 1:fast:nointrinsics
 */

/*
 * @test id=1-nofast-nointrinsics
 * @summary Test hashcode fast path with value classes
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64" | os.simpleArch == "riscv64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main ${test.main.class} 1:nofast:nointrinsics
 */

package compiler.valhalla.inlinetypes;

import static compiler.lib.generators.Generators.G;
import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

import static compiler.lib.ir_framework.IRNode.*;

public class TestHashcodeFastPath {
    record RunSettings(int scenario, boolean useHashCodeFastPath, boolean disableIntrinsics) {}

    static boolean parseBooleanSetting(String[] pieces, int idx, String false_str, String true_str, boolean def) {
        if (pieces.length > idx) {
            String piece = pieces[idx];
            if(piece.equals(false_str)) {
                return false;
            } else if(piece.equals(true_str)) {
                return true;
            } else {
                throw new RuntimeException("Unknown setting: " + piece);
            }
        } else {
            return def;
        }
    }

    static RunSettings parseSetting(String arg) {
        String[] pieces = arg.split(":");

        int scenario = Integer.parseInt(pieces[0]);

        boolean useHashCodeFastPath = parseBooleanSetting(pieces, 1, "nofast", "fast", true);
        boolean disableIntrinsics = parseBooleanSetting(pieces, 2, "intrinsics", "nointrinsics", false);

        return new RunSettings(scenario, useHashCodeFastPath, disableIntrinsics);
    }

    public static void main(String[] args) {
        Scenario[] scenarios = InlineTypes.DEFAULT_SCENARIOS;
        RunSettings settings = parseSetting(args[0]);
        Scenario scenario = scenarios[settings.scenario];
        if (!settings.useHashCodeFastPath) {
            scenario.addFlags("-XX:-UseHashcodeFastPath");
        }
        if (settings.disableIntrinsics) {
            scenario.addFlags("-XX:DisableIntrinsic=_identityHashCode");
        }
        scenario.addFlags("-XX:CompileCommand=exclude,*::h");
        InlineTypes.getFramework()
                .addScenarios(scenario)
                .start();
    }

    static abstract value class UniquelyDerivedBase {
    }
    static value class UniqueDerived extends UniquelyDerivedBase {
        byte b;
        UniqueDerived(byte b) {
            this.b = b;
        }
    }

    static abstract value class MultiplyDerivedBase {
    }
    static value class Derived extends MultiplyDerivedBase {
        byte b;
        Derived(byte b) {
            this.b = b;
        }
    }
    // Prevents Derived from being the only concrete class under MultiplyDerivedBase
    static value class EvilDerived extends MultiplyDerivedBase {
        byte b;
        EvilDerived(byte b) {
            this.b = b;
        }
    }

    static value class DerivedWrapper {
        short s;
        Derived b;
        DerivedWrapper(byte b, short s) {
            this.b = new Derived(b);
            this.s = s;
        }
    }

    value record ShortWrapper(short s) {}

    static abstract value class AbstractShort {
        ShortWrapper s;
        AbstractShort(int s) {
            this.s = new ShortWrapper((short)s);
        }

        public String toString() {
            return "AbstractShort(" + s + ")";
        }
    }

    static value class ShortWithInt extends AbstractShort {
        int i;
        ShortWithInt(int s, int i) {
            this.i = i;
            super(s);
        }
        public String toString() {
            return "ShortWithInt(s=" + s.s + ", i=" + i + ")";
        }
    }

    static value class Empty {}



    static value class LongLong {
        long s;
        long b;
        LongLong(long s, long b) {
            this.s = s;
            this.b = b;
        }
    }
    static value class WithOop {
        String s;
        WithOop(String s) {
            this.s = s;
        }
    }

    int h(Object o) {
        return System.identityHashCode(o);
    }

    @Run(test = {
            "h_object",
            "h_unique_derived",
            "h_uniquely_derived_base",
            "h_derived",
            "h_base",
            "h_derived_hidden_type",
            "h_short_with_int",
            "h_short_with_int_hidden_type",
            "h_with_oop",
            "h_with_oop_hidden_type",
    })
    @Warmup(0)  // We want to prevent profiling
    public void run() {
        var wrapper = new DerivedWrapper((byte)0, (short)0xa2a1);
        var wrapper_ = new DerivedWrapper((byte)0, (short)0xa2a1);

        var derived1 = new Derived((byte)0);
        var derived_ = new Derived((byte)0);
        var evilDerived1 = new EvilDerived((byte)1);  // Force class loading

        Asserts.assertEQ(h_object(null), h(null));
        Asserts.assertEQ(h_object(null), 0);
        Asserts.assertEQ(h_object(wrapper), h(wrapper_));

        Asserts.assertEQ(h_derived(derived1), h(derived_));
        Asserts.assertEQ(h_base(derived1), h(derived_));
        Asserts.assertEQ(h_derived_hidden_type(derived1), h(derived_));

        Asserts.assertEQ(h_base(evilDerived1), h(evilDerived1));

        var uniqueDerived = new UniqueDerived((byte)0);
        var uniqueDerived_ = new UniqueDerived((byte)0);

        Asserts.assertEQ(h_object(uniqueDerived), h(uniqueDerived_));
        Asserts.assertEQ(h_unique_derived(uniqueDerived), h(uniqueDerived_));
        Asserts.assertEQ(h_uniquely_derived_base(uniqueDerived), h(uniqueDerived_));

        var swi = new ShortWithInt(0, 1);
        var swi_ = new ShortWithInt(0, 1);
        Asserts.assertEQ(h_object(swi), h(swi_));
        Asserts.assertEQ(h_short_with_int(swi), h(swi_));
        Asserts.assertEQ(h_short_with_int_hidden_type(swi), h(swi_));

        var empty = new Empty();
        var empty_ = new Empty();
        Asserts.assertEQ(h_object(empty), h(empty_));

        var with_oops = new WithOop("a");
        var with_oops_ = new WithOop("a");
        Asserts.assertEQ(h_with_oop(with_oops), h(with_oops_));
        Asserts.assertEQ(h_with_oop_hidden_type(with_oops), h(with_oops_));
    }

    static final String IDENTITY_HASHCODE = "identityHashCode";

    static final int URSHIFT_L_COUNT_FOR_CACHE_PATH = 1;  // Shift object header
    static final int URSHIFT_L_COUNT_FOR_FAST_PATH = 2;  // Shift class header, maybe shift again for long payload
    static final int RSHIFT_L_COUNT_FOR_FAST_PATH = 1;  // Shift object payload
    static final String CACHE_PATH_U = "" + URSHIFT_L_COUNT_FOR_CACHE_PATH;
    static final String CACHE_AND_FAST_PATH_U = "" + (URSHIFT_L_COUNT_FOR_CACHE_PATH + URSHIFT_L_COUNT_FOR_FAST_PATH);
    static final String FAST_PATH_S = "" + RSHIFT_L_COUNT_FOR_FAST_PATH;

    // Get hashcode fast path
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"UseHashcodeFastPath", "false", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"UseHashcodeFastPath", "false", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_AND_FAST_PATH_U, RSHIFT_L, FAST_PATH_S, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"UseHashcodeFastPath", "true", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_AND_FAST_PATH_U, RSHIFT_L, FAST_PATH_S, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"UseHashcodeFastPath", "true", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIfAnd = {"UseHashcodeFastPath", "false", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIfAnd = {"UseHashcodeFastPath", "false", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L, RSHIFT_L}, applyIfAnd = {"UseHashcodeFastPath", "true", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L, RSHIFT_L}, applyIfAnd = {"UseHashcodeFastPath", "true", "DisableIntrinsic", "_identityHashCode"})
    int h_object(Object a) {
        return System.identityHashCode(a);
    }

    // No hashcode fast path: the type is precise, and the call will be intrinsified
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_U}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_unique_derived(UniqueDerived a) {
        return System.identityHashCode(a);
    }

    // No hashcode fast path: single concrete derived, and the call will be intrinsified
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_U}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_uniquely_derived_base(UniquelyDerivedBase a) {
        return System.identityHashCode(a);
    }

    // No hashcode fast path: the type is precise, and the call will be intrinsified
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_U}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_derived(Derived a) {
        return System.identityHashCode(a);
    }

    // Hashcode fast path is generated, the type is not precise enough for intrinsifying
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"UseHashcodeFastPath", "false", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"UseHashcodeFastPath", "false", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_AND_FAST_PATH_U, RSHIFT_L, FAST_PATH_S, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"UseHashcodeFastPath", "true", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_AND_FAST_PATH_U, RSHIFT_L, FAST_PATH_S, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"UseHashcodeFastPath", "true", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIfAnd = {"UseHashcodeFastPath", "false", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIfAnd = {"UseHashcodeFastPath", "false", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L, RSHIFT_L}, applyIfAnd = {"UseHashcodeFastPath", "true", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L, RSHIFT_L}, applyIfAnd = {"UseHashcodeFastPath", "true", "DisableIntrinsic", "_identityHashCode"})
    int h_base(MultiplyDerivedBase a) {
        return System.identityHashCode(a);
    }

    // Hides the type during parsing when always incrementally inlining
    @ForceInline
    public Object getter(Object o) {
        return o;
    }

    // With late inlining, type is hidden at first, and a fast path is generated.
    // Later, type becomes precise, call is intrinsified and fast path is removed.
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"AlwaysIncrementalInline", "false", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_AND_FAST_PATH_U, RSHIFT_L, FAST_PATH_S, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "true", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "false", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_U}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIfAnd = {"AlwaysIncrementalInline", "false", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L, RSHIFT_L}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "true", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "false", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_derived_hidden_type(Derived a) {
        return System.identityHashCode(getter(a));
    }

    // No hashcode fast path: the type is precise, and the call will be intrinsified. Fast path wouldn't work anyway because it has a weird size.
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_U}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL},failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_short_with_int(ShortWithInt a) {
        return System.identityHashCode(a);
    }

    // With late inlining, type is hidden at first, and a fast path is generated.
    // Later, type becomes precise, call is intrinsified and fast path is removed.
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"AlwaysIncrementalInline", "false", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_AND_FAST_PATH_U, RSHIFT_L, FAST_PATH_S, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "true", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "false", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_U}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIfAnd = {"AlwaysIncrementalInline", "false", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L, RSHIFT_L}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "true", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "false", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_short_with_int_hidden_type(ShortWithInt a) {
        return System.identityHashCode(getter(a));
    }

    // No hashcode fast path: the type is precise, and the call will be intrinsified if possible. Fast path wouldn't work anyway because of the oop.
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_with_oop(WithOop a) {
        return System.identityHashCode(a);
    }

    // With late inlining, type is hidden at first, and a fast path is generated.
    // Later, type becomes precise, call would be intrinsified if possible. But it's not. Yet, we can also find out the fast path won't work, and it is removed.
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"AlwaysIncrementalInline", "false", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_AND_FAST_PATH_U, RSHIFT_L, FAST_PATH_S, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "true", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "false", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIfAnd = {"AlwaysIncrementalInline", "false", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L, RSHIFT_L}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "true", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "false", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_with_oop_hidden_type(WithOop a) {
        return System.identityHashCode(getter(a));
    }

    // Only null path should exist
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, failOn = {URSHIFT_L, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, failOn = {URSHIFT_L, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, failOn = {URSHIFT_L, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_null() {
        return System.identityHashCode(null);
    }

    // Only null path should survive
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, failOn = {URSHIFT_L, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIfAnd = {"AlwaysIncrementalInline", "false", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_AND_FAST_PATH_U, RSHIFT_L, FAST_PATH_S, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "true", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "false", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, failOn = {URSHIFT_L, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIfAnd = {"AlwaysIncrementalInline", "false", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L, RSHIFT_L}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "true", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "false", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, failOn = {URSHIFT_L, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_null_hidden_type() {
        return System.identityHashCode(getter(null));
    }

    @Run(test = {
            "h_byte",
            "h_short",
            "h_int",
            "h_long",
            "h_long_long",
            "h_short_with_int2",
            "h_short_with_int_hidden_type2",
            "h_with_oop2",
    })
    public void run2() {
        for (int i = Byte.MIN_VALUE; i<= Byte.MAX_VALUE; ++i) {
            Asserts.assertEQ(h_byte(new Byte((byte)i)), h(new Byte((byte)i)), "i = " + i);
        }
        // -1000 and 1000 are here to have a "normal" range, but outside what Short,
        // Integer or Long will cache, that is without cached hash in the header.
        int HALF_WIDTH = 256;
        for (short base : new short[]{0, -1000, 1000, Short.MIN_VALUE, Short.MAX_VALUE}) {
            for (short k = 0; k < 2 * HALF_WIDTH + 1; ++k) {
                short s = (short) (k + base - HALF_WIDTH);
                Asserts.assertEQ(h_short(new Short(s)), h(new Short(s)), "s = " + s);
            }
        }
        for (int base : new int[]{0, -1000, 1000, Short.MIN_VALUE, Short.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            for (int k = 0; k < 2 * HALF_WIDTH + 1; ++k) {
                int i = k + base - HALF_WIDTH;
                Asserts.assertEQ(h_int(new Integer(i)), h(new Integer(i)), "i = " + i);
                Asserts.assertEQ(h_short_with_int2(new ShortWithInt(i, i)), h(new ShortWithInt(i, i)), "i = " + i);
                Asserts.assertEQ(h_short_with_int_hidden_type2(new ShortWithInt(i, i)), h(new ShortWithInt(i, i)), "i = " + i);
            }
        }
        for (long base : new long[]{0, -1000, 1000, Long.MIN_VALUE, Long.MAX_VALUE}) {
            for (long k = 0; k < 2 * HALF_WIDTH + 1; ++k) {
                long l = k + base - HALF_WIDTH;
                Asserts.assertEQ(h_long(new Long(l)), h(new Long(l)), "l = " + l);
                Asserts.assertEQ(h_long_long(new LongLong(l, l)), h(new LongLong(l, l)), "l = " + l);
                Asserts.assertEQ(h_long_long(new LongLong((l << 32L) + l, Long.MAX_VALUE - l)), h(new LongLong((l << 32L) + l, Long.MAX_VALUE - l)), "l = " + l);
                String str = String.valueOf(l);
                Asserts.assertEQ(h_with_oop2(new WithOop(str)), h(new WithOop(str)), "l = " + l);

                Long l_ = new Long(l);
                int expected_hash = h(l_);
                Asserts.assertEQ(h_long(l_), expected_hash, "l = " + l);
            }
        }

        short s = G.ints().next().shortValue();
        Asserts.assertEQ(h_short(new Short(s)), h(new Short(s)), "s = " + s);
        int i = G.ints().next();
        Asserts.assertEQ(h_int(new Integer(i)), h(new Integer(i)), "i = " + i);
        long l = G.longs().next();
        Asserts.assertEQ(h_long(new Long(l)), h(new Long(l)), "l = " + l);
        Asserts.assertEQ(h_long_long(new LongLong(i, i)), h(new LongLong(i, i)), "i = " + i);
        Asserts.assertEQ(h_short_with_int2(new ShortWithInt(i, i)), h(new ShortWithInt(i, i)), "i = " + i);
        Asserts.assertEQ(h_short_with_int_hidden_type2(new ShortWithInt(i, i)), h(new ShortWithInt(i, i)), "i = " + i);
        String str = String.valueOf(i);
        Asserts.assertEQ(h_with_oop2(new WithOop(str)), h(new WithOop(str)), "i = " + i);

        Long lon = new Long(i);
        int expected_hash = h(lon);
        Asserts.assertEQ(h_long(lon), expected_hash, "lon = " + lon);
    }

    // Statically expanded
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_U}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_byte(Byte a) {
        return System.identityHashCode(a);
    }
    // Statically expanded
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_U}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_short(Short a) {
        return System.identityHashCode(a);
    }
    // Statically expanded
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_U}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_int(Integer a) {
        return System.identityHashCode(a);
    }
    // Statically expanded
    public static final String ONE_LONG_IN_INTRINSIC = "" + 1;
    public static final String CACHE_PATH_AND_ONE_LONG_IN_INTRINSIC = "" + (URSHIFT_L_COUNT_FOR_CACHE_PATH + 1);
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_AND_ONE_LONG_IN_INTRINSIC}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, ONE_LONG_IN_INTRINSIC}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_long(Long a) {
        return System.identityHashCode(a);
    }
    // Statically expanded
    public static final String TWO_LONG_IN_INTRINSIC = "" + 2;
    public static final String CACHE_PATH_AND_TWO_LONG_IN_INTRINSIC = "" + (URSHIFT_L_COUNT_FOR_CACHE_PATH + 2);
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_AND_TWO_LONG_IN_INTRINSIC}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, TWO_LONG_IN_INTRINSIC}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_long_long(LongLong a) {
        return System.identityHashCode(a);
    }

    // No hashcode fast path: the type is precise, and the call will be intrinsified. Fast path wouldn't work anyway because it has a weird size.
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_U}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_short_with_int2(ShortWithInt a) {
        return System.identityHashCode(a);
    }

    // With late inlining, type is hidden at first, and a fast path is generated.
    // Later, type becomes precise, call is intrinsified and fast path is removed.
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"AlwaysIncrementalInline", "false", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_AND_FAST_PATH_U, RSHIFT_L, FAST_PATH_S, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "true", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "false", "DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_U}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIfAnd = {"AlwaysIncrementalInline", "false", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L, RSHIFT_L}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "true", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIfAnd = {"AlwaysIncrementalInline", "true", "UseHashcodeFastPath", "false", "DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, failOn = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_short_with_int_hidden_type2(ShortWithInt a) {
        return System.identityHashCode(getter(a));
    }

    // No hashcode fast path: the type is precise, and the call will be intrinsified if possible. Fast path wouldn't work anyway because of the oop.
    @Test
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {URSHIFT_L, CACHE_PATH_U, STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, applyIf = {"DisableIntrinsic", ""})
    @IR(phase = {CompilePhase.AFTER_PARSING}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    @IR(phase = {CompilePhase.PRINT_IDEAL}, counts = {STATIC_CALL_OF_METHOD, IDENTITY_HASHCODE, "1"}, failOn = {URSHIFT_L}, applyIf = {"DisableIntrinsic", "_identityHashCode"})
    int h_with_oop2(WithOop a) {
        return System.identityHashCode(a);
    }
}
