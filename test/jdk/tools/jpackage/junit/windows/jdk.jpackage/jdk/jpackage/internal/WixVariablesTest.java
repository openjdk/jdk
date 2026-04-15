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

package jdk.jpackage.internal;

import static jdk.jpackage.internal.WixToolset.WixToolsetType.Wix4;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import jdk.jpackage.internal.WixToolset.WixToolsetType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class WixVariablesTest {

    @Test
    void test_define() {
        assertEquals(List.of("-d", "foo=yes"), new WixVariables().define("foo").toWixCommandLine(Wix4));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void test_define_null(boolean immutable) {
        assertThrows(NullPointerException.class, vars -> {
            vars.define(null);
        }, create(immutable));
    }

    @Test
    void test_put() {
        assertEquals(List.of("-d", "foo=bar"), new WixVariables().put("foo", "bar").toWixCommandLine(Wix4));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void test_put_null(boolean immutable) {
        assertThrows(NullPointerException.class, vars -> {
            vars.put("foo", null);
        }, create(immutable));

        assertThrows(NullPointerException.class, vars -> {
            vars.put(null, "foo");
        }, create(immutable));
    }

    @Test
    void test_putAll() {
        assertEquals(List.of("-d", "foo=bar"), new WixVariables().putAll(Map.of("foo", "bar")).toWixCommandLine(Wix4));
        assertEquals(List.of("-d", "foo=yes"), new WixVariables().putAll(new WixVariables().define("foo")).toWixCommandLine(Wix4));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void test_putAll_null(boolean immutable) {

        assertThrows(NullPointerException.class, vars -> {
            vars.putAll((Map<String, String>)null);
        }, create(immutable));

        assertThrows(NullPointerException.class, vars -> {
            vars.putAll((WixVariables)null);
        }, create(immutable));

        final var expectedExceptionType = immutable ? IllegalStateException.class : NullPointerException.class;

        var other = new HashMap<String, String>();

        other.clear();
        other.put("foo", null);
        assertThrows(expectedExceptionType, vars -> {
            vars.putAll(other);
        }, create(immutable));

        other.clear();
        other.put(null, "foo");
        assertThrows(expectedExceptionType, vars -> {
            vars.putAll(other);
        }, create(immutable));
    }

    @Test
    void testImmutable() {
        var vars = new WixVariables().define("foo").createdImmutableCopy();

        assertThrows(IllegalStateException.class, _ -> {
            vars.putAll(Map.of());
        }, vars);

        assertThrows(IllegalStateException.class, _ -> {
            vars.putAll(new WixVariables());
        }, vars);

        assertThrows(IllegalStateException.class, _ -> {
            vars.define("foo");
        }, vars);

        assertThrows(IllegalStateException.class, _ -> {
            vars.put("foo", "bar");
        }, vars);

        for (var allowOverrides : List.of(true, false)) {
            assertThrows(IllegalStateException.class, _ -> {
                vars.allowOverrides(allowOverrides);
            }, vars);
        }
    }

    @Test
    void testDefaultOverridable() {
        var vars = new WixVariables().define("foo");

        assertThrows(IllegalStateException.class, _ -> {
            vars.define("foo");
        }, vars);

        assertThrows(IllegalStateException.class, _ -> {
            vars.put("foo", "no");
        }, vars);

        assertThrows(IllegalStateException.class, _ -> {
            vars.put("foo", "yes");
        }, vars);

        assertThrows(IllegalStateException.class, _ -> {
            vars.putAll(Map.of("foo", "A", "bar", "B"));
        }, vars);

        assertThrows(IllegalStateException.class, _ -> {
            vars.putAll(new WixVariables().putAll(Map.of("foo", "A", "bar", "B")));
        }, vars);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testOverridable_define(boolean overridable) {
        var vars = new WixVariables().allowOverrides(overridable).define("foo");

        if (overridable) {
            vars.define("foo");
        } else {
            assertThrows(IllegalStateException.class, _ -> {
                vars.define("foo");
            }, vars);
            vars.allowOverrides(true);
            vars.define("foo");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testOverridable_put(boolean overridable) {
        var vars = new WixVariables().allowOverrides(overridable).define("foo");

        if (overridable) {
            vars.put("foo", "bar");
            assertEquals(List.of("-d", "foo=bar"), vars.toWixCommandLine(Wix4));
        } else {
            assertThrows(IllegalStateException.class, _ -> {
                vars.put("foo", "bar");
            }, vars);
            vars.allowOverrides(true);
            vars.put("foo", "bar");
            assertEquals(List.of("-d", "foo=bar"), vars.toWixCommandLine(Wix4));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testOverridable_putAll(boolean overridable) {
        var vars = new WixVariables().allowOverrides(overridable).define("foo");

        var other = Map.of("foo", "A", "bar", "B");

        if (overridable) {
            vars.putAll(other);
            assertEquals(List.of("-d", "bar=B", "-d", "foo=A"), vars.toWixCommandLine(Wix4));
        } else {
            assertThrows(IllegalStateException.class, _ -> {
                vars.putAll(other);
            }, vars);
            vars.allowOverrides(true);
            vars.putAll(other);
            assertEquals(List.of("-d", "bar=B", "-d", "foo=A"), vars.toWixCommandLine(Wix4));
        }
    }

    @Test
    void test_createdImmutableCopy() {
        var vars = new WixVariables().define("foo");

        var copy = vars.createdImmutableCopy();

        assertNotSame(vars, copy);

        assertSame(copy, copy.createdImmutableCopy());

        assertEquals(List.of("-d", "foo=yes"), copy.toWixCommandLine(Wix4));

        vars.allowOverrides(true).put("foo", "bar");
        assertEquals(List.of("-d", "foo=bar"), vars.toWixCommandLine(Wix4));
        assertEquals(List.of("-d", "foo=yes"), copy.toWixCommandLine(Wix4));
    }

    @ParameterizedTest
    @EnumSource(WixToolsetType.class)
    void test_toWixCommandLine(WixToolsetType wixType) {
        var args = new WixVariables().define("foo").put("bar", "a").toWixCommandLine(wixType);

        var expectedArgs = switch (wixType) {
            case Wix3 -> {
                yield List.of("-dbar=a", "-dfoo=yes");
            }
            case Wix4 -> {
                yield List.of("-d", "bar=a", "-d", "foo=yes");
            }
        };

        assertEquals(expectedArgs, args);
    }

    private static WixVariables create(boolean immutable) {
        var vars = new WixVariables();
        if (immutable) {
            return vars.createdImmutableCopy();
        } else {
            return vars;
        }
    }

    private static void assertThrows(
            Class<? extends RuntimeException> expectedExceptionType, Consumer<WixVariables> mutator, WixVariables vars) {

        var content = vars.toWixCommandLine(Wix4);

        assertThrowsExactly(expectedExceptionType, () -> {
            mutator.accept(vars);
        });

        assertEquals(content, vars.toWixCommandLine(Wix4));
    }
}
