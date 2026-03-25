/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=default
 * @key randomness
 * @summary Test that Virtual Threads work well with Value Objects.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=600 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               compiler.valhalla.inlinetypes.TestVirtualThreads
 */

/*
 * @test id=ci
 * @key randomness
 * @summary Test that Virtual Threads work well with Value Objects.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=600 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestVirtualThreads*::*
 *                   compiler.valhalla.inlinetypes.TestVirtualThreads
 */

/*
 * @test id=ci-test
 * @key randomness
 * @summary Test that Virtual Threads work well with Value Objects.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=600 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xbatch -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestVirtualThreads*::test*
 *                                compiler.valhalla.inlinetypes.TestVirtualThreads
 */

/*
 * @test id=ci-test-di
 * @key randomness
 * @summary Test that Virtual Threads work well with Value Objects.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=600 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xbatch -XX:CompileCommand=dontinline,*::dontinline
 *                               -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestVirtualThreads*::test*
 *                               -XX:CompileCommand=dontinline,*::test*
 *                               compiler.valhalla.inlinetypes.TestVirtualThreads
 */

/*
 * @test id=ci-test-di-helper
 * @key randomness
 * @summary Test that Virtual Threads work well with Value Objects.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=600 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xbatch -XX:CompileCommand=dontinline,*::dontinline
 *                               -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestVirtualThreads*::test*
 *                               -XX:CompileCommand=dontinline,*::*Helper
 *                               compiler.valhalla.inlinetypes.TestVirtualThreads
 */

/*
 * @test id=ci-test-di-exclude-helper
 * @key randomness
 * @summary Test that Virtual Threads work well with Value Objects.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=600 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xbatch -XX:CompileCommand=dontinline,*::dontinline
 *                               -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestVirtualThreads*::test*
 *                               -XX:CompileCommand=exclude,*::*Helper
 *                               compiler.valhalla.inlinetypes.TestVirtualThreads
 */

/*
 * @test id=xcomp-ci
 * @key randomness
 * @summary Test that Virtual Threads work well with Value Objects.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=600 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xcomp -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestVirtualThreads*::*
 *                               compiler.valhalla.inlinetypes.TestVirtualThreads
 */

/*
 * @test id=xcomp-co-test
 * @key randomness
 * @summary Test that Virtual Threads work well with Value Objects.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=600 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xcomp -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestVirtualThreads*::test*
 *                               compiler.valhalla.inlinetypes.TestVirtualThreads
 */

/*
 * @test id=xcomp-co-test-di
 * @key randomness
 * @summary Test that Virtual Threads work well with Value Objects.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=600 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xcomp -XX:CompileCommand=dontinline,*::dontinline
 *                               -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestVirtualThreads*::test*
 *                               -XX:CompileCommand=dontinline,*::test*
 *                                compiler.valhalla.inlinetypes.TestVirtualThreads
 */

/*
 * @test id=xcomp-co-test-di-helper
 * @key randomness
 * @summary Test that Virtual Threads work well with Value Objects.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=600 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xcomp -XX:CompileCommand=dontinline,*::dontinline
 *                               -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestVirtualThreads*::test*
 *                               -XX:CompileCommand=dontinline,*::*Helper
 *                               compiler.valhalla.inlinetypes.TestVirtualThreads
 */

/*
 * @test id=xcomp-co-test-di-exclude-helper
 * @key randomness
 * @summary Test that Virtual Threads work well with Value Objects.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=600 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xcomp -XX:CompileCommand=dontinline,*::dontinline
 *                               -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestVirtualThreads*::test*
 *                               -XX:CompileCommand=exclude,*::*Helper
 *                               compiler.valhalla.inlinetypes.TestVirtualThreads
 */

/*
 * @test id=co-di
 * @key randomness
 * @summary Test that Virtual Threads work well with Value Objects.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=600 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xbatch -XX:CompileCommand=dontinline,*::*
 *                               -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestVirtualThreads*::*
 *                                compiler.valhalla.inlinetypes.TestVirtualThreads 250000
 */

/*
 * @test id=xcomp-co-di
 * @key randomness
 * @summary Test that Virtual Threads work well with Value Objects.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=600 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                               -Xcomp -XX:CompileCommand=dontinline,*::*
 *                               -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestVirtualThreads*::*
 *                               compiler.valhalla.inlinetypes.TestVirtualThreads 250000
 */

package compiler.valhalla.inlinetypes;

import java.lang.reflect.Method;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.CountDownLatch;
import java.util.Random;

import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.whitebox.WhiteBox;

public class TestVirtualThreads {
    static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    static final int COMP_LEVEL_SIMPLE = 1; // C1
    static final int COMP_LEVEL_FULL_OPTIMIZATION = 4; // C2 or JVMCI
    static final Random RAND = Utils.getRandomInstance();
    static final int PARK_DURATION = 10;

    static value class SmallValue {
        int x1;
        int x2;

        public SmallValue(int i) {
            this.x1 = i;
            this.x2 = i;
        }

        public String toString() {
            return "x1 = " + x1 + ", x2 = " + x2;
        }

        public void verify(String loc, int i) {
            if (x1 != i || x2 != i) {
                throw new RuntimeException("Incorrect result at " + loc + " for i = " + i + ": " + this);
            }
        }

        public static void verify(SmallValue val, String loc, int i, boolean useNull) {
            if (useNull) {
                if (val != null) {
                    throw new RuntimeException("Incorrect result at " + loc + " for i = " + i + ": " + val);
                }
            } else {
                val.verify(loc, i);
            }
        }
    }

    // Large value class
    static value class LargeValue {
        int x1;
        int x2;
        int x3;
        int x4;
        int x5;
        int x6;
        int x7;

        public LargeValue(int i) {
            this.x1 = i;
            this.x2 = i;
            this.x3 = i;
            this.x4 = i;
            this.x5 = i;
            this.x6 = i;
            this.x7 = i;
        }

        public String toString() {
            return "x1 = " + x1 + ", x2 = " + x2 + ", x3 = " + x3 + ", x4 = " + x4 + ", x5 = " + x5 +
                   ", x6 = " + x6 + ", x7 = " + x7;
        }

        public void verify(String loc, int i) {
            if (x1 != i || x2 != i || x3 != i || x4 != i || x5 != i ||
                x6 != i || x7 != i) {
                throw new RuntimeException("Incorrect result at " + loc + " for i = " + i + ": " + this);
            }
        }

        public static void verify(LargeValue val, String loc, int i, boolean useNull) {
            if (useNull) {
                if (val != null) {
                    throw new RuntimeException("Incorrect result at " + loc + " for i = " + i + ": " + val);
                }
            } else {
                val.verify(loc, i);
            }
        }
    }

    // Large value class with fields of different types
    static value class LargeValue2 {
        byte x1;
        short x2;
        int x3;
        long x4;
        double x5;
        boolean x6;

        public LargeValue2(int i) {
            this.x1 = (byte)i;
            this.x2 = (short)i;
            this.x3 = i;
            this.x4 = i;
            this.x5 = i;
            this.x6 = (i % 2) == 0;
        }

        public String toString() {
            return "x1 = " + x1 + ", x2 = " + x2 + ", x3 = " + x3 + ", x4 = " + x4 + ", x5 = " + x5 +
                   ", x6 = " + x6;
        }

        public void verify(String loc, int i) {
            if (x1 != (byte)i || x2 != (short)i || x3 != i || x4 != i || x5 != i ||
                x6 != ((i % 2) == 0)) {
                throw new RuntimeException("Incorrect result at " + loc + " for i = " + i + ": " + this);
            }
        }

        public static void verify(LargeValue2 val, String loc, int i, boolean useNull) {
            if (useNull) {
                if (val != null) {
                    throw new RuntimeException("Incorrect result at " + loc + " for i = " + i + ": " + val);
                }
            } else {
                val.verify(loc, i);
            }
        }
    }

    // Large value class with oops (and different number of fields) that requires stack extension/repair
    static value class LargeValueWithOops {
        Object x1;
        Object x2;
        Object x3;
        Object x4;
        Object x5;

        public LargeValueWithOops(Object obj) {
            this.x1 = obj;
            this.x2 = obj;
            this.x3 = obj;
            this.x4 = obj;
            this.x5 = obj;
        }

        public String toString() {
            return "x1 = " + x1 + ", x2 = " + x2 + ", x3 = " + x3 + ", x4 = " + x4 + ", x5 = " + x5;
        }

        public void verify(String loc, Object obj) {
            if (x1 != obj || x2 != obj || x3 != obj || x4 != obj || x5 != obj) {
                throw new RuntimeException("Incorrect result at " + loc + " for obj = " + obj + ": " + this);
            }
        }

        public static void verify(LargeValueWithOops val, String loc, Object obj, boolean useNull) {
            if (useNull) {
                if (val != null) {
                    throw new RuntimeException("Incorrect result at " + loc + " for obj = " + obj + ": " + val);
                }
            } else {
                val.verify(loc, obj);
            }
        }
    }

    public static value class DoubleValue {
        double d;

        public DoubleValue(double d) {
            this.d = d;
        }

        public String toString() {
            return "d = " + d;
        }

        public void verify(String loc, double d) {
            if (this.d != d) {
                throw new RuntimeException("Incorrect result at " + loc + " for d = " + d + ": " + this);
            }
        }

        public static void verify(DoubleValue val, String loc, double d, boolean useNull) {
            if (useNull) {
                if (val != null) {
                    throw new RuntimeException("Incorrect result at " + loc + " for d = " + d+ ": " + val);
                }
            } else {
                val.verify(loc, d);
            }
        }
    }

    public static value class DoubleValue2 {
        double d1;
        double d2;
        double d3;
        double d4;
        double d5;

        public DoubleValue2(double d) {
            this.d1 = d;
            this.d2 = d + 1;
            this.d3 = d + 2;
            this.d4 = d + 3;
            this.d5 = d + 4;
        }

        public String toString() {
            return "d1 = " + d1 + ", d2 = " + d2 + ", d3 = " + d3 + ", d4= " + d4 + ", d5= " + d5;
        }

        public void verify(String loc, double d) {
            if (this.d1 != d || this.d2 != (d+1) || this.d3 != (d+2) || this.d4 != (d+3) || this.d5 != (d+4)) {
                throw new RuntimeException("Incorrect result at " + loc + " for d = " + d + ": " + this);
            }
        }

        public static void verify(DoubleValue2 val, String loc, double d, boolean useNull) {
            if (useNull) {
                if (val != null) {
                    throw new RuntimeException("Incorrect result at " + loc + " for d = " + d + ": " + val);
                }
            } else {
                val.verify(loc, d);
            }
        }
    }

    static abstract value class BaseValue {
        public abstract void verify(String loc, int i);
    }

    static value class ValueExtendsAbstract extends BaseValue {
        int x1;
        int x2;
        int x3;
        int x4;
        int x5;
        int x6;
        int x7;

        public ValueExtendsAbstract(int i) {
            this.x1 = i;
            this.x2 = i;
            this.x3 = i;
            this.x4 = i;
            this.x5 = i;
            this.x6 = i;
            this.x7 = i;
        }

        public String toString() {
            return "x1 = " + x1 + ", x2 = " + x2 + ", x3 = " + x3 + ", x4 = " + x4 + ", x5 = " + x5 +
                   ", x6 = " + x6 + ", x7 = " + x7;
        }

        public void verify(String loc, int i) {
            if (x1 != i || x2 != i || x3 != i || x4 != i || x5 != i ||
                x6 != i || x7 != i) {
                throw new RuntimeException("Incorrect result at " + loc + " for i = " + i + ": " + this);
            }
        }
    }

    public static void dontInline() { }

    public static SmallValue testSmall(SmallValue val, int i, boolean useNull, boolean park) {
        SmallValue.verify(val, "entry", i, useNull);
        if (park) {
            LockSupport.parkNanos(PARK_DURATION);
        }
        SmallValue.verify(val, "exit", i, useNull);
        return val;
    }

    public static SmallValue testSmallHelper(int i, boolean useNull, boolean park) {
        SmallValue val = useNull ? null : new SmallValue(i);
        val = testSmall(val, i, useNull, park);
        SmallValue.verify(val, "helper", i, useNull);
        return val;
    }

    public static LargeValue testLarge(LargeValue val, int i, boolean useNull, boolean park) {
        LargeValue.verify(val, "entry", i, useNull);
        if (park) {
            LockSupport.parkNanos(PARK_DURATION);
        }
        dontInline(); // Prevent C2 from optimizing out below checks
        LargeValue.verify(val, "exit", i, useNull);
        return val;
    }

    public static LargeValue testLargeHelper(int i, boolean useNull, boolean park) {
        LargeValue val = useNull ? null : new LargeValue(i);
        val = testLarge(val, i, useNull, park);
        LargeValue.verify(val, "helper", i, useNull);
        return val;
    }

    // Version that already has values on the stack even before stack extensions
    public static LargeValue testLargeManyArgs(LargeValue val1, LargeValue val2, LargeValue val3, LargeValue val4, int i, boolean useNull, boolean park) {
        LargeValue.verify(val1, "entry", i, useNull);
        LargeValue.verify(val2, "entry", i + 1, useNull);
        LargeValue.verify(val3, "entry", i + 2, useNull);
        LargeValue.verify(val4, "entry", i + 3, useNull);
        if (park) {
            LockSupport.parkNanos(PARK_DURATION);
        }
        dontInline(); // Prevent C2 from optimizing out below checks
        LargeValue.verify(val1, "exit", i, useNull);
        LargeValue.verify(val2, "exit", i + 1, useNull);
        LargeValue.verify(val3, "exit", i + 2, useNull);
        LargeValue.verify(val4, "exit", i + 3, useNull);
        return val4;
    }

    public static LargeValue testLargeManyArgsHelper(int i, boolean useNull, boolean park) {
        LargeValue val1 = useNull ? null : new LargeValue(i);
        LargeValue val2 = useNull ? null : new LargeValue(i + 1);
        LargeValue val3 = useNull ? null : new LargeValue(i + 2);
        LargeValue val4 = useNull ? null : new LargeValue(i + 3);
        LargeValue val = testLargeManyArgs(val1, val2, val3, val4, i, useNull, park);
        LargeValue.verify(val, "helper", i + 3, useNull);
        return val;
    }

    public static LargeValue2 testLarge2(LargeValue2 val, int i, boolean useNull, boolean park) {
        LargeValue2.verify(val, "entry", i, useNull);
        if (park) {
            LockSupport.parkNanos(PARK_DURATION);
        }
        dontInline(); // Prevent C2 from optimizing out below checks
        LargeValue2.verify(val, "exit", i, useNull);
        return val;
    }

    public static LargeValue2 testLarge2Helper(int i, boolean useNull, boolean park) {
        LargeValue2 val = useNull ? null : new LargeValue2(i);
        val = testLarge2(val, i, useNull, park);
        LargeValue2.verify(val, "helper", i, useNull);
        return val;
    }

    // Version that already has values on the stack even before stack extensions
    public static LargeValue2 testLarge2ManyArgs(LargeValue2 val1, LargeValue2 val2, LargeValue2 val3, LargeValue2 val4, int i, boolean useNull, boolean park) {
        LargeValue2.verify(val1, "entry", i, useNull);
        LargeValue2.verify(val2, "entry", i + 1, useNull);
        LargeValue2.verify(val3, "entry", i + 2, useNull);
        LargeValue2.verify(val4, "entry", i + 3, useNull);
        if (park) {
            LockSupport.parkNanos(PARK_DURATION);
        }
        dontInline(); // Prevent C2 from optimizing out below checks
        LargeValue2.verify(val1, "exit", i, useNull);
        LargeValue2.verify(val2, "exit", i + 1, useNull);
        LargeValue2.verify(val3, "exit", i + 2, useNull);
        LargeValue2.verify(val4, "exit", i + 3, useNull);
        return val4;
    }

    public static LargeValue2 testLarge2ManyArgsHelper(int i, boolean useNull, boolean park) {
        LargeValue2 val1 = useNull ? null : new LargeValue2(i);
        LargeValue2 val2 = useNull ? null : new LargeValue2(i + 1);
        LargeValue2 val3 = useNull ? null : new LargeValue2(i + 2);
        LargeValue2 val4 = useNull ? null : new LargeValue2(i + 3);
        LargeValue2 val = testLarge2ManyArgs(val1, val2, val3, val4, i, useNull, park);
        return val;
    }

    public static ValueExtendsAbstract testExtendsAbstractHelper(int i, boolean park) {
        ValueExtendsAbstract val1 = new ValueExtendsAbstract(i);
        ValueExtendsAbstract val2 = new ValueExtendsAbstract(i + 1);
        ValueExtendsAbstract val3 = new ValueExtendsAbstract(i + 2);
        ValueExtendsAbstract val4 = new ValueExtendsAbstract(i + 3);

        val1.verify("entry", i);
        val2.verify("entry", i + 1);
        val3.verify("entry", i + 2);
        val4.verify("entry", i + 3);
        if (park) {
            LockSupport.parkNanos(PARK_DURATION);
        }
        dontInline(); // Prevent C2 from optimizing out below checks
        val1.verify("exit", i);
        val2.verify("exit", i + 1);
        val3.verify("exit", i + 2);
        val4.verify("exit", i + 3);
        return val4;
    }

    public static LargeValueWithOops testLargeValueWithOops(LargeValueWithOops val, Object obj, boolean useNull, boolean park) {
        LargeValueWithOops.verify(val, "entry", obj, useNull);
        if (park) {
            LockSupport.parkNanos(PARK_DURATION);
        }
        dontInline(); // Prevent C2 from optimizing out below checks
        LargeValueWithOops.verify(val, "exit", obj, useNull);
        return val;
    }

    public static LargeValueWithOops testLargeValueWithOopsHelper(Object obj, boolean useNull, boolean park) {
        LargeValueWithOops val = useNull ? null : new LargeValueWithOops(obj);
        val = testLargeValueWithOops(val, obj, useNull, park);
        LargeValueWithOops.verify(val, "helper", obj, useNull);
        return val;
    }

    // Version that already has values on the stack even before stack extensions
    public static LargeValueWithOops testLargeValueWithOops2(LargeValueWithOops val1, LargeValueWithOops val2, LargeValueWithOops val3, LargeValueWithOops val4, LargeValueWithOops val5, Object obj, boolean useNull, boolean park) {
        LargeValueWithOops.verify(val1, "entry", obj, useNull);
        LargeValueWithOops.verify(val2, "entry", obj, useNull);
        LargeValueWithOops.verify(val3, "entry", obj, useNull);
        LargeValueWithOops.verify(val4, "entry", obj, useNull);
        LargeValueWithOops.verify(val5, "entry", obj, useNull);
        if (park) {
            LockSupport.parkNanos(PARK_DURATION);
        }
        dontInline(); // Prevent C2 from optimizing out below checks
        LargeValueWithOops.verify(val1, "exit", obj, useNull);
        LargeValueWithOops.verify(val2, "exit", obj, useNull);
        LargeValueWithOops.verify(val3, "exit", obj, useNull);
        LargeValueWithOops.verify(val4, "exit", obj, useNull);
        LargeValueWithOops.verify(val5, "exit", obj, useNull);
        return val5;
    }

    public static LargeValueWithOops testLargeValueWithOops2Helper(Object obj, boolean useNull, boolean park) {
        LargeValueWithOops val1 = useNull ? null : new LargeValueWithOops(obj);
        LargeValueWithOops val2 = useNull ? null : new LargeValueWithOops(obj);
        LargeValueWithOops val3 = useNull ? null : new LargeValueWithOops(obj);
        LargeValueWithOops val4 = useNull ? null : new LargeValueWithOops(obj);
        LargeValueWithOops val5 = useNull ? null : new LargeValueWithOops(obj);
        LargeValueWithOops val = testLargeValueWithOops2(val1, val2, val3, val4, val5, obj, useNull, park);
        LargeValueWithOops.verify(val, "helper", obj, useNull);
        return val;
    }

    // Pass via fields to not affect number of arguments
    static double testDoubleValueDP;
    static boolean testDoubleValueUseNullP;
    static boolean testDoubleValueParkP;
    static double testDoubleValueDV;
    static boolean testDoubleValueUseNullV;
    static boolean testDoubleValueParkV;

    // This method needs less stack space when scalarized because (some of) the arguments can then be passed in floating point registers
    public static DoubleValue testDoubleValue(DoubleValue val1, DoubleValue val2, DoubleValue val3, DoubleValue val4, DoubleValue val5, DoubleValue val6, DoubleValue val7) {
        boolean isVirtual = Thread.currentThread().isVirtual();
        double d = isVirtual ? testDoubleValueDV : testDoubleValueDP;
        boolean useNull = isVirtual ? testDoubleValueUseNullV : testDoubleValueUseNullP;
        boolean park = isVirtual ? testDoubleValueParkV : testDoubleValueParkP;

        DoubleValue.verify(val1, "entry", d, useNull);
        DoubleValue.verify(val2, "entry", d + 1, useNull);
        DoubleValue.verify(val3, "entry", d + 2, useNull);
        DoubleValue.verify(val4, "entry", d + 3, useNull);
        DoubleValue.verify(val5, "entry", d + 4, useNull);
        DoubleValue.verify(val6, "entry", d + 4, useNull);
        DoubleValue.verify(val7, "entry", d + 4, useNull);
        if (park) {
            LockSupport.parkNanos(PARK_DURATION);
        }
        DoubleValue.verify(val1, "exit", d, useNull);
        DoubleValue.verify(val2, "exit", d + 1, useNull);
        DoubleValue.verify(val3, "exit", d + 2, useNull);
        DoubleValue.verify(val4, "exit", d + 3, useNull);
        DoubleValue.verify(val5, "exit", d + 4, useNull);
        DoubleValue.verify(val6, "exit", d + 4, useNull);
        DoubleValue.verify(val7, "exit", d + 4, useNull);
        return val1;
    }

    public static DoubleValue testDoubleValueHelper(double d, boolean useNull, boolean park) {
        if (Thread.currentThread().isVirtual()) {
            testDoubleValueDV = d;
            testDoubleValueUseNullV = useNull;
            testDoubleValueParkV = park;
        } else {
            testDoubleValueDP = d;
            testDoubleValueUseNullP = useNull;
            testDoubleValueParkP = park;
        }

        DoubleValue val1 = useNull ? null : new DoubleValue(d);
        DoubleValue val2 = useNull ? null : new DoubleValue(d + 1);
        DoubleValue val3 = useNull ? null : new DoubleValue(d + 2);
        DoubleValue val4 = useNull ? null : new DoubleValue(d + 3);
        DoubleValue val5 = useNull ? null : new DoubleValue(d + 4);
        DoubleValue val6 = useNull ? null : new DoubleValue(d + 4);
        DoubleValue val7 = useNull ? null : new DoubleValue(d + 4);
        val1 = testDoubleValue(val1, val2, val3, val4, val5, val6, val7);
        DoubleValue.verify(val1, "helper", d, useNull);
        return val1;
    }

    public static DoubleValue2 recurseTestDoubleValue2(double d, boolean useNull, boolean park, int depth) {
        if (depth > 0) {
            DoubleValue2 val = recurseTestDoubleValue2(d, useNull, park, depth - 1);
            DoubleValue2.verify(val, "entry", d, useNull);
            dontInline(); // Prevent C2 from optimizing out below checks
            DoubleValue2.verify(val, "exit", d, useNull);
            return val;
        } else {
            if (park) {
                LockSupport.parkNanos(PARK_DURATION);
            }
            return useNull ? null : new DoubleValue2(d);
        }
    }

    public static DoubleValue2 testDoubleValue2Helper(double d, boolean useNull, boolean park) {
        DoubleValue2 val = recurseTestDoubleValue2(d, useNull, park, 4);
        DoubleValue2.verify(val, "helper", d, useNull);
        return val;
    }

    static class GarbageProducerThread extends Thread {
        public void run() {
            for (;;) {
                // Produce some garbage and then let the GC do its work
                Object[] arrays = new Object[1024];
                for (int i = 0; i < arrays.length; i++) {
                    arrays[i] = new int[1024];
                }
                System.gc();
            }
        }
    }

    public static void startTest(CountDownLatch cdl, Thread.Builder builder, int iterations) {
        builder.start(() -> {
            try {
                // Trigger compilation
                boolean isVirtual = Thread.currentThread().isVirtual();
                for (int i = 0; i < iterations; i++) {
                    boolean park = (i % 1000) == 0;
                    boolean useNull = RAND.nextBoolean();
                    Object val = useNull ? null : new SmallValue(i);
                    SmallValue.verify(testSmallHelper(i, useNull, park), "return", i, useNull);
                    LargeValue.verify(testLargeHelper(i, useNull, park), "return", i, useNull);
                    LargeValue.verify(testLargeManyArgsHelper(i, useNull, park), "return", i + 3, useNull);
                    LargeValue2.verify(testLarge2Helper(i, useNull, park), "return", i, useNull);
                    LargeValue2.verify(testLarge2ManyArgsHelper(i, useNull, park), "return", i + 3, useNull);
                    testExtendsAbstractHelper(i, park).verify("return", i + 3);
                    LargeValueWithOops.verify(testLargeValueWithOopsHelper(val, useNull, park), "return", val, useNull);
                    LargeValueWithOops.verify(testLargeValueWithOops2Helper(val, useNull, park), "return", val, useNull);
                    DoubleValue.verify(testDoubleValueHelper(i, useNull, park), "return", i, useNull);
                    DoubleValue2.verify(testDoubleValue2Helper(i, useNull, park), "return", i, useNull);
                    if (i % 1000 == 0) {
                        System.out.format("%s => %s %d of %d%n", Instant.now(), isVirtual ? "Virtual: " : "Platform:", i, iterations);
                    }
                }
                cdl.countDown();
            } catch (Exception e) {
                System.out.println("Exception thrown: " + e);
                e.printStackTrace(System.out);
                System.exit(1);
            }
        });
    }

    public static void main(String[] args) throws Exception {
        // Sometimes, exclude some methods from compilation with C1 and/or C2 to stress test the calling convention
        if (Utils.getRandomInstance().nextBoolean()) {
            ArrayList<Method> methods = new ArrayList<Method>();
            Collections.addAll(methods, SmallValue.class.getDeclaredMethods());
            Collections.addAll(methods, LargeValue.class.getDeclaredMethods());
            Collections.addAll(methods, LargeValue2.class.getDeclaredMethods());
            Collections.addAll(methods, LargeValueWithOops.class.getDeclaredMethods());
            Collections.addAll(methods, DoubleValue.class.getDeclaredMethods());
            Collections.addAll(methods, compiler.valhalla.inlinetypes.TestVirtualThreads.class.getDeclaredMethods());
            System.out.println("Excluding methods from C1 compilation:");
            for (Method m : methods) {
                if (Utils.getRandomInstance().nextBoolean()) {
                    System.out.println(m);
                    WHITE_BOX.makeMethodNotCompilable(m, COMP_LEVEL_SIMPLE, false);
                }
            }
            System.out.println("Excluding methods from C2 compilation:");
            for (Method m : methods) {
                if (Utils.getRandomInstance().nextBoolean()) {
                    System.out.println(m);
                    WHITE_BOX.makeMethodNotCompilable(m, COMP_LEVEL_FULL_OPTIMIZATION, false);
                }
            }
        }

        // Start another thread that does some allocations and calls System.gc()
        // to trigger GCs while virtual threads are parked.
        Thread garbage_producer = new GarbageProducerThread();
        garbage_producer.setDaemon(true);
        garbage_producer.start();

        int iterations = args.length > 0 ? Integer.parseInt(args[0]) : 300_000;
        if (Platform.isDebugBuild()) {
            iterations /= 4;
        }
        CountDownLatch cdlPlatform = new CountDownLatch(1);
        CountDownLatch cdlVirtual = new CountDownLatch(1);
        startTest(cdlPlatform, Thread.ofPlatform(), iterations);
        startTest(cdlVirtual, Thread.ofVirtual(), iterations);
        cdlPlatform.await();
        cdlVirtual.await();
    }
}

