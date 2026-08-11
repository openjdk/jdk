/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.util;

import static jdk.jpackage.internal.util.PathUtils.mapNullablePath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.CompositeProxy.InvokeTunnel;
import jdk.jpackage.test.JUnitUtils.StringArrayConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;


class CompositeProxyTest {

    static interface Smalltalk {

        default String sayHello() {
            return "Hello";
        }

        default String sayBye() {
            return "Bye";
        }
    }

    static interface ConvoMixin {

        String sayThings();

        record Stub(String sayThings) implements ConvoMixin {
        }
    }

    static interface Convo extends Smalltalk, ConvoMixin {
    }

    static interface ConvoMixinWithOverrideSayBye {

        String sayThings();

        String sayBye();

        record Stub(String sayThings, String sayBye) implements ConvoMixinWithOverrideSayBye {
        }
    }

    static interface ConvoWithOverrideSayBye extends Smalltalk, ConvoMixinWithOverrideSayBye {
        @Override
        String sayBye();
    }

    static interface ConvoWithDefaultSayHelloWithOverrideSayBye extends Smalltalk, ConvoMixinWithOverrideSayBye {
        @Override
        String sayBye();

        @Override
        default String sayHello() {
            return "Ciao";
        }

        static String saySomething() {
            return "blah";
        }
    }

    @Test
    void testSmalltalk() {
        var convo = CompositeProxy.create(Smalltalk.class);
        assertEquals("Hello", convo.sayHello());
        assertEquals("Bye", convo.sayBye());
    }

    @Test
    void testConvo() {
        final var otherThings = "How is your day?";
        var convo = CompositeProxy.create(Convo.class,
                new ConvoMixin.Stub(otherThings));
        assertEquals("Hello", convo.sayHello());
        assertEquals("Bye", convo.sayBye());
        assertEquals(otherThings, convo.sayThings());
    }

    @Test
    void testConvoWithDuke() {
        final var otherThings = "How is your day?";
        var convo = CompositeProxy.create(Convo.class, new Smalltalk() {
            @Override
            public String sayHello() {
                return "Hello, Duke";
            }
        }, new ConvoMixin.Stub(otherThings));
        assertEquals("Hello, Duke", convo.sayHello());
        assertEquals("Bye", convo.sayBye());
        assertEquals(otherThings, convo.sayThings());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testConvoWithCustomSayBye(boolean allowUnreferencedSlices) {
        var mixin = new ConvoMixinWithOverrideSayBye.Stub("How is your day?", "See you");

        var smalltalk = new Smalltalk() {};

        var proxyBuilder = CompositeProxy.build().allowUnreferencedSlices(allowUnreferencedSlices);

        if (!allowUnreferencedSlices) {
            var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
                proxyBuilder.create(ConvoWithOverrideSayBye.class, smalltalk, mixin);
            });

            assertEquals(String.format("Unreferenced slices: %s", List.of(smalltalk)), ex.getMessage());
        } else {
            var convo = proxyBuilder.create(ConvoWithOverrideSayBye.class, smalltalk, mixin);

            var expectedConvo = new ConvoWithOverrideSayBye() {
                @Override
                public String sayBye() {
                    return mixin.sayBye;
                }

                @Override
                public String sayThings() {
                    return mixin.sayThings;
                }
            };

            assertEquals(expectedConvo.sayHello(), convo.sayHello());
            assertEquals(expectedConvo.sayBye(), convo.sayBye());
            assertEquals(expectedConvo.sayThings(), convo.sayThings());
        }
    }

    @Test
    void testConvoWithCustomSayHelloAndSayBye() {
        var mixin = new ConvoMixinWithOverrideSayBye.Stub("How is your day?", "See you");

        var convo = CompositeProxy.create(ConvoWithDefaultSayHelloWithOverrideSayBye.class, mixin);

        var expectedConvo = new ConvoWithDefaultSayHelloWithOverrideSayBye() {
            @Override
            public String sayBye() {
                return mixin.sayBye;
            }

            @Override
            public String sayThings() {
                return mixin.sayThings;
            }
        };

        assertEquals("Ciao", expectedConvo.sayHello());
        assertEquals(expectedConvo.sayHello(), convo.sayHello());
        assertEquals(expectedConvo.sayBye(), convo.sayBye());
        assertEquals(expectedConvo.sayThings(), convo.sayThings());
    }

    @Test
    void testInherited() {
        interface Base {
            String doSome();
        }

        interface Next extends Base {
            String doNext();
        }

        interface Last extends Next {
        }

        var last = CompositeProxy.create(Last.class, new Next() {
            @Override
            public String doNext() {
                return "next";
            }

            @Override
            public String doSome() {
                return "some";
            }
        });

        assertEquals("next", last.doNext());
        assertEquals("some", last.doSome());
    }

    @Test
    void testNestedProxy() {
        interface AddM {
            String m();
        }

        interface AddN {
            String n();
        }

        interface A extends AddM {
        }

        interface B extends AddN  {
        }

        interface C extends A, B {
        }

        var proxyA = CompositeProxy.create(A.class, new AddM() {
            @Override
            public String m() {
                return "hello";
            }
        });
        var proxyB = CompositeProxy.create(B.class, new AddN() {
            @Override
            public String n() {
                return "bye";
            }

        });
        var proxyC = CompositeProxy.create(C.class, proxyA, proxyB);

        assertEquals("hello", proxyC.m());
        assertEquals("bye", proxyC.n());
    }

    @Test
    void testComposite() {
        interface A {
            String sayHello();
            String sayBye();
            default String talk() {
                return String.join(",", sayHello(), sayBye());
            }
        }

        interface B extends A {
            @Override
            default String sayHello() {
                return "ciao";
            }
        }

        var proxy = CompositeProxy.create(B.class, new A() {
            @Override
            public String sayHello() {
                return "hello";
            }

            @Override
            public String sayBye() {
                return "bye";
            }
        });

        assertEquals("ciao,bye", proxy.talk());
    }

    @Test
    void testBasicObjectMethods() {
        interface Foo {
        }

        var proxy = CompositeProxy.create(Foo.class);
        var proxy2 = CompositeProxy.create(Foo.class);

        assertNotEquals(proxy.toString(), proxy2.toString());
        assertNotEquals(proxy.hashCode(), proxy2.hashCode());
        assertFalse(proxy.equals(proxy2));
        assertFalse(proxy2.equals(proxy));
        assertTrue(proxy.equals(proxy));
        assertTrue(proxy2.equals(proxy2));
    }

    @Test
    void testAutoMethodConflictResolver() {

        interface A {
            String getString();
        }

        interface B {
            String getString();
        }

        interface AB extends A, B {
        }

        var foo = new Object() {
            public String getString() {
                return "foo";
            }
        };

        var proxy = CompositeProxy.create(AB.class, foo);
        assertEquals("foo", proxy.getString());
    }

    @Test
    void testAutoMethodConflictResolver2() {

        interface A {
            String getString();
        }

        interface B {
            String getString();
        }

        interface AB extends A, B {
            String getString();
        }

        var foo = new Object() {
            public String getString() {
                return "foo";
            }
        };

        var proxy = CompositeProxy.create(AB.class, foo);
        assertEquals("foo", proxy.getString());
    }

    @Test
    void testUnreferencedSlices() {

        interface A {
            String getString();
        }

        interface B {
            String getString();
        }

        interface AB extends A, B {
            default String getString() {
                throw new AssertionError();
            }
        }

        var foo = new Object() {
            public String getString() {
                throw new AssertionError();
            }
        };

        var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            CompositeProxy.create(AB.class, foo);
        });

        assertEquals(String.format("Unreferenced slices: %s", List.of(foo)), ex.getMessage());
    }

    @Test
    void testAutoMethodConflictResolver4() {

        interface A {
            String getString();
        }

        interface B {
            String getString();
        }

        interface AB extends A, B {
            default String getString() {
                return "AB";
            }
        }

        var proxy = CompositeProxy.create(AB.class);
        assertEquals("AB", proxy.getString());
    }

    @Test
    void testAutoMethodConflictResolver4_1() {

        interface A {
            String foo();
            String bar();
        }

        interface B {
            String foo();
            String bar();
        }

        interface AB extends A, B {
            default String foo() {
                return "AB.foo";
            }
        }

        var proxy = CompositeProxy.create(AB.class, new AB() {
            @Override
            public String bar() {
                return "Obj.bar";
            }
        });
        assertEquals("AB.foo", proxy.foo());
        assertEquals("Obj.bar", proxy.bar());
    }

    @Test
    void testAutoMethodConflictResolver5() {

        interface A {
            default String getString() {
                throw new AssertionError();
            }
        }

        interface B {
            String getString();
        }

        interface AB extends A, B {
            String getString();
        }

        var foo = new Object() {
            public String getString() {
                return "foo";
            }
        };

        var proxy = CompositeProxy.create(AB.class, foo);
        assertEquals("foo", proxy.getString());
    }

    @Test
    void testAutoMethodConflictResolver6() {

        interface A {
            default String getString() {
                return "A";
            }
        }

        interface B {
            String getString();
        }

        interface AB extends A, B {
            default String getString() {
                return A.super.getString() + "!";
            }
        }

        var proxy = CompositeProxy.create(AB.class);
        assertEquals("A!", proxy.getString());
    }

    @Test
    void testAutoMethodConflictResolver7() {

        interface A {
            String getString();
        }

        interface B extends A {
            default String getString() {
                return "B";
            }
        }

        interface AB extends A, B {
            default String getString() {
                return B.super.getString() + "!";
            }
        }

        var proxy = CompositeProxy.create(AB.class);
        assertEquals("B!", proxy.getString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAutoMethodConflictResolver8(boolean override) {

        interface A {
            String getString();
        }

        interface B extends A {
            default String getString() {
                return "B";
            }
        }

        interface AB extends A, B {
        }

        if (override) {
            var foo = new Object() {
                public String getString() {
                    return "foo";
                }
            };

            var proxy = CompositeProxy.build().methodConflictResolver((_, _, _, _) -> {
                return true;
            }).create(AB.class, foo);
            assertEquals("foo", proxy.getString());
        } else {
            var proxy = CompositeProxy.create(AB.class);
            assertEquals("B", proxy.getString());
        }
    }

    @Test
    void testAutoMethodConflictResolver9() {

        interface A {
            String getString();
        }

        interface B extends A {
            default String getString() {
                throw new AssertionError();
            }
        }

        var foo = new Object() {
            public String getString() {
                return "foo";
            }
        };

        interface AB extends A, B {
            String getString();
        }

        var ab = CompositeProxy.create(AB.class, foo);
        assertEquals("foo", ab.getString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAutoMethodConflictResolver10(boolean override) {

        interface A {
            String getString();
        }

        interface B extends A {
            default String getString() {
                return "B";
            }
        }

        interface AB extends A, B {
            String getString();
        }

        if (override) {
            var foo = new B() {
                @Override
                public String getString() {
                    return B.super.getString() + "!";
                }
            };

            var proxy = CompositeProxy.create(AB.class, foo);
            assertEquals("B!", proxy.getString());
        } else {
            var proxy = CompositeProxy.create(AB.class, new B() {});
            assertEquals("B", proxy.getString());
        }
    }

    @Test
    void testAutoMethodConflictResolver11() {

        interface A {
            String getString();
        }

        class Foo implements A {
            @Override
            public String getString() {
                throw new AssertionError();
            }
        }

        class Bar extends Foo {
            @Override
            public String getString() {
                throw new AssertionError();
            }
        }

        class Buz extends Bar {
            @Override
            public String getString() {
                return "buz";
            }
        }

        var proxy = CompositeProxy.create(A.class, new Buz());
        assertEquals("buz", proxy.getString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAutoMethodConflictResolver12(boolean override) {

        interface A {
            String getString();
        }

        interface B {
            default String getString() {
                return "foo";
            }
        }

        if (override) {
            class BImpl implements B {
                @Override
                public String getString() {
                    return "bar";
                }
            }

            var proxy = CompositeProxy.create(A.class, new BImpl() {});
            assertEquals("bar", proxy.getString());
        } else {
            class BImpl implements B {
            }

            var proxy = CompositeProxy.create(A.class, new BImpl() {});
            assertEquals("foo", proxy.getString());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAutoMethodConflictResolver13(boolean override) {

        interface A {
            String getString();
        }

        interface Foo {
            default String getString() {
                return "foo";
            }
        }

        if (override) {
            class B {
                public String getString() {
                    return "B";
                }
            }

            class C extends B implements Foo {
            }

            for (var slice : List.of(new C(), new C() {})) {
                var proxy = CompositeProxy.create(A.class, slice);
                assertEquals("B", proxy.getString());
            }

            var proxy = CompositeProxy.create(A.class, new C() {
                @Override
                public String getString() {
                    return "C";
                }
            });
            assertEquals("C", proxy.getString());
        } else {
            class B {
            }

            class C extends B implements Foo {
            }

            for (var slice : List.of(new C(), new C() {})) {
                var proxy = CompositeProxy.create(A.class, slice);
                assertEquals("foo", proxy.getString());
            }
        }
    }

    @Test
    void testAutoMethodConflictResolver14() {

        interface Launcher {

            String name();

            Map<String, String> extraAppImageFileData();

            record Stub(String name, Map<String, String> extraAppImageFileData) implements Launcher {}
        }

        interface WinLauncherMixin {

            boolean shortcut();

            record Stub(boolean shortcut) implements WinLauncherMixin {}
        }

        interface WinLauncher extends Launcher, WinLauncherMixin {

            default Map<String, String> extraAppImageFileData() {
                return Map.of("shortcut", Boolean.toString(shortcut()));
            }
        }

        var proxy = CompositeProxy.create(WinLauncher.class, new Launcher.Stub("foo", Map.of()), new WinLauncherMixin.Stub(true));

        assertEquals("foo", proxy.name());
        assertEquals(Map.of("shortcut", "true"), proxy.extraAppImageFileData());
    }

    @ParameterizedTest
    @CsvSource({
        "a,b",
        "b,a",
    })
    void testObjectConflictResolver(String fooResolve, String barResolve) {

        interface I {
            String foo();
            String bar();
        }

        var a = new I() {
            @Override
            public String foo() {
                return "a-foo";
            }

            @Override
            public String bar() {
                return "a-bar";
            }
        };

        var b = new Object() {
            public String foo() {
                return "b-foo";
            }

            public String bar() {
                return "b-bar";
            }
        };

        Function<String, Object> resolver = tag -> {
            return switch (tag) {
                case "a" -> a;
                case "b" -> b;
                default -> {
                    throw new AssertionError();
                }
            };
        };

        var proxy = CompositeProxy.build().objectConflictResolver((_, _, method, _) -> {
            return switch (method.getName()) {
                case "foo" -> resolver.apply(fooResolve);
                case "bar" -> resolver.apply(barResolve);
                default -> {
                    throw new AssertionError();
                }
            };
        }).create(I.class, a, b);

        assertEquals(fooResolve + "-foo", proxy.foo());
        assertEquals(barResolve + "-bar", proxy.bar());
    }

    @Test
    void testObjectConflictResolverInvalid() {

        interface I {
            String foo();
        }

        var a = new I() {
            @Override
            public String foo() {
                throw new AssertionError();
            }
        };

        var b = new Object() {
            public String foo() {
                throw new AssertionError();
            }
        };

        assertThrowsExactly(UnsupportedOperationException.class, () -> {
            CompositeProxy.build().objectConflictResolver((_, _, _, _) -> {
                return new Object();
            }).create(I.class, a, b);
        });
    }

    @ParameterizedTest
    @ValueSource( strings = {
        "no-foo",
        "private-foo",
        "protected-foo",
        "package-foo",
        "static-foo",
        "static-foo,private-foo,no-foo",
    })
    void testMissingImplementer(@ConvertWith(StringArrayConverter.class) String[] slicesSpec) throws NoSuchMethodException, SecurityException {

        interface A {
            void foo();
        }

        var slices = Stream.of(slicesSpec).map(slice -> {
            return switch (slice) {
                case "no-foo" -> new Object();
                case "private-foo" -> new Object() {
                    private void foo() {
                        throw new AssertionError();
                    }
                };
                case "protected-foo" -> new Object() {
                    protected void foo() {
                        throw new AssertionError();
                    }
                };
                case "package-foo" -> new Object() {
                    void foo() {
                        throw new AssertionError();
                    }
                };
                case "static-foo" -> new Object() {
                    public static void foo() {
                        throw new AssertionError();
                    }
                };
                default -> { throw new AssertionError(); }
            };
        }).toList();

        var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            CompositeProxy.create(A.class, slices.toArray());
        });

        assertEquals(String.format("None of the slices can handle %s", A.class.getMethod("foo")), ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testUnusedSlice(boolean all) {

        interface A {
            default void foo() {
                throw new AssertionError();
            }
        }

        A a = new A() {};
        var obj = new Object();

        if (all) {
            var messages = Set.of(
                    String.format("Unreferenced slices: %s", List.of(a, obj)),
                    String.format("Unreferenced slices: %s", List.of(obj, a))
            );

            var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
                CompositeProxy.create(A.class, a, obj);
            });

            assertTrue(messages.contains(ex.getMessage()));
        } else {
            interface B extends A {
                void foo();
            }

            var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
                CompositeProxy.create(B.class, a, obj);
            });

            assertEquals(String.format("Unreferenced slices: %s", List.of(obj)), ex.getMessage());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "'a,b,a',false",
        "'a,b,a',true",
        "'a,b',true",
        "'b,a',true",
        "'a,b',false",
        "'b,a',false",
    })
    void testAmbiguousImplementers(
            @ConvertWith(StringArrayConverter.class) String[] slicesSpec,
            boolean withObjectConflictResolver) throws NoSuchMethodException, SecurityException {

        interface A {
            String foo();
            String bar();
        }

        var a = new Object() {
            public String foo() {
                return "a-foo";
            }
            public String bar() {
                throw new AssertionError();
            }
        };

        var b = new Object() {
            public String bar() {
                return "b-bar";
            }
        };

        var ambiguousMethod = A.class.getMethod("bar");

        var slices = Stream.of(slicesSpec).map(slice -> {
            return switch (slice) {
                case "a" -> a;
                case "b" -> b;
                default -> { throw new AssertionError(); }
            };
        }).toArray();

        if (withObjectConflictResolver) {
            var proxy = CompositeProxy.build().objectConflictResolver((_, _, _, _) -> {
                return b;
            }).create(A.class, slices);

            assertEquals("a-foo", proxy.foo());
            assertEquals("b-bar", proxy.bar());
        } else {
            var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
                CompositeProxy.create(A.class, slices);
            });

            var messages = Set.of(
                    String.format("Ambiguous choice between %s for %s", List.of(a, b), ambiguousMethod),
                    String.format("Ambiguous choice between %s for %s", List.of(b, a), ambiguousMethod)
            );

            assertTrue(messages.contains(ex.getMessage()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testDifferentReturnTypes(boolean compatible) {

        interface A {
            Number foo();
        }

        Object obj;
        if (compatible) {
            obj = new Object() {
                public Integer foo() {
                    return 123;
                }
            };
        } else {
            obj = new Object() {
                public String foo() {
                    return "123";
                }
            };
        }

        var proxy = CompositeProxy.create(A.class, obj);

        if (compatible) {
            assertEquals(123, proxy.foo());
        } else {
            assertThrows(ClassCastException.class, proxy::foo);
        }
    }

    @Test
    void testCovariantReturnType() {

        interface A {
            Number foo();
        }

        interface Mixin {
            String bar();
        }

        interface AWithMixin extends A, Mixin {
            Integer foo();
        }

        var proxy = CompositeProxy.create(AWithMixin.class, new A() {
            @Override
            public Number foo() {
                return 123;
            }
        }, new Mixin() {
            @Override
            public String bar() {
                return "bar";
            }
        });

        assertEquals(123, proxy.foo());
        assertEquals("bar", proxy.bar());
    }

    @Test
    void testNotInterface() {
        var ex = assertThrowsExactly(IllegalArgumentException.class, () -> {
            CompositeProxy.create(Integer.class);
        });

        assertEquals(String.format("Type %s must be an interface", Integer.class.getName()), ex.getMessage());
    }

    @Test
    void testExcessiveInterfaces() {

        interface Launcher {
            String name();

            default String executableResource() {
                return "jpackageapplauncher";
            }

            record Stub(String name) implements Launcher {
            }
        }

        interface WinLauncherMixin {
            String version();

            record Stub(String version) implements WinLauncherMixin {
            }
        }

        interface WinLauncher extends Launcher, WinLauncherMixin {

            default String executableResource() {
                return "jpackageapplauncher.exe";
            }
        }

        var winLauncher = CompositeProxy.create(WinLauncher.class, new Launcher.Stub("foo"), new WinLauncherMixin.Stub("1.0"));

        var winLauncher2 = CompositeProxy.create(WinLauncher.class, new Launcher.Stub("bar"), winLauncher);

        assertEquals("foo", winLauncher.name());
        assertEquals("1.0", winLauncher.version());
        assertEquals("jpackageapplauncher.exe", winLauncher.executableResource());

        assertEquals("bar", winLauncher2.name());
        assertEquals("1.0", winLauncher2.version());
        assertEquals("jpackageapplauncher.exe", winLauncher2.executableResource());
    }

    @Test
    void testInvokeTunnel() {

        interface A {
            default String foo() {
                return "foo";
            }
            String bar();
        }

        var obj = new Object() {
            public String bar() {
                return "bar";
            }
        };

        Slot<Boolean> invokeCalled = Slot.createEmpty();
        invokeCalled.set(false);

        Slot<Boolean> invokeDefaultCalled = Slot.createEmpty();
        invokeDefaultCalled.set(false);

        var proxy = CompositeProxy.build().invokeTunnel(new InvokeTunnel() {

            @Override
            public Object invoke(Object obj, Method method, Object[] args) throws Throwable {
                invokeCalled.set(true);
                return method.invoke(obj, args);
            }

            @Override
            public Object invokeDefault(Object proxy, Method method, Object[] args) throws Throwable {
                invokeDefaultCalled.set(true);
                return InvocationHandler.invokeDefault(proxy, method, args);
            }

        }).create(A.class, obj);

        assertFalse(invokeCalled.get());
        assertFalse(invokeDefaultCalled.get());
        assertEquals("foo", proxy.foo());
        assertFalse(invokeCalled.get());
        assertTrue(invokeDefaultCalled.get());

        invokeDefaultCalled.set(false);
        assertEquals("bar", proxy.bar());
        assertTrue(invokeCalled.get());
        assertFalse(invokeDefaultCalled.get());
    }

    @Test
    void testDefaultOverride() {

        interface AppImageLayout {

            Path runtimeDirectory();

            Path rootDirectory();

            default boolean isResolved() {
                return !rootDirectory().equals(Path.of(""));
            }

            default AppImageLayout unresolve() {
                if (isResolved()) {
                    final var root = rootDirectory();
                    return map(root::relativize);
                } else {
                    return this;
                }
            }

            AppImageLayout map(UnaryOperator<Path> mapper);

            record Stub(Path rootDirectory, Path runtimeDirectory) implements AppImageLayout {

                public Stub {
                    Objects.requireNonNull(rootDirectory);
                }

                public Stub(Path runtimeDirectory) {
                    this(Path.of(""), runtimeDirectory);
                }

                @Override
                public AppImageLayout map(UnaryOperator<Path> mapper) {
                    return new Stub(mapNullablePath(mapper, rootDirectory), mapNullablePath(mapper, runtimeDirectory));
                }
            }
        }

        interface ApplicationLayoutMixin {

            Path appDirectory();

            record Stub(Path appDirectory) implements ApplicationLayoutMixin {
            }
        }

        interface ApplicationLayout extends AppImageLayout, ApplicationLayoutMixin {

            @Override
            default ApplicationLayout unresolve() {
                return (ApplicationLayout)AppImageLayout.super.unresolve();
            }

            @Override
            default ApplicationLayout map(UnaryOperator<Path> mapper) {
                return CompositeProxy.create(ApplicationLayout.class,
                        new AppImageLayout.Stub(rootDirectory(), runtimeDirectory()).map(mapper),
                        new ApplicationLayoutMixin.Stub(mapper.apply(appDirectory())));
            }
        }

        var proxy = CompositeProxy.create(ApplicationLayout.class,
                new AppImageLayout.Stub(Path.of(""), Path.of("runtime")),
                new ApplicationLayoutMixin.Stub(Path.of("app")));

        assertSame(proxy, proxy.unresolve());

        var mapped = proxy.map(Path.of("a")::resolve);
        assertEquals(Path.of("a"), mapped.rootDirectory());
        assertEquals(Path.of("a/runtime"), mapped.runtimeDirectory());
        assertEquals(Path.of("a/app"), mapped.appDirectory());
    }

    @Test
    void testJavadocExample() {
        interface Sailboat {
            default void trimSails() {}
        }

        interface WithMain {
            void trimMain();
        }

        interface WithJib {
            void trimJib();
        }

        interface Sloop extends Sailboat, WithMain, WithJib {
            @Override
            public default void trimSails() {
                System.out.println("On the sloop:");
                trimMain();
                trimJib();
            }
        }

        interface Catboat extends Sailboat, WithMain {
            @Override
            public default void trimSails() {
                System.out.println("On the catboat:");
                trimMain();
            }
        }

        final var withMain = new WithMain() {
            @Override
            public void trimMain() {
                System.out.println("  trim the main");
            }
        };

        final var withJib = new WithJib() {
            @Override
            public void trimJib() {
                System.out.println("  trim the jib");
            }
        };

        Sloop sloop = CompositeProxy.create(Sloop.class, withMain, withJib);

        Catboat catboat = CompositeProxy.create(Catboat.class, withMain);

        sloop.trimSails();
        catboat.trimSails();
    }
}
