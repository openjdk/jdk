/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.runtime.linker;

import java.util.Objects;
import jdk.internal.dynalink.beans.BeansLinker;

/**
 * Represents a Dynalink dynamic method bound to a receiver. Note that objects of this class are just the tuples of
 * a method and a bound this, without any behavior. All the behavior is defined in the {@code BoundDynamicMethodLinker}.
 */
final class BoundDynamicMethod {
    private final Object dynamicMethod;
    private final Object boundThis;

    BoundDynamicMethod(final Object dynamicMethod, final Object boundThis) {
        assert BeansLinker.isDynamicMethod(dynamicMethod);
        this.dynamicMethod = dynamicMethod;
        this.boundThis = boundThis;
    }

    Object getDynamicMethod() {
        return dynamicMethod;
    }

    Object getBoundThis() {
        return boundThis;
    }

    @Override
    public String toString() {
        return dynamicMethod.toString() + " on " + Objects.toString(boundThis);
    }
}
