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
package jdk.jpackage.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class ObjectMapperTest {

    @Test
    public void test_String() {
        var om = ObjectMapper.blank().create();

        var map = om.map("foo");

        assertEquals("foo", map);
    }

    @Test
    public void test_int() {
        var om = ObjectMapper.blank().create();

        var map = om.map(100);

        assertEquals(100, map);
    }

    @Test
    public void test_null() {
        var om = ObjectMapper.blank().create();

        var map = om.map(null);

        assertNull(map);
    }

    @Test
    public void test_Object() {
        var obj = new Object();
        assertSame(obj, ObjectMapper.blank().create().map(obj));
        assertSame(obj, ObjectMapper.standard().create().map(obj));
    }

    @Test
    public void test_Path() {
        var obj = Path.of("foo/bar");

        assertSame(obj, ObjectMapper.standard().create().map(obj));
    }

    @Test
    public void test_UUID() {
        var obj = UUID.randomUUID();

        assertSame(obj, ObjectMapper.standard().create().map(obj));
    }

    @Test
    public void test_BigInteger() {
        var obj = BigInteger.TEN;

        assertSame(obj, ObjectMapper.standard().create().map(obj));
    }

    @Test
    public void test_Enum() {

        var expected = Map.of(
                "name", TestEnum.BAR.name(),
                "ordinal", TestEnum.BAR.ordinal(),
                "a", "A",
                "b", 123,
                "num", 100
        );

        assertEquals(expected, ObjectMapper.standard().create().map(TestEnum.BAR));
    }

    @Test
    public void test_array_int() {

        var obj = new int[] { 1, 4, 5 };

        assertSame(obj, ObjectMapper.standard().create().map(obj));
    }

    @Test
    public void test_array_String() {

        var obj = new String[] { "Hello", "Bye" };

        assertSame(obj, ObjectMapper.standard().create().map(obj));
    }

    @Test
    public void test_array_empty() {

        var obj = new Thread[0];

        assertSame(obj, ObjectMapper.standard().create().map(obj));
    }

    @Test
    public void test_array_nulls() {

        var obj = new Thread[10];

        assertSame(obj, ObjectMapper.standard().create().map(obj));
    }

    @Test
    public void test_array_Path() {

        var obj = new Path[] { Path.of("foo/bar"), null, Path.of("").toAbsolutePath() };

        assertSame(obj, ObjectMapper.standard().create().map(obj));
    }

    @Test
    public void test_array_Object() {

        var obj = new Object[] { Path.of("foo/bar"), null, 145, new Simple.Stub("Hello", 738), "foo" };

        var expected = new Object[] { Path.of("foo/bar"), null, 145, Map.of("a", "Hello", "b", 738), "foo" };

        assertArrayEquals(expected, (Object[])ObjectMapper.standard().create().map(obj));
    }

    @Test
    public void test_functional() {
        assertWrappedIdentity(new Function<Object, Integer>() {

            @Override
            public Integer apply(Object o) {
                throw new AssertionError();
            }

        });

        assertWrappedIdentity(new BiFunction<Object, String, Integer>() {

            @Override
            public Integer apply(Object a, String b) {
                throw new AssertionError();
            }

        });

        assertWrappedIdentity(new Consumer<>() {

            @Override
            public void accept(Object o) {
                throw new AssertionError();
            }

        });

        assertWrappedIdentity(new BiConsumer<>() {

            @Override
            public void accept(Object a, Object b) {
                throw new AssertionError();
            }

        });

        assertWrappedIdentity(new Predicate<>() {

            @Override
            public boolean test(Object o) {
                throw new AssertionError();
            }

        });

        assertWrappedIdentity(new Supplier<>() {

            @Override
            public Object get() {
                throw new AssertionError();
            }

        });

        assertWrappedIdentity(new Runnable() {

            @Override
            public void run() {
                throw new AssertionError();
            }

        });
    }

    @Test
    public void testIdentityWrapper() {
        var om = ObjectMapper.standard().create();

        var a = new Object() {};
        var b = new Object() {};

        var amap = om.map(a);
        var amap2 = om.map(a);

        assertEquals(amap, amap2);
        assertEquals(ObjectMapper.wrapIdentity(a), amap);

        var bmap = om.map(b);

        assertNotEquals(amap, bmap);
        assertEquals(ObjectMapper.wrapIdentity(b), bmap);
    }

    @Test
    public void test_wrapIdentity() {

        assertThrowsExactly(NullPointerException.class, () -> ObjectMapper.wrapIdentity(null));

        var iw = ObjectMapper.wrapIdentity(new Object());

        assertSame(iw, ObjectMapper.wrapIdentity(iw));

        var simpleStubA = new Simple.Stub("Hello", 77);
        var simpleStubB = new Simple.Stub("Hello", 77);

        assertEquals(simpleStubA, simpleStubB);
        assertNotEquals(ObjectMapper.wrapIdentity(simpleStubA), ObjectMapper.wrapIdentity(simpleStubB));
        assertEquals(ObjectMapper.wrapIdentity(simpleStubA), ObjectMapper.wrapIdentity(simpleStubA));
    }

    @Test
    public void test_empty_List() {
        var om = ObjectMapper.blank().create();

        var map = om.map(List.of());

        assertEquals(List.of(), map);
    }

    @Test
    public void test_List() {
        var om = ObjectMapper.blank().create();

        var map = om.map(List.of(100, "foo"));

        assertEquals(List.of(100, "foo"), map);
    }

    @Test
    public void test_empty_Map() {
        var om = ObjectMapper.blank().create();

        var map = om.map(Map.of());

        assertEquals(Map.of(), map);
    }

    @Test
    public void test_Map() {
        var om = ObjectMapper.blank().create();

        var map = om.map(Map.of(100, "foo"));

        assertEquals(Map.of(100, "foo"), map);
    }

    @Test
    public void test_MapSimple() {
        var om = ObjectMapper.standard().create();

        var map = om.map(Map.of(123, "foo", 321, new Simple.Stub("Hello", 567)));

        assertEquals(Map.of(123, "foo", 321, Map.of("a", "Hello", "b", 567)), map);
    }

    @Test
    public void test_ListSimple() {
        var om = ObjectMapper.standard().create();

        var map = om.map(List.of(100, new Simple.Stub("Hello", 567), "bar", new Simple() {}));

        assertEquals(List.of(100, Map.of("a", "Hello", "b", 567), "bar", Map.of("a", "foo", "b", 123)), map);
    }

    @Test
    public void test_Simple() {
        var om = ObjectMapper.standard().create();

        var map = om.map(new Simple() {});

        assertEquals(Map.of("a", "foo", "b", 123), map);
    }

    @Test
    public void test_Proxy() {
        var om = ObjectMapper.standard().create();

        var map = om.map(Proxy.newProxyInstance(Simple.class.getClassLoader(), new Class<?>[] { Simple.class }, new InvocationHandler() {

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                switch (method.getName()) {
                    case "a" -> {
                        return "Bye";
                    }
                    case "b" -> {
                        return 335;
                    }
                    default -> {
                        throw new UnsupportedOperationException();
                    }
                }
            }

        }));

        assertEquals(Map.of("a", "Bye", "b", 335), map);
    }

    @Test
    public void test_Simple_null_property() {
        var om = ObjectMapper.standard().create();

        var map = om.map(new Simple.Stub(null, 123));

        assertEquals(Map.of("b", 123, "a", ObjectMapper.NULL), map);
    }

    @Test
    public void test_Optional_String() {
        var om = ObjectMapper.standard().create();

        var map = om.map(Optional.of("foo"));

        assertEquals(Map.of("get", "foo"), map);
    }

    @Test
    public void test_Optional_empty() {
        var om = ObjectMapper.standard().create();

        var map = om.map(Optional.empty());

        assertEquals(Map.of("get", ObjectMapper.NULL), map);
    }

    @Test
    public void test_toMap() {
        var om = ObjectMapper.standard().create();

        assertNull(om.toMap(null));
        assertEquals(Map.of("value", "Hello"), om.toMap("Hello"));
        assertEquals(Map.of("a", "foo", "b", 123), om.toMap(new Simple() {}));
    }

    @Test
    public void test_getter_throws() {
        var om = ObjectMapper.blank()
                .mutate(ObjectMapper.configureObject())
                .mutate(ObjectMapper.configureLeafClasses())
                .mutate(ObjectMapper.configureException())
                .create();

        var expected = Map.of("get", om.toMap(new UnsupportedOperationException("Not for you!")));

        var actual = om.toMap(new Supplier<>() {
            @Override
            public Object get() {
                throw new UnsupportedOperationException("Not for you!");
            }
        });

        assertEquals(expected, actual);
    }

    @Test
    public void test_exception_with_message_with_cause() {

        var ex = new Exception("foo", new IllegalArgumentException("Cause", new RuntimeException("Ops!")));

        var om = ObjectMapper.standard().create();

        var map = om.toMap(ex);

        assertEquals(Map.of(
                "getClass", Exception.class.getName(),
                "getMessage", "foo",
                "getCause", Map.of(
                        "getClass", IllegalArgumentException.class.getName(),
                        "getMessage", "Cause",
                        "getCause", Map.of(
                                "getClass", RuntimeException.class.getName(),
                                "getMessage", "Ops!"
                        )
                )
        ), map);
    }

    @Test
    public void test_exception_without_message_with_cause() {

        var ex = new RuntimeException(null, new UnknownError("Ops!"));

        var om = ObjectMapper.standard().create();

        var map = om.toMap(ex);

        assertEquals(Map.of(
                "getClass", RuntimeException.class.getName(),
                "getCause", Map.of(
                        "getMessage", "Ops!",
                        "getCause", ObjectMapper.NULL
                )
        ), map);
    }

    @Test
    public void test_exception_without_message_without_cause() {

        var ex = new UnsupportedOperationException();

        var om = ObjectMapper.standard().create();

        var map = om.toMap(ex);

        assertEquals(Map.of("getClass", UnsupportedOperationException.class.getName()), map);
    }

    @Test
    public void test_exception_CustomException() {

        var ex = new CustomException("Hello", Path.of(""), Optional.empty(), null);

        var om = ObjectMapper.standard().create();

        var map = om.toMap(ex);

        assertEquals(Map.of(
                "getClass", CustomException.class.getName(),
                "getMessage", "Hello",
                "op", Map.of("get", ObjectMapper.NULL),
                "path2", Path.of("")
        ), map);
    }

    @Test
    public void test_Builder_accessPackageMethods() {

        var obj = new TestType().foo("Hello").bar(81);

        var map = ObjectMapper.standard().create().toMap(obj);

        assertEquals(Map.of("foo", "Hello"), map);

        map = ObjectMapper.standard().accessPackageMethods(TestType.class.getPackage()).create().toMap(obj);

        assertEquals(Map.of("foo", "Hello", "bar", 81), map);
    }

    @Test
    public void test_Builder_methods_Simple() {

        var om = ObjectMapper.standard().exceptSomeMethods(Simple.class).add("a").apply().create();

        assertEquals(Map.of("b", 123), om.toMap(new Simple() {}));
        assertEquals(Map.of("b", 345), om.toMap(new Simple.Stub("Hello", 345)));
        assertEquals(Map.of("b", 123), om.toMap(new Simple.Default("Hello")));
        assertEquals(Map.of("b", 345 + 10), om.toMap(new Simple.DefaultExt("Hello", 345)));

        om = ObjectMapper.standard().exceptSomeMethods(Simple.class).add("b").apply().create();

        assertEquals(Map.of("a", "foo"), om.toMap(new Simple() {}));
        assertEquals(Map.of("a", "Hello"), om.toMap(new Simple.Stub("Hello", 345)));
        assertEquals(Map.of("a", "Hello"), om.toMap(new Simple.Default("Hello")));
        assertEquals(Map.of("a", "[Hello]"), om.toMap(new Simple.DefaultExt("Hello", 345)));
    }

    @Test
    public void test_Builder_methods_SimpleStub() {

        var om = ObjectMapper.standard().exceptSomeMethods(Simple.Stub.class).add("a").apply().create();

        assertEquals(Map.of("a", "foo", "b", 123), om.toMap(new Simple() {}));
        assertEquals(Map.of("b", 345), om.toMap(new Simple.Stub("Hello", 345)));
        assertEquals(Map.of("a", "Hello", "b", 123), om.toMap(new Simple.Default("Hello")));
        assertEquals(Map.of("a", "[Hello]", "b", 345 + 10), om.toMap(new Simple.DefaultExt("Hello", 345)));

        om = ObjectMapper.standard().exceptSomeMethods(Simple.Stub.class).add("b").apply().create();

        assertEquals(Map.of("a", "foo", "b", 123), om.toMap(new Simple() {}));
        assertEquals(Map.of("a", "Hello"), om.toMap(new Simple.Stub("Hello", 345)));
        assertEquals(Map.of("a", "Hello", "b", 123), om.toMap(new Simple.Default("Hello")));
        assertEquals(Map.of("a", "[Hello]", "b", 345 + 10), om.toMap(new Simple.DefaultExt("Hello", 345)));
    }

    @Test
    public void test_Builder_methods_SimpleDefault() {

        var om = ObjectMapper.standard().exceptSomeMethods(Simple.Default.class).add("a").apply().create();

        assertEquals(Map.of("a", "foo", "b", 123), om.toMap(new Simple() {}));
        assertEquals(Map.of("a", "Hello", "b", 345), om.toMap(new Simple.Stub("Hello", 345)));
        assertEquals(Map.of("b", 123), om.toMap(new Simple.Default("Hello")));
        assertEquals(Map.of("b", 345 + 10), om.toMap(new Simple.DefaultExt("Hello", 345)));

        om = ObjectMapper.standard().exceptSomeMethods(Simple.Default.class).add("b").apply().create();

        assertEquals(Map.of("a", "foo"), om.toMap(new Simple() {}));
        assertEquals(Map.of("a", "Hello"), om.toMap(new Simple.Stub("Hello", 345)));
        assertEquals(Map.of("a", "Hello"), om.toMap(new Simple.Default("Hello")));
        assertEquals(Map.of("a", "[Hello]"), om.toMap(new Simple.DefaultExt("Hello", 345)));
    }

    @Test
    public void test_Builder_methods_SimpleDefaultExt() {

        var om = ObjectMapper.standard().exceptSomeMethods(Simple.DefaultExt.class).add("a").apply().create();

        assertEquals(Map.of("a", "foo", "b", 123), om.toMap(new Simple() {}));
        assertEquals(Map.of("a", "Hello", "b", 345), om.toMap(new Simple.Stub("Hello", 345)));
        assertEquals(Map.of("a", "Hello", "b", 123), om.toMap(new Simple.Default("Hello")));
        assertEquals(Map.of("b", 345 + 10), om.toMap(new Simple.DefaultExt("Hello", 345)));

        om = ObjectMapper.standard().exceptSomeMethods(Simple.DefaultExt.class).add("b").apply().create();

        assertEquals(Map.of("a", "foo", "b", 123), om.toMap(new Simple() {}));
        assertEquals(Map.of("a", "Hello", "b", 345), om.toMap(new Simple.Stub("Hello", 345)));
        assertEquals(Map.of("a", "Hello", "b", 123), om.toMap(new Simple.Default("Hello")));
        assertEquals(Map.of("a", "[Hello]"), om.toMap(new Simple.DefaultExt("Hello", 345)));
    }

    @Test
    public void test_Builder_methods_SimpleStub_and_SimpleDefault() {

        var om = ObjectMapper.standard()
                .exceptSomeMethods(Simple.Stub.class).add("a").apply()
                .exceptSomeMethods(Simple.Default.class).add("a").apply()
                .create();

        assertEquals(Map.of("a", "foo", "b", 123), om.toMap(new Simple() {}));
        assertEquals(Map.of("b", 345), om.toMap(new Simple.Stub("Hello", 345)));
        assertEquals(Map.of("b", 123), om.toMap(new Simple.Default("Hello")));
        assertEquals(Map.of("b", 345 + 10), om.toMap(new Simple.DefaultExt("Hello", 345)));

        om = ObjectMapper.standard()
                .exceptSomeMethods(Simple.Stub.class).add("b").apply()
                .exceptSomeMethods(Simple.Default.class).add("b").apply()
                .create();

        assertEquals(Map.of("a", "foo"), om.toMap(new Simple() {}));
        assertEquals(Map.of("a", "Hello"), om.toMap(new Simple.Stub("Hello", 345)));
        assertEquals(Map.of("a", "Hello"), om.toMap(new Simple.Default("Hello")));
        assertEquals(Map.of("a", "[Hello]"), om.toMap(new Simple.DefaultExt("Hello", 345)));
    }

    @Test
    public void test_Builder_methods_all_excluded() {

        var om = ObjectMapper.standard()
                .exceptSomeMethods(Simple.class).add("a").apply()
                .exceptSomeMethods(Simple.Stub.class).add("b").apply()
                .create();

        var obj = new Simple.Stub("Hello", 345);

        assertEquals(ObjectMapper.wrapIdentity(obj), om.map(obj));
    }

    interface Simple {
        default String a() {
            return "foo";
        }

        default int b() {
            return 123;
        }

        record Stub(String a, int b) implements Simple {}

        static class Default implements Simple {
            Default(String a) {
                this.a = a;
            }

            @Override
            public String a() {
                return a;
            }

            private final String a;
        }

        static class DefaultExt extends Default {
            DefaultExt(String a, int b) {
                super(a);
                this.b = b;
            }

            @Override
            public String a() {
                return "[" + super.a() + "]";
            }

            @Override
            public int b() {
                return 10 + b;
            }

            private final int b;
        }
    }

    final class TestType {

        public String foo() {
            return foo;
        }

        public TestType foo(String v) {
            foo = v;
            return this;
        }

        int bar() {
            return bar;
        }

        TestType bar(int v) {
            bar = v;
            return this;
        }

        private String foo;
        private int bar;
    }

    enum TestEnum implements Simple {
        FOO,
        BAR;

        public int num() {
            return 100;
        }

        public int num(int v) {
            return v;
        }

        @Override
        public String a() {
            return "A";
        }
    }

    static final class CustomException extends Exception {

        CustomException(String message, Path path, Optional<Object> optional, Throwable cause) {
            super(message, cause);
            this.path = path;
            this.optional = optional;
        }

        Path path() {
            return path;
        }

        public Path path2() {
            return path;
        }

        public Optional<Object> op() {
            return optional;
        }

        private final Path path;
        private final Optional<Object> optional;

        private static final long serialVersionUID = 1L;

    }

    private static void assertWrappedIdentity(ObjectMapper om, Object obj) {
        var map = om.toMap(obj);
        assertEquals(Map.of("value", ObjectMapper.wrapIdentity(obj)), map);
    }

    private static void assertWrappedIdentity(Object obj) {
        assertWrappedIdentity(ObjectMapper.standard().create(), obj);
    }
}
