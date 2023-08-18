/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.access.SharedSecrets;

import java.lang.StackWalker.StackFrame;

class ClassFrameInfo implements StackFrame {
    protected Object classOrMemberName;    // Class or ResolvedMemberName initialized by VM
    protected int flags;

    // package-private called by StackStreamFactory to skip
    // the capability check
    Class<?> declaringClass() {
        return (Class<?>) classOrMemberName;
    }

    boolean isCallerSensitive() {
        return SharedSecrets.getJavaLangInvokeAccess().isCallerSensitive(flags);
    }
    boolean isHidden() {
        return SharedSecrets.getJavaLangInvokeAccess().isHiddenMember(flags);
    }

    // ----- implementation of StackFrame methods

    @Override
    public String getClassName() {
        return declaringClass().getName();
    }

    @Override
    public String getMethodName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<?> getDeclaringClass() {
        return declaringClass();
    }

    @Override
    public int getByteCodeIndex() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getFileName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getLineNumber() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isNativeMethod() {
        throw new UnsupportedOperationException();
    }

    @Override
    public StackTraceElement toStackTraceElement() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        String tags = isHidden() ? " hidden" : "";
        if (isCallerSensitive()) {
            tags += " caller sensitive";
        }
        return declaringClass().getName() + tags;
    }
}
