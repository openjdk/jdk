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
package jdk.jpackage.internal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;


public class CompositeProxyTest {

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
    public void testSmalltalk() {
        var convo = CompositeProxy.create(Smalltalk.class);
        assertEquals("Hello", convo.sayHello());
        assertEquals("Bye", convo.sayBye());
    }

    @Test
    public void testConvo() {
        final var otherThings = "How is your day?";
        var convo = CompositeProxy.create(Convo.class,
                new Smalltalk() {}, new ConvoMixin.Stub(otherThings));
        assertEquals("Hello", convo.sayHello());
        assertEquals("Bye", convo.sayBye());
        assertEquals(otherThings, convo.sayThings());
    }

    @Test
    public void testConvoWithDuke() {
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

    @Test
    public void testConvoWithCustomSayBye() {
        var mixin = new ConvoMixinWithOverrideSayBye.Stub("How is your day?", "See you");

        var convo = CompositeProxy.create(ConvoWithOverrideSayBye.class, new Smalltalk() {}, mixin);

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

    @Test
    public void testConvoWithCustomSayHelloAndSayBye() {
        var mixin = new ConvoMixinWithOverrideSayBye.Stub("How is your day?", "See you");

        var convo = CompositeProxy.create(ConvoWithDefaultSayHelloWithOverrideSayBye.class, new Smalltalk() {}, mixin);

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
    public void testInherited() {
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
    public void testNestedProxy() {
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
    public void testComposite() {
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testBasicObjectMethods(boolean withOverrides) {
        interface A {
            default void foo() {}
        }

        interface B {
            default void bar() {}
        }

        interface C extends A, B {
        }

        final A aImpl;
        final B bImpl;

        if (withOverrides) {
            aImpl = new A() {
                @Override
                public String toString() {
                    return "theA";
                }

                @Override
                public boolean equals(Object other) {
                    return true;
                }

                @Override
                public int hashCode() {
                    return 7;
                }
            };

            bImpl = new B() {
                @Override
                public String toString() {
                    return "theB";
                }
            };
        } else {
            aImpl = new A() {};
            bImpl = new B() {};
        }

        var proxy = CompositeProxy.create(C.class, aImpl, bImpl);
        var proxy2 = CompositeProxy.create(C.class, aImpl, bImpl);

        assertNotEquals(proxy.toString(), proxy2.toString());
        assertNotEquals(proxy.hashCode(), proxy2.hashCode());
        assertFalse(proxy.equals(proxy2));
        assertFalse(proxy2.equals(proxy));
        assertTrue(proxy.equals(proxy));
        assertTrue(proxy2.equals(proxy2));
    }

    @Test
    public void testJavadocExample() {
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

        Sloop sloop = CompositeProxy.create(Sloop.class, new Sailboat() {}, withMain, withJib);

        Catboat catboat = CompositeProxy.create(Catboat.class, new Sailboat() {}, withMain);

        sloop.trimSails();
        catboat.trimSails();
    }
}
