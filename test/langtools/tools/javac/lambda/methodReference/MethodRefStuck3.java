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

/**
 * @test
 * @bug 8375572
 * @summary stuck method references should consider free vars in the target type
 * @compile MethodRefStuck3.java
 */

class MethodRefStuck3 {
    interface Interface<A> {
        interface Factory<A extends Interface<B>,B> {
            Interface<B> create(B obj);
        }
    }

    record Klass(String value, int otherValue) implements Interface<String> {
        public Klass(String thing) {
            this(thing, -1);
        }
    }

    interface InterfaceB<A extends Interface<B>,B> {}

    record KlassB<A extends Interface<B>,B>(Class<A> cls, Interface.Factory<A,B> factory) implements InterfaceB<A,B> {}

    private interface InterfaceC<A extends Interface<B>,B> {
        InterfaceB<A,B> getInterfaceB();
    }

    private static class KlassC implements InterfaceC<Klass,String> {
        @Override
        public InterfaceB<Klass, String> getInterfaceB() {
            return new KlassB<>(Klass.class, Klass::new);
        }
    }
}
