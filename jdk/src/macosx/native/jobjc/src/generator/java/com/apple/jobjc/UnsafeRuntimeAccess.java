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

import com.apple.jobjc.Invoke.FunCall;
import com.apple.jobjc.Invoke.MsgSend;

public class UnsafeRuntimeAccess {
    public static NativeArgumentBuffer getNativeBuffer() {
        return NativeArgumentBuffer.getThreadLocalBuffer(JObjCRuntime.getInstance());
    }

    public static String getClassNameFor(final long obj) {
        return NSClass.getClassNameOfClass(obj);
    }

    public static String getClassNameFor(final NSClass cls) {
        return NSClass.getClassNameOfClass(cls.ptr);
    }

    public static NSClass<?> getSuperClass(final NSClass<? extends ID> clazz) {
        return clazz.getSuperClass();
    }

    public static String getDescriptionForPtr(final long objPtr) {
        return ID.getNativeDescription(objPtr);
    }

    public static MacOSXFramework getFramework(final String[] frameworkLibs) {
        return new MacOSXFramework(JObjCRuntime.getInstance(), frameworkLibs);
    }

    public static FunCall createFunCall(final MacOSXFramework framework, final String fxnName, final Coder returnCoder, final Coder ... argCoders) {
        return new FunCall(framework, fxnName, returnCoder, argCoders);
    }

    public static MsgSend createMsgSend(final NSClass<?> clazz, final String selName, final Coder returnCoder, final Coder ... argCoders) {
        return new MsgSend(clazz.getRuntime(), selName, returnCoder, argCoders);
    }

    public static NSClass<ID> getNSClass(final MacOSXFramework framework, final String name) {
        return new NSClass<ID>(name, framework.getRuntime());
    }

    public static long getObjPtr(final ID obj) {
        return obj.ptr;
    }
}
