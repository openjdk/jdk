/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
package java.lang;

import jdk.internal.vm.ContinuationScope;

import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;

/**
 * StackFrameInfo is an implementation of StackFrame that contains the
 * class and method information.  This is used by stack walker configured
 * without StackWalker.Option#DROP_METHOD_INFO.
 */
class StackFrameInfo extends ClassFrameInfo {
    private String name;
    private Object type;          // String or MethodType
    private int bci;              // set by VM to >= 0
    private ContinuationScope contScope;
    private volatile StackTraceElement ste;

    /*
     * Construct an empty StackFrameInfo object that will be filled by the VM
     * during stack walking.
     *
     * @see StackStreamFactory.AbstractStackWalker#callStackWalk
     * @see StackStreamFactory.AbstractStackWalker#fetchStackFrames
     */
    StackFrameInfo(StackWalker walker) {
        super(walker);
    }

    // package-private called by StackStreamFactory to skip
    // the capability check
    Class<?> declaringClass() {
        return JLIA.getDeclaringClass(classOrMemberName);
    }

    // ----- implementation of StackFrame methods

    @Override
    public String getClassName() {
        return declaringClass().getName();
    }

    @Override
    public String getMethodName() {
        if (name == null) {
            expandStackFrameInfo();
            assert name != null;
        }
        return name;
    }

    @Override
    public MethodType getMethodType() {
        ensureRetainClassRefEnabled();

        if (type == null) {
            expandStackFrameInfo();
            assert type != null;
        }

        if (type instanceof MethodType mt) {
            return mt;
        }

        // type is not a MethodType yet.  Convert it thread-safely.
        synchronized (this) {
            if (type instanceof String sig) {
                type = JLIA.getMethodType(sig, declaringClass().getClassLoader());
            }
        }
        return (MethodType)type;
    }

    // expand the name and type field of StackFrameInfo
    private native void expandStackFrameInfo();

    @Override
    public String getDescriptor() {
        return getMethodType().descriptorString();
    }

    @Override
    public int getByteCodeIndex() {
        // bci not available for native methods
        if (isNativeMethod())
            return -1;

        return bci;
    }

    @Override
    public String getFileName() {
        return toStackTraceElement().getFileName();
    }

    @Override
    public int getLineNumber() {
        // line number not available for native methods
        if (isNativeMethod())
            return -2;

        return toStackTraceElement().getLineNumber();
    }


    @Override
    public boolean isNativeMethod() {
        return Modifier.isNative(flags);
    }

    private String getContinuationScopeName() {
        return contScope != null ? contScope.getName() : null;
    }

    @Override
    public String toString() {
        return toStackTraceElement().toString();
    }

    @Override
    public StackTraceElement toStackTraceElement() {
        StackTraceElement s = ste;
        if (s == null) {
            synchronized (this) {
                s = ste;
                if (s == null) {
                    ste = s = StackTraceElement.of(this);
                }
            }
        }
        return s;
    }
}
