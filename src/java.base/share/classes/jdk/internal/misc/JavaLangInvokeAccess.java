/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.misc;

import java.lang.invoke.MethodType;
import java.util.Map;

public interface JavaLangInvokeAccess {
    /**
     * Create a new MemberName instance. Used by {@code StackFrameInfo}.
     */
    Object newMemberName();

    /**
     * Returns the name for the given MemberName. Used by {@code StackFrameInfo}.
     */
    String getName(Object mname);

    /**
     * Returns the {@code MethodType} for the given MemberName.
     * Used by {@code StackFrameInfo}.
     */
    MethodType getMethodType(Object mname);

    /**
     * Returns the descriptor for the given MemberName.
     * Used by {@code StackFrameInfo}.
     */
    String getMethodDescriptor(Object mname);

    /**
     * Returns {@code true} if the given MemberName is a native method.
     * Used by {@code StackFrameInfo}.
     */
    boolean isNative(Object mname);

    /**
     * Returns the declaring class for the given MemberName.
     * Used by {@code StackFrameInfo}.
     */
    Class<?> getDeclaringClass(Object mname);

    /**
     * Returns a {@code byte[]} representation of a class implementing
     * DirectMethodHandle of each pairwise combination of {@code MethodType} and
     * an {@code int} representing method type.  Used by
     * GenerateJLIClassesPlugin to generate such a class during the jlink phase.
     */
    byte[] generateDirectMethodHandleHolderClassBytes(String className,
            MethodType[] methodTypes, int[] types);

    /**
     * Returns a {@code byte[]} representation of a class implementing
     * DelegatingMethodHandles of each {@code MethodType} kind in the
     * {@code methodTypes} argument.  Used by GenerateJLIClassesPlugin to
     * generate such a class during the jlink phase.
     */
    byte[] generateDelegatingMethodHandleHolderClassBytes(String className,
            MethodType[] methodTypes);

    /**
     * Returns a {@code byte[]} representation of {@code BoundMethodHandle}
     * species class implementing the signature defined by {@code types}. Used
     * by GenerateJLIClassesPlugin to enable generation of such classes during
     * the jlink phase. Should do some added validation since this string may be
     * user provided.
     */
    Map.Entry<String, byte[]> generateConcreteBMHClassBytes(
            final String types);

    /**
     * Returns a {@code byte[]} representation of a class implementing
     * the zero and identity forms of all {@code LambdaForm.BasicType}s.
     */
    byte[] generateBasicFormsClassBytes(final String className);

    /**
     * Returns a {@code byte[]} representation of a class implementing
     * the invoker forms for the set of supplied {@code invokerMethodTypes}
     * and {@code callSiteMethodTypes}.
     */
    byte[] generateInvokersHolderClassBytes(String className,
            MethodType[] invokerMethodTypes,
            MethodType[] callSiteMethodTypes);

}
