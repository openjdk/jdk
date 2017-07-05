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
import java.util.Optional;
import java.util.OptionalInt;

class StackFrameInfo implements StackFrame {
    private final static JavaLangInvokeAccess jlInvokeAccess =
        SharedSecrets.getJavaLangInvokeAccess();

    // -XX:+MemberNameInStackFrame will initialize MemberName and all other fields;
    // otherwise, VM will set the hidden fields (injected by the VM).
    // -XX:+MemberNameInStackFrame is temporary to enable performance measurement
    //
    // Footprint improvement: MemberName::clazz and MemberName::name
    // can replace StackFrameInfo::declaringClass and StackFrameInfo::methodName
    // Currently VM sets StackFrameInfo::methodName instead of expanding MemberName::name

    final StackWalker walker;
    final Class<?> declaringClass;
    final Object memberName;
    final int bci;

    // methodName, fileName, and lineNumber will be lazily set by the VM
    // when first requested.
    private String methodName;
    private String fileName = null;     // default for unavailable filename
    private int    lineNumber = -1;     // default for unavailable lineNumber

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

    // Call the VM to set methodName, lineNumber, and fileName
    private synchronized void ensureMethodInfoInitialized() {
        if (methodName == null) {
            setMethodInfo();
        }
    }

    @Override
    public String getMethodName() {
        ensureMethodInfoInitialized();
        return methodName;
    }

    @Override
    public Optional<String> getFileName() {
        ensureMethodInfoInitialized();
        return fileName != null ? Optional.of(fileName) : Optional.empty();
    }

    @Override
    public OptionalInt getLineNumber() {
        ensureMethodInfoInitialized();
        return lineNumber > 0 ? OptionalInt.of(lineNumber) : OptionalInt.empty();
    }

    @Override
    public boolean isNativeMethod() {
        ensureMethodInfoInitialized();
        return lineNumber == -2;
    }

    @Override
    public String toString() {
        ensureMethodInfoInitialized();
        // similar format as StackTraceElement::toString
        if (isNativeMethod()) {
            return getClassName() + "." + getMethodName() + "(Native Method)";
        } else {
            // avoid allocating Optional objects
            return getClassName() + "." + getMethodName() +
                "(" + (fileName != null ? fileName : "Unknown Source") +
                      (lineNumber > 0 ? ":" + lineNumber : " bci:" + bci) + ")";
        }
    }

    /**
     * Lazily initialize method name, file name, line number
     */
    private native void setMethodInfo();

    /**
     * Fill in source file name and line number of the given StackFrame array.
     */
    static native void fillInStackFrames(int startIndex,
                                         Object[] stackframes,
                                         int fromIndex, int toIndex);
}
