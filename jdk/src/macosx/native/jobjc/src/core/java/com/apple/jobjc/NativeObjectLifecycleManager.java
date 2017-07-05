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

public abstract class NativeObjectLifecycleManager {
    private static native void retainNativeObject(final long ptr);
    private static native void releaseNativeObject(final long ptr);
    private static native void freeNativeObject(final long ptr);

    abstract void begin(final long ptr);
    abstract void end(final long ptr);
    boolean shouldPreRetain() { return false; }

    public static class CFRetainRelease extends NativeObjectLifecycleManager {
        public static final NativeObjectLifecycleManager INST = new CFRetainRelease();
        @Override void begin(final long ptr) { retainNativeObject(ptr); }
        @Override void end(final long ptr) { releaseNativeObject(ptr); }
        @Override boolean shouldPreRetain() { return true; }
    }

    public static class Free extends NativeObjectLifecycleManager {
        public static final NativeObjectLifecycleManager INST = new Free();
        @Override void begin(final long ptr) { }
        @Override void end(final long ptr) { freeNativeObject(ptr); }
    }

    public static class Nothing extends NativeObjectLifecycleManager {
        public static final NativeObjectLifecycleManager INST = new Nothing();
        @Override void begin(final long ptr) { }
        @Override void end(final long ptr) { }
    }
}
