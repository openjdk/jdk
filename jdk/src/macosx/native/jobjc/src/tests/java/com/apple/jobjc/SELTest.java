/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.apple.jobjc.Coder.PointerCoder;
import com.apple.jobjc.Coder.PrimitivePointerCoder;
import com.apple.jobjc.Invoke.MsgSend;

public class SELTest extends PooledTestCase {

    NativeArgumentBuffer nativeBuffer;
    JObjCRuntime runtime;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        nativeBuffer = UnsafeRuntimeAccess.getNativeBuffer();
        runtime = nativeBuffer.runtime;
    }

    public void testGetBlackColor() throws Throwable {
        final MacOSXFramework appKit = TestUtils.getAppKit();
        final NSClass<? extends ID> clazz = UnsafeRuntimeAccess.getNSClass(appKit, "NSColor");
        final MsgSend sel = UnsafeRuntimeAccess.createMsgSend(clazz, "redColor", PointerCoder.INST);

        sel.init(nativeBuffer, clazz);
        sel.invoke(nativeBuffer);

        final long blackColorPtr = PrimitivePointerCoder.INST.pop(nativeBuffer);
        String dscr = UnsafeRuntimeAccess.getDescriptionForPtr(blackColorPtr);

        System.out.println("0x" + Long.toHexString(blackColorPtr) + ": " + dscr);
        assertEquals("NSCalibratedRGBColorSpace 1 0 0 1", dscr);
    }

    public static void main(final String[] args) {
        junit.textui.TestRunner.run(SELTest.class);
    }
}
