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

import java.util.Arrays;
import jdk.nashorn.internal.runtime.ScriptRuntime;

/**
 * Represents a Nashorn callable bound to a receiver and optionally arguments. Note that objects of this class
 * are just the tuples of a callable and a bound this and arguments, without any behavior. All the behavior is
 * defined in the {@code BoundCallableLinker}.
 */
public final class BoundCallable {
    private final Object callable;
    private final Object boundThis;
    private final Object[] boundArgs;

    BoundCallable(final Object callable, final Object boundThis, final Object[] boundArgs) {
        this.callable = callable;
        this.boundThis = boundThis;
        this.boundArgs = isEmptyArray(boundArgs) ? ScriptRuntime.EMPTY_ARRAY : boundArgs.clone();
    }

    private BoundCallable(final BoundCallable original, final Object[] extraBoundArgs) {
        this.callable = original.callable;
        this.boundThis = original.boundThis;
        this.boundArgs = original.concatenateBoundArgs(extraBoundArgs);
    }

    Object getCallable() {
        return callable;
    }

    Object getBoundThis() {
        return boundThis;
    }

    Object[] getBoundArgs() {
        return boundArgs;
    }

    BoundCallable bind(final Object[] extraBoundArgs) {
        if (isEmptyArray(extraBoundArgs)) {
            return this;
        }
        return new BoundCallable(this, extraBoundArgs);
    }

    private Object[] concatenateBoundArgs(final Object[] extraBoundArgs) {
        if (boundArgs.length == 0) {
            return extraBoundArgs.clone();
        }
        final int origBoundArgsLen = boundArgs.length;
        final int extraBoundArgsLen = extraBoundArgs.length;
        final Object[] newBoundArgs = new Object[origBoundArgsLen + extraBoundArgsLen];
        System.arraycopy(boundArgs, 0, newBoundArgs, 0, origBoundArgsLen);
        System.arraycopy(extraBoundArgs, 0, newBoundArgs, origBoundArgsLen, extraBoundArgsLen);
        return newBoundArgs;
    }

    private static boolean isEmptyArray(final Object[] a) {
        return a == null || a.length == 0;
    }

    @Override
    public String toString() {
        final StringBuilder b = new StringBuilder(callable.toString()).append(" on ").append(boundThis);
        if (boundArgs.length != 0) {
            b.append(" with ").append(Arrays.toString(boundArgs));
        }
        return b.toString();
    }
}
