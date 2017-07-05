/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package rtm;

import com.oracle.java.testlibrary.Utils;
import sun.misc.Unsafe;

/**
 * Current RTM locking implementation force transaction abort
 * before native method call by explicit xabort(0) call.
 */
class XAbortProvoker extends AbortProvoker {
    // Following field have to be static in order to avoid escape analysis.
    @SuppressWarnings("UnsuedDeclaration")
    private static int field = 0;
    private static final Unsafe UNSAFE = Utils.getUnsafe();

    public XAbortProvoker() {
        this(new Object());
    }

    public XAbortProvoker(Object monitor) {
        super(monitor);
    }

    @Override
    public void forceAbort() {
        synchronized(monitor) {
            XAbortProvoker.field = UNSAFE.addressSize();
        }
    }

    @Override
    public String[] getMethodsToCompileNames() {
        return new String[] {
                getMethodWithLockName(),
                Unsafe.class.getName() + "::addressSize"
        };
    }
}
