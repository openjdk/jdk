/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.JavaLangInvokeAccess;
import jdk.internal.misc.SharedSecrets;

import static java.lang.StackWalker.Option.*;
import java.lang.StackWalker.StackFrame;
import java.lang.reflect.Module;
import java.util.Optional;
import java.util.OptionalInt;

class StackFrameInfo implements StackFrame {
    private final static JavaLangInvokeAccess jlInvokeAccess =
        SharedSecrets.getJavaLangInvokeAccess();

    // Footprint improvement: MemberName::clazz can replace
    // StackFrameInfo::declaringClass.

    final StackWalker walker;
    final Class<?> declaringClass;
    final Object memberName;
    final short bci;
    private volatile StackTraceElement ste;

    /*
     * Create StackFrameInfo for StackFrameTraverser and LiveStackFrameTraverser
     * to use
     */
    StackFrameInfo(StackWalker walker) {
        this.walker = walker;
        this.declaringClass = null;
        this.bci = -1;
        this.memberName = jlInvokeAccess.newMemberName();
    }

    @Override
    public String getClassName() {
        return declaringClass.getName();
    }

    @Override
    public Class<?> getDeclaringClass() {
        walker.ensureAccessEnabled(RETAIN_CLASS_REFERENCE);
        return declaringClass;
    }

    @Override
    public String getMethodName() {
        return jlInvokeAccess.getName(memberName);
    }

    @Override
    public final Optional<String> getFileName() {
        StackTraceElement ste = toStackTraceElement();
        return ste.getFileName() != null ? Optional.of(ste.getFileName()) : Optional.empty();
    }

    @Override
    public final OptionalInt getLineNumber() {
        StackTraceElement ste = toStackTraceElement();
        return ste.getLineNumber() > 0 ? OptionalInt.of(ste.getLineNumber()) : OptionalInt.empty();
    }

    @Override
    public final boolean isNativeMethod() {
        StackTraceElement ste = toStackTraceElement();
        return ste.isNativeMethod();
    }

    @Override
    public String toString() {
        StackTraceElement ste = toStackTraceElement();
        return ste.toString();
    }

    /**
     * Fill in the fields of the given StackTraceElement
     */
    private native void toStackTraceElement0(StackTraceElement ste);

    @Override
    public StackTraceElement toStackTraceElement() {
        StackTraceElement s = ste;
        if (s == null) {
            synchronized (this) {
                s = ste;
                if (s == null) {
                    s = new StackTraceElement();
                    toStackTraceElement0(s);
                    ste = s;
                }
            }
        }
        return s;
    }
}
