/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

package runtime.valhalla.inlinetypes;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.LayoutFocusTraversalPolicy;

import java.util.Optional;

import jdk.internal.misc.Unsafe;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.whitebox.WhiteBox;
import static jdk.test.lib.Asserts.*;

/*
 * @test
 * @ignore
 * @summary Test tearing of inline fields and array elements
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile ValueTearing.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:ForceNonTearable=
 *                   -DSTEP_COUNT=10000 -XX:+UseFieldFlattening -XX:+UseArrayFlattening
 *                   -Xbootclasspath/a:. -XX:+WhiteBoxAPI
 *                                   runtime.valhalla.inlinetypes.ValueTearing
 */

/*
 * @test
 * @ignore
 * @summary Test tearing of inline fields and array elements
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile ValueTearing.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:ForceNonTearable=*
 *                   -DSTEP_COUNT=10000 -XX:+UseFieldFlattening -XX:+UseArrayFlattening
 *                   -Xbootclasspath/a:. -XX:+WhiteBoxAPI
 *                                   runtime.valhalla.inlinetypes.ValueTearing
 */

/*
 * @test
 * @ignore
 * @summary Test tearing of inline fields and array elements
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile ValueTearing.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -DSTEP_COUNT=10000000 -XX:+UseFieldFlattening -XX:+UseArrayFlattening
 *                   -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                                   runtime.valhalla.inlinetypes.ValueTearing
 */

/*
 * @test
 * @ignore
 * @summary Test tearing of inline fields and array elements
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile ValueTearing.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:ForceNonTearable=
 *                   -DTEAR_MODE=fieldonly -XX:+UseFieldFlattening -XX:+UseArrayFlattening
 *                   -Xbootclasspath/a:. -XX:+WhiteBoxAPI
 *                                   runtime.valhalla.inlinetypes.ValueTearing
 */

/*
 * @test
 * @ignore
 * @summary Test tearing of inline fields and array elements
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile ValueTearing.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:ForceNonTearable=
 *                   -DTEAR_MODE=arrayonly -XX:+UseFieldFlattening -XX:+UseArrayFlattening
 *                   -Xbootclasspath/a:. -XX:+WhiteBoxAPI
 *                                   runtime.valhalla.inlinetypes.ValueTearing
 */

/*
 * @test
 * @ignore
 * @summary Test tearing of inline fields and array elements
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile ValueTearing.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:ForceNonTearable=*
 *                   -DTEAR_MODE=both -XX:+UseFieldFlattening -XX:+UseArrayFlattening
 *                   -Xbootclasspath/a:. -XX:+WhiteBoxAPI
 *                                   runtime.valhalla.inlinetypes.ValueTearing
 */

public class ValueTearing {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static final boolean USE_COMPILER = WHITE_BOX.getBooleanVMFlag("UseCompiler");
    private static final boolean ALWAYS_ATOMIC = WHITE_BOX.getStringVMFlag("ForceNonTearable").contains("*");
    private static final String TEAR_MODE = System.getProperty("TEAR_MODE", "both");
    private static final boolean TEAR_FIELD = !TEAR_MODE.equals("arrayonly");
    private static final boolean TEAR_ARRAY = !TEAR_MODE.equals("fieldonly");
    private static final int STEP_COUNT = Integer.getInteger("STEP_COUNT", 100_000);
    private static final boolean TFIELD_FLAT, TARRAY_FLAT;
    private static final boolean NTFIELD_FLAT, NTARRAY_FLAT;
    static {
        try {
            Field TPB_field = TPointBox.class.getDeclaredField("field");
            Field TPB_array = TPointBox.class.getDeclaredField("array");
            Field NTPB_field = NTPointBox.class.getDeclaredField("field");
            Field NTPB_array = NTPointBox.class.getDeclaredField("array");
            TFIELD_FLAT = UNSAFE.isFlatField(TPB_field);
            TARRAY_FLAT = UNSAFE.isFlatArray(TPB_array.getType());
            NTFIELD_FLAT = UNSAFE.isFlatField(NTPB_field);
            NTARRAY_FLAT = UNSAFE.isFlatArray(NTPB_array.getType());
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
    private static final String SETTINGS =
        String.format("USE_COMPILER=%s ALWAYS_ATOMIC=%s TEAR_MODE=%s STEP_COUNT=%s FLAT TF/TA=%s/%s NTF/NTA=%s/%s",
                      USE_COMPILER, ALWAYS_ATOMIC, TEAR_MODE, STEP_COUNT,
                      TFIELD_FLAT, TARRAY_FLAT, NTFIELD_FLAT, NTARRAY_FLAT);
    private static final String NOTE_TORN_POINT = "Note: torn point";

    public static void main(String[] args) throws Exception {
        System.out.println(SETTINGS);
        ValueTearing valueTearing = new ValueTearing();
        valueTearing.run();
        // Extra representation check:
        assert(!NTFIELD_FLAT) : "NT field must be indirect not flat";
        assert(!NTARRAY_FLAT) : "NT array must be indirect not flat";
        if (ALWAYS_ATOMIC) {
            assert(!TFIELD_FLAT) : "field must be indirect not flat";
            assert(!TARRAY_FLAT) : "array must be indirect not flat";
        }
    }

    // A tearable value.
    @LooselyConsistentValue
    static value class TPoint {
        TPoint(long x, long y) { this.x = x; this.y = y; }
        final long x, y;
        public String toString() { return String.format("(%d,%d)", x, y); }
    }

    static class TooTearable extends AssertionError {
        final Object badPoint;
        TooTearable(String msg, Object badPoint) {
            super(msg);
            this.badPoint = badPoint;
        }
    }

    interface PointBox {
        void step();    // mutate inline value state
        void check();   // check sanity of inline value state
    }

    class TPointBox implements PointBox {
        @NullRestricted
        TPoint field = new TPoint(0, 0);
        TPoint[] array = (TPoint[])ValueClass.newNullRestrictedNonAtomicArray(TPoint.class, 1, new TPoint(0, 0));
        // Step the points forward by incrementing their components
        // "simultaneously".  A racing thread will catch flaws in the
        // simultaneity.
        TPoint step(TPoint p) {
            return new TPoint(p.x + 1, p.y + 1);
        }
        public @Override
        void step() {
            if (TEAR_FIELD) {
                field = step(field);
            }
            if (TEAR_ARRAY) {
                array[0] = step(array[0]);
            }
            check();
        }
        // Invariant:  The components of each point are "always" equal.
        // As long as simultaneity is preserved, this is true.
        public @Override
        void check() {
            if (TEAR_FIELD) {
                check(field, "field");
            }
            if (TEAR_ARRAY) {
                check(array[0], "array element");
            }
        }
        void check(TPoint p, String where) {
            if (p.x == p.y)  return;
            String msg = String.format("%s %s in %s; settings = %s",
                                       NOTE_TORN_POINT,
                                       p, where, SETTINGS);
            throw new TooTearable(msg, p);
        }
        public String toString() {
            return String.format("TPB[%s, {%s}]", field, array[0]);
        }
    }


    // A non-tearable version of TPoint.
    static value class NTPoint {
        NTPoint(long x, long y) { this.x = x; this.y = y; }
        final long x, y;
        public String toString() { return String.format("(%d,%d)", x, y); }
    }

    class NTPointBox implements PointBox {
        @NullRestricted
        NTPoint field = new NTPoint(0, 0);
        NTPoint[] array = (NTPoint[])ValueClass.newNullRestrictedNonAtomicArray(NTPoint.class, 1, new NTPoint(0, 0));
        // Step the points forward by incrementing their components
        // "simultaneously".  A racing thread will catch flaws in the
        // simultaneity.
        NTPoint step(NTPoint p) {
            return new NTPoint(p.x + 1, p.y + 1);
        }
        public @Override
        void step() {
            field = step(field);
            array[0] = step(array[0]);
            check();
        }
        // Invariant:  The components of each point are "always" equal.
        public @Override
        void check() {
            check(field, "field");
            check(array[0], "array element");
        }
        void check(NTPoint p, String where) {
            if (p.x == p.y)  return;
            String msg = String.format("%s *NonTearable* %s in %s; settings = %s",
                                       NOTE_TORN_POINT,
                                       p, where, SETTINGS);
            throw new TooTearable(msg, p);
        }
        public String toString() {
            return String.format("NTPB[%s, {%s}]", field, array[0]);
        }
    }

    class AsyncObserver extends Thread {
        volatile boolean done;
        long observationCount;
        final PointBox pointBox;
        volatile Object badPointObserved;
        AsyncObserver(PointBox pointBox) {
            this.pointBox = pointBox;
        }
        public void run() {
            try {
                while (!done) {
                    observationCount++;
                    pointBox.check();
                }
            } catch (TooTearable ex) {
                done = true;
                badPointObserved = ex.badPoint;
                System.out.println(ex);
                if (ALWAYS_ATOMIC || !badPointObserved.getClass().isAnnotationPresent(LooselyConsistentValue.class)) {
                    throw ex;
                }
            }
        }
    }

    public void run() throws Exception {
        System.out.println("Test for tearing of NTPoint, which must not happen...");
        run(new NTPointBox(), false);
        System.out.println("Test for tearing of TPoint, which "+
                           (ALWAYS_ATOMIC ? "must not" : "is allowed to")+
                           " happen...");
        run(new TPointBox(), ALWAYS_ATOMIC ? false : true);
    }
    public void run(PointBox pointBox, boolean canTear) throws Exception {
        var observer = new AsyncObserver(pointBox);
        observer.start();
        for (int i = 0; i < STEP_COUNT; i++) {
            pointBox.step();
            if (observer.done)  break;
        }
        observer.done = true;
        observer.join();
        var obCount = observer.observationCount;
        var badPoint = observer.badPointObserved;
        System.out.println(String.format("finished after %d observations at %s; %s",
                                         obCount, pointBox,
                                         (badPoint == null
                                          ? "no tearing observed"
                                          : "bad point = " + badPoint)));
        if (canTear && badPoint == null) {
            var complain = String.format("%s NOT observed after %d observations",
                                         NOTE_TORN_POINT, obCount);
            System.out.println("?????? "+complain);
            if (STEP_COUNT >= 3_000_000) {
                // If it's a small count, OK, but if it's big the test is broken.
                throw new AssertionError(complain + ", but it should have been");
            }
        }
        if (!canTear && badPoint != null) {
            throw new AssertionError("should not reach here; other thread must throw");
        }
    }
}
