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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;


public class IdentityWrapperTest {

    @Test
    public void test_null() {
        assertThrows(NullPointerException.class, () -> identityOf(null));
    }

    @Test
    public void test_equals() {
        var obj = new TestRecord(10);
        assertEquals(identityOf(obj), identityOf(obj));
    }

    @Test
    public void test_not_equals() {
        var identity = identityOf(new TestRecord(10));
        var identity2 = identityOf(new TestRecord(10));
        assertNotEquals(identity, identity2);
        assertEquals(identity.value(), identity2.value());
    }

    @Test
    public void test_Foo() {
        var foo = new Foo(10);
        assertFalse(foo.accessed());

        foo.hashCode();
        assertTrue(foo.accessed());
        assertTrue(foo.hashCodeCalled());
        assertFalse(foo.equalsCalled());

        foo = new Foo(1);
        foo.equals(null);
        assertTrue(foo.accessed());
        assertFalse(foo.hashCodeCalled());
        assertTrue(foo.equalsCalled());
    }

    @Test
    public void test_wrappedValue_not_accessed() {
        var identity = identityOf(new Foo(10));
        var identity2 = identityOf(new Foo(10));
        assertNotEquals(identity, identity2);

        assertFalse(identity.value().accessed());
        assertFalse(identity2.value().accessed());

        assertEquals(identity.value(), identity2.value());
        assertEquals(identity2.value(), identity.value());

        assertTrue(identity.value().accessed());
        assertTrue(identity2.value().accessed());
    }

    @Test
    public void test_wrappedValue_not_accessed_in_set() {
        var identitySet = Set.of(identityOf(new Foo(10)), identityOf(new Foo(10)), identityOf(new Foo(10)));
        assertEquals(3, identitySet.size());

        var valueSet = identitySet.stream().peek(identity -> {
            assertFalse(identity.value().accessed());
        }).map(IdentityWrapper::value).collect(Collectors.toSet());

        assertEquals(1, valueSet.size());
    }

    private static <T> IdentityWrapper<T> identityOf(T obj) {
        return new IdentityWrapper<>(obj);
    }

    private record TestRecord(int v) {}

    private final static class Foo {

        Foo(int v) {
            this.v = v;
        }

        @Override
        public int hashCode() {
            try {
                return Objects.hash(v);
            } finally {
                hashCodeCalled = true;
            }
        }

        @Override
        public boolean equals(Object obj) {
            try {
                if (this == obj)
                    return true;
                if (obj == null)
                    return false;
                if (getClass() != obj.getClass())
                    return false;
                Foo other = (Foo) obj;
                return v == other.v;
            } finally {
                equalsCalled = true;
            }
        }

        boolean equalsCalled() {
            return equalsCalled;
        }

        boolean hashCodeCalled() {
            return hashCodeCalled;
        }

        boolean accessed() {
            return equalsCalled() || hashCodeCalled();
        }

        private final int v;
        private boolean equalsCalled;
        private boolean hashCodeCalled;
    }
}
