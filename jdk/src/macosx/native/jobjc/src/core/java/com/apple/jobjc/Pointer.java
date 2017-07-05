/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.jobjc;

public class Pointer <T> implements Comparable<Pointer<T>>{
    long ptr;

    protected Pointer(final long ptr) {
        this.ptr = ptr;
        getNativeObjectLifecycleManager().begin(ptr);
    }

    @Override protected final synchronized void finalize() throws Throwable {
        long pptr = ptr;
        ptr = 0;
        if (pptr != 0) getNativeObjectLifecycleManager().end(pptr);
    }

    protected NativeObjectLifecycleManager getNativeObjectLifecycleManager() {
        return NativeObjectLifecycleManager.Nothing.INST;
    }

    @Override public boolean equals(Object o) {
        return o instanceof Pointer && ptr == ((Pointer) o).ptr;
    }

    @Override public int hashCode() { return (int)(ptr^(ptr>>>32)); }

    public int compareTo(Pointer<T> o) {
        if(this==o || ptr==o.ptr) return 0;
        if(ptr < o.ptr) return -1;
        return 1;
    }
}
