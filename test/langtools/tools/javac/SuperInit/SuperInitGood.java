/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8194743
 * @summary Test valid placements of super()/this() in constructors
 * @enablePreview
 */

import java.util.concurrent.atomic.AtomicReference;

public class SuperInitGood {

    SuperInitGood(Object obj) {
    }

    SuperInitGood(int x) {
    }

    // Default constructor provided by compiler
    static class Test0 {
    }

    // No explicit calls to this()/super()
    static class Test1 {
        Test1() {
        }
        Test1(int a) {
            this.hashCode();
        }
    }

    // Explicit calls to this()/super()
    static class Test2<T> {
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
    static class Test3 {
        int x;
        final int y;
        final int z;

        Test3() {
            new Object().hashCode();
            new Object().hashCode();
            super();
            this.x = new Object().hashCode();
            this.y = new Object().hashCode() % 17;
            this.z = this.x + this.y;
        }
    }

    // Reference within constructor to outer class that's also my superclass
    class Test5 extends SuperInitGood {
        Test5(Object obj) {
            if (obj == null)
                throw new IllegalArgumentException();
            super(SuperInitGood.this);      // NOT a 'this' reference
        }
    }

    // Initialization blocks
    class Test6 {
        final long startTime;
        final int x;
        {
            this.x = 12;
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
            super();
            this.startTime = now;
        }
    }

    // Mix up inner classes, proxies, and super() calls
    // Copied mostly from UnverifiableInitForNestedLocalClassTest.java
    public static void test7(final String arg) {
        final String inlined = " inlined ";
        class LocalClass {
            String m() {
                return "LocalClass " + arg + inlined;
            }

            class SubClass extends LocalClass {
                @Override
                String m() {
                    return "SubClass " + arg + inlined;
                }
            }

            class SubSubClass extends SubClass {
                @Override
                String m() {
                    return "SubSubClass " + arg + inlined;
                }
            }

            class AnotherLocal {
                class AnotherSub extends LocalClass {
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
    public static class Test9 extends Test5 {

        public Test9(SuperInitGood implicit, Object obj) {
            obj.hashCode();
            implicit.super(obj);
        }
    }

    // Copied from WhichImplicitThis6
    public static class Test10 {
        private int i;
        public Test10(int i) {}
        public class Sub extends Test10 {
            public Sub() {
                super(i); // i is not inherited, so it is the enclosing i
            }
        }
    }

    // Two constructors where only one invokes super()
    public static class Test11 {
        public Test11() {
        }
        public Test11(int x) {
            super();
        }
    }

    // Nested version of the previous test
    public static class Test12 {
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
    public static class Test13 extends SuperInitGood {
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

    // Initializer in initializer block
    public static class Test14 {
        final int x;                // initialized in constructor
        final int y;                // initialized in initialization block
        final int z = 13;           // initialized with intializer value
        public Test14() {
            this(0);
        }
        public Test14(boolean z) {
            this.x = z ? 1 : 0;
        }
        public Test14(int x) {
            super();
            this.x = x;
        }
        {
            this.y = -1;
        }
    }

    // Qualified super() invocation with superclass instance
    public static class Test15 {

        final String name;

        public Test15(String name) {
            this.name = name;
        }

        public class Test15b extends Test15 {

            public Test15b(String name) {
                super(name);
            }

            public String getName() {
                return Test15.this.name;
            }
        }
    }

    public static class Test15c extends Test15.Test15b {
        public Test15c(Test15 a, String name) {
            a.super(name);
        }
    }

    // Mixing up outer instances, proxies, and initializers
    public static class Test16 {

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
    public class Test17 {

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

    // Exceptions thrown by initializer block
    public static class Test18 extends AtomicReference<Object> {

        {
            if ((this.get().hashCode() % 3) == 0)
                throw new MyException();
        }

        public Test18(Object obj) throws MyException {
            super(obj);
        }

        public Test18(boolean fail) throws MyException {
            Object obj;
            for (obj = new Object(); true; obj = new Object()) {
                if (((obj.hashCode() % 3) == 0) != fail)
                    continue;
                break;
            }
            this(obj);
        }

        public static class MyException extends Exception {
        }
    }

    // super()/this() within outer try block but inside inner class
    public static class Test19 {
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

    // we allow 'this' reference prior to super() for field assignments only
    public static class Test20 {
        private int x;
        public Test20(short x) {
            x = x;
            super();
        }
        public Test20(int x) {
            this.x = x;
            super();
        }
        public Test20(char x) {
            Test20.this.x = x;
            super();
        }
        public Test20(byte y) {
            x = y;
            this((int)y);
            this.x++;
        }
    }

    // allow creating and using local and anonymous classes before super()
    // they will not have enclosing instances though
    public static class Test21 {
        public Test21(int x) {
            Runnable r = new Runnable() {
                public void run() {
                    this.hashCode();
                }
            };
            r.run();
            super();
            r.run();
        }
        public Test21(float x) {
            class Foo {
                public void bar() {
                    this.hashCode();
                }
            };
            new Foo().bar();
            super();
            new Foo().bar();
        }
    }


    public static void main(String[] args) {
        new Test0();
        new Test1();
        new Test1(7);
        new Test2<Byte>();
        new Test2<>(args);
        new Test3();
        new SuperInitGood(3).new Test5(3);
        new SuperInitGood(3).new Test6();
        SuperInitGood.test7("foo");
        SuperInitGood.test8();
        new Test9(new SuperInitGood(5), "abc");
        new Test10(7);
        new Test11(9);
        new Test12();
        new Test13();
        Test14 t14 = new Test14();
        assert t14.x == 0 && t14.y == -1 && t14.z == 13;
        t14 = new Test14(7);
        assert t14.x == 7 && t14.y == -1 && t14.z == 13;
        new Test15c(new Test15("foo"), "bar");
        new Test16().run();
        new Test17.StringHolder("foo");
        try {
            new Test17.StringHolder(null);
            throw new Error();
        } catch (NullPointerException e) {
            // expected
        }
        try {
            new Test18(true);
            assert false : "expected exception";
        } catch (Test18.MyException e) {
            // expected
        }
        try {
            new Test18(false);
        } catch (Test18.MyException e) {
            assert false : "unexpected exception: " + e;
        }
        new Test19(123);
        new Test20(123);
        new Test21((int)123);
        new Test21((float)123);
    }
}
