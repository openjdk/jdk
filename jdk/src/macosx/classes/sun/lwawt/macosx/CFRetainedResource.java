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


package sun.lwawt.macosx;

/**
 * Safely holds and disposes of native AppKit resources, using the
 * correct AppKit threading and Objective-C GC semantics.
 */
public class CFRetainedResource {
    private static native void nativeCFRelease(final long ptr, final boolean disposeOnAppKitThread);

    final boolean disposeOnAppKitThread;
    protected long ptr;

    /**
     * @param ptr CFRetained native object pointer
     * @param disposeOnAppKitThread is the object needs to be CFReleased on the main thread
     */
    protected CFRetainedResource(final long ptr, final boolean disposeOnAppKitThread) {
        this.disposeOnAppKitThread = disposeOnAppKitThread;
        this.ptr = ptr;
    }

    /**
     * CFReleases previous resource and assigns new pre-retained resource.
     * @param ptr CFRetained native object pointer
     */
    protected void setPtr(final long ptr) {
        synchronized (this) {
            if (this.ptr != 0) dispose();
            this.ptr = ptr;
        }
    }

    /**
     * Manually CFReleases the native resource
     */
    protected void dispose() {
        long oldPtr = 0L;
        synchronized (this) {
            if (ptr == 0) return;
            oldPtr = ptr;
            ptr = 0;
        }

        nativeCFRelease(oldPtr, disposeOnAppKitThread); // perform outside of the synchronized block
    }

    @Override
    protected void finalize() throws Throwable {
        dispose();
    }
}
