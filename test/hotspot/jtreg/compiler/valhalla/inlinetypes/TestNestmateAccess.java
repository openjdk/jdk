/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.inlinetypes;

import jdk.test.lib.Asserts;

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;


/**
 * @test
 * @bug 8253416
 * @summary Test nestmate access to flattened field if nest-host is not loaded.
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.Test*::<init>
 *                   compiler.valhalla.inlinetypes.TestNestmateAccess
 * @run main/othervm -Xcomp -XX:TieredStopAtLevel=1
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.Test*::<init>
 *                   compiler.valhalla.inlinetypes.TestNestmateAccess
 * @run main/othervm compiler.valhalla.inlinetypes.TestNestmateAccess
 */

interface MyInterfaceNestmateAccess {
    int hash();
}

@LooselyConsistentValue
value class MyValueNestmateAccess implements MyInterfaceNestmateAccess {
    int x = 42;
    int y = 43;

    @Override
    public int hash() { return x + y; }
}

// Test load from flattened field in nestmate when nest-host is not loaded.
class Test1NestmateAccess {
    @NullRestricted
    private MyValueNestmateAccess vt;

    public Test1NestmateAccess(final MyValueNestmateAccess vt) {
        this.vt = vt;
        super();
    }

    public MyInterfaceNestmateAccess test() {
        return new MyInterfaceNestmateAccess() {
            // The vt field load does not link.
            private int x = (Test1NestmateAccess.this).vt.hash();

            @Override
            public int hash() { return x; }
        };
    }
}

// Same as Test1NestmateAccess but outer class is a value class
@LooselyConsistentValue
value class Test2NestmateAccess {
    @NullRestricted
    private MyValueNestmateAccess vt;

    public Test2NestmateAccess(final MyValueNestmateAccess vt) {
        this.vt = vt;
    }

    public MyInterfaceNestmateAccess test() {
        return new MyInterfaceNestmateAccess() {
            // Delayed flattened load of Test2NestmateAccess.this.
            // The vt field load does not link.
            private int x = (Test2NestmateAccess.this).vt.hash();

            @Override
            public int hash() { return x; }
        };
    }
}

// Test store to flattened field in nestmate when nest-host is not loaded.
class Test3NestmateAccess {
    private MyValueNestmateAccess vt;

    public MyInterfaceNestmateAccess test(MyValueNestmateAccess init) {
        return new MyInterfaceNestmateAccess() {
            // Store to the vt field does not link.
            private MyValueNestmateAccess tmp = (vt = init);

            @Override
            public int hash() { return tmp.hash() + vt.hash(); }
        };
    }
}

// Same as Test1NestmateAccess but with static field
class Test4NestmateAccess {
    private static MyValueNestmateAccess vt = null;

    public Test4NestmateAccess(final MyValueNestmateAccess vt) {
        this.vt = vt;
    }

    public MyInterfaceNestmateAccess test() {
        return new MyInterfaceNestmateAccess() {
            // The vt field load does not link.
            private int x = (Test4NestmateAccess.this).vt.hash();

            @Override
            public int hash() { return x; }
        };
    }
}

// Same as Test2NestmateAccess but with static field
@LooselyConsistentValue
value class Test5NestmateAccess {
    private static MyValueNestmateAccess vt;

    public Test5NestmateAccess(final MyValueNestmateAccess vt) {
        this.vt = vt;
    }

    public MyInterfaceNestmateAccess test() {
        return new MyInterfaceNestmateAccess() {
            // Delayed flattened load of Test5NestmateAccess.this.
            // The vt field load does not link.
            private int x = (Test5NestmateAccess.this).vt.hash();

            @Override
            public int hash() { return x; }
        };
    }
}

// Same as Test3NestmateAccess but with static field
class Test6NestmateAccess {
    private static MyValueNestmateAccess vt;

    public MyInterfaceNestmateAccess test(MyValueNestmateAccess init) {
        return new MyInterfaceNestmateAccess() {
            // Store to the vt field does not link.
            private MyValueNestmateAccess tmp = (vt = init);

            @Override
            public int hash() { return tmp.hash() + vt.hash(); }
        };
    }
}

// Same as Test6NestmateAccess but outer class is a value class
@LooselyConsistentValue
value class Test7NestmateAccess {
    private static MyValueNestmateAccess vt;

    public MyInterfaceNestmateAccess test(MyValueNestmateAccess init) {
        return new MyInterfaceNestmateAccess() {
            // Store to the vt field does not link.
            private MyValueNestmateAccess tmp = (vt = init);

            @Override
            public int hash() { return tmp.hash() + vt.hash(); }
        };
    }
}

public class TestNestmateAccess {

    public static void main(String[] args) {
        Test1NestmateAccess t1 = new Test1NestmateAccess(new MyValueNestmateAccess());
        int res = t1.test().hash();
        Asserts.assertEQ(res, 85);

        Test2NestmateAccess t2 = new Test2NestmateAccess(new MyValueNestmateAccess());
        res = t2.test().hash();
        Asserts.assertEQ(res, 85);

        Test3NestmateAccess t3 = new Test3NestmateAccess();
        res = t3.test(new MyValueNestmateAccess()).hash();
        Asserts.assertEQ(res, 170);

        Test4NestmateAccess t4 = new Test4NestmateAccess(new MyValueNestmateAccess());
        res = t4.test().hash();
        Asserts.assertEQ(res, 85);

        Test5NestmateAccess t5 = new Test5NestmateAccess(new MyValueNestmateAccess());
        res = t5.test().hash();
        Asserts.assertEQ(res, 85);

        Test6NestmateAccess t6 = new Test6NestmateAccess();
        res = t6.test(new MyValueNestmateAccess()).hash();
        Asserts.assertEQ(res, 170);

        Test7NestmateAccess t7 = new Test7NestmateAccess();
        res = t7.test(new MyValueNestmateAccess()).hash();
        Asserts.assertEQ(res, 170);
    }
}
