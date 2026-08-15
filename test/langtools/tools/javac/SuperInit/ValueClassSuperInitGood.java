/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8324873
 * @summary Test valid placements of super()/this() in constructors
 * @enablePreview
 */

import java.util.ArrayList;
import java.util.List;

public value class ValueClassSuperInitGood {

    ValueClassSuperInitGood(Object obj) {
    }

    ValueClassSuperInitGood(int x) {
    }

    // Default constructor provided by compiler
    static value class Test0 {
    }

    // No explicit calls to this()/super()
    static abstract value class Test1 {
        Test1() {
        }
        Test1(int a) {
            super();
            this.hashCode();
        }
    }

    // Explicit calls to this()/super()
    static abstract value class Test2<T> {
        static int i;
        Test2() {
            this(0);
        }
        Test2(int i) {
            Test2.i = i;
            super();
        }
        Test2(T obj) {
            this(java.util.Objects.hashCode(obj));
        }
        public T get() {
            return null;
        }
    }

    // Explicit this()/super() with stuff in front
    static value class Test3 {
        int x;
        final int y;
        final int z;

        Test3() {
            new Object().hashCode();
            new Object().hashCode();
            this.x = new Object().hashCode();
            this.y = new Object().hashCode() % 17;
            this.z = this.x + this.y;
            super();
        }
    }

    static abstract value class Test5Abstract {
        Test5Abstract(Object obj) {}
    }

    // Reference within constructor to outer class that's also my superclass
    abstract value class Test5 extends Test5Abstract {
        Test5(Object obj) {
            if (obj == null)
                throw new IllegalArgumentException();
            super(ValueClassSuperInitGood.this);      // NOT a 'this' reference
        }
    }

    // Initialization blocks
    value class Test6 {
        final long startTime;
        List<String> l = new ArrayList<>();
        {
            l.add("");
        }
        Test6() {
            long now = System.nanoTime();
            long then = now + 1000000L;
            while (System.nanoTime() < then) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            this.startTime = now;
            super();
        }
    }

    // Mix up inner classes, proxies, and super() calls
    // Copied mostly from UnverifiableInitForNestedLocalClassTest.java
    public static void test7(final String arg) {
        final String inlined = " inlined ";
        abstract value class LocalClass {
            String m() {
                return "LocalClass " + arg + inlined;
            }

            abstract value class SubClass extends LocalClass {
                @Override
                String m() {
                    return "SubClass " + arg + inlined;
                }
            }

            value class SubSubClass extends SubClass {
                @Override
                String m() {
                    return "SubSubClass " + arg + inlined;
                }
            }

            value class AnotherLocal {
                value class AnotherSub extends LocalClass {
                    AnotherSub() {
                    }
                    AnotherSub(int x) {
                        this((char)x);
                    }
                    AnotherSub(char y) {
                        super();
                    }
                    @Override
                    String m() {
                        return "AnotherSub " + arg + inlined;
                    }
                }
            }
        }
    }

    // Anonymous inner class
    public static void test8() {
        new Test2<Byte>(null) {
            @Override
            public Byte get() {
                return (byte)-1;
            }
        };
    }

    // Qualified super() invocation
    public static value class Test9 extends Test5 {

        public Test9(ValueClassSuperInitGood implicit, Object obj) {
            obj.hashCode();
            implicit.super(obj);
        }
    }

    // Copied from WhichImplicitThis6
    public static abstract value class Test10 {
        private int i;
        public Test10(int i) { this.i = i; }
        public value class Sub extends Test10 {
            public Sub() {
                super(i); // i is not inherited, so it is the enclosing i
            }
        }
    }

    // Two constructors where only one invokes super()
    public static value class Test11 {
        public Test11() {
        }
        public Test11(int x) {
            super();
        }
    }

    // Nested version of the previous test
    public static value class Test12 {
        Test12() {
            class Sub {
                public Sub() {
                }
                public Sub(int j) {
                    super();
                }
            }
        }
    }

    // Nested super()'s requiring initialization code appended
    public static value class Test13 extends Test5Abstract {
        final int x = new Object().hashCode();
        Test13() {
            super(new Object() {
                public void foo() {
                    class Bar {
                        final int y = new Object().hashCode();
                        Bar() {
                            super();
                        }
                        Bar(int ignored) {
                        }
                    }
                }
            });
        }
    }

    // Qualified super() invocation with superclass instance
    public static abstract value class Test15 {

        final String name;

        public Test15(String name) {
            this.name = name;
        }

        public abstract value class Test15b extends Test15 {

            public Test15b(String name) {
                super(name);
            }

            public String getName() {
                return Test15.this.name;
            }
        }
    }

    public static value class Test15c extends Test15.Test15b {
        public Test15c(Test15 a, String name) {
            a.super(name);
        }
    }

    // Mixing up outer instances, proxies, and initializers
    public static value class Test16 {

        final String x = String.valueOf(new Object().hashCode());

        public void run() {

            final String y = String.valueOf(new Object().hashCode());

            class Sub {

                final String z;

                Sub(String z, int ignored) {
                    this(z, (float)ignored);
                }

                Sub(String z, float ignored) {
                    this.z = z;
                }

                Sub(String z, byte ignored) {
                    super();
                    this.z = z;
                }

                Sub(String z, char ignored) {
                    this(z, (int)ignored);
                }

                String x() {
                    return x;
                }

                String y() {
                    return y;
                }

                String z() {
                    return z;
                }
            }

            final String z = String.valueOf(new Object().hashCode());

            final Sub[] subs = new Sub[] {
                new Sub(z, 1),
                new Sub(z, -1),
                new Sub(z, (float)0),
                new Sub(z, (byte)0),
                new Sub(z, (char)0)
            };

            for (int i = 0; i < subs.length; i++) {
                //System.err.println("i = " + i);
                final Sub sub = subs[i];
                final String subx = sub.x();
                final String suby = sub.y();
                final String subz = sub.z();
                if (!x.equals(subx))
                    throw new RuntimeException("x=" + x + " but sub[" + i + "].x()=" + subx);
                if (!y.equals(suby))
                    throw new RuntimeException("y=" + y + " but sub[" + i + "].y()=" + suby);
                if (!z.equals(subz))
                    throw new RuntimeException("z=" + z + " but sub[" + i + "].z()=" + subz);
            }
        }
    }

    // Records
    public value class Test17 {

        record Rectangle(float length, float width) { }

        record StringHolder(String string) {
            StringHolder {
                java.util.Objects.requireNonNull(string);
            }
        }

        record ValueHolder(int value) {
            ValueHolder(float x) {
                if (Float.isNaN(x))
                    throw new IllegalArgumentException();
                this((int)x);
            }
        }
    }

    static abstract value class AR<V> implements java.io.Serializable {
        public AR(V initialValue) {
        }

        public AR() {
        }

        public final V get() {
            return null;
        }
    }

    // super()/this() within outer try block but inside inner class
    public static value class Test19 {
        public Test19(int x) {
            try {
                new Test1(x) {
                    @Override
                    public int hashCode() {
                        return x ^ super.hashCode();
                    }
                };
            } catch (StackOverflowError e) {
                // ignore
            }
        }
    }

    public static value class Test20 {
        private final int[] data1 = new int[10];
        private final int[] data2 = new int[10];
        private final int[] data3 = new int[10];
        Test20() {
            for (int i = 0; i < data1.length; i++) {
                data1[i] = i; // OK we are assigning to an array component
                this.data2[i] = i; // OK we are assigning to an array component
                Test20.this.data3[i] = i; // OK we are assigning to an array component
            }
        }
    }

    public static void main(String[] args) {
        new Test0();
        new Test1() {};
        new Test1(7) {};
        new Test2<Byte>() {};
        new Test2<>(args) {};
        new Test3();
        new ValueClassSuperInitGood(3).new Test5(3) {};
        new ValueClassSuperInitGood(3).new Test6();
        ValueClassSuperInitGood.test7("foo");
        ValueClassSuperInitGood.test8();
        new Test9(new ValueClassSuperInitGood(5), "abc");
        new Test10(7) {};
        new Test11(9);
        new Test12();
        new Test13();
        new Test15c(new Test15("foo"){}, "bar");
        new Test16().run();
        new Test17.StringHolder("foo");
        try {
            new Test17.StringHolder(null);
            throw new Error();
        } catch (NullPointerException e) {
            // expected
        }
        new Test19(123);
        new Test20();
    }
}
