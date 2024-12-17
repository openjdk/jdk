/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.classfile.attribute;

import java.lang.classfile.Attribute;
import java.lang.classfile.MethodElement;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.util.Arrays;
import java.util.List;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;
import jdk.internal.classfile.impl.Util;

/**
 * Models the {@code Exceptions} attribute (JVMS {@jvms 4.7.5}), which can appear on
 * methods, and records the exceptions declared to be thrown by this method.
 * Delivered as a {@link MethodElement} when traversing the elements of a
 * {@link java.lang.classfile.MethodModel}.
 * <p>
 * The attribute does not permit multiple instances in a given location.
 * Subsequent occurrence of the attribute takes precedence during the attributed
 * element build or transformation.
 *
 * @since 24
 */
public sealed interface ExceptionsAttribute
        extends Attribute<ExceptionsAttribute>, MethodElement
        permits BoundAttribute.BoundExceptionsAttribute,
                UnboundAttribute.UnboundExceptionsAttribute {

    /**
     * {@return the exceptions declared to be thrown by this method}
     */
    List<ClassEntry> exceptions();

    /**
     * {@return an {@code Exceptions} attribute}
     * @param exceptions the checked exceptions that may be thrown from this method
     */
    static ExceptionsAttribute of(List<ClassEntry> exceptions) {
        return new UnboundAttribute.UnboundExceptionsAttribute(exceptions);
    }

    /**
     * {@return an {@code Exceptions} attribute}
     * @param exceptions the checked exceptions that may be thrown from this method
     */
    static ExceptionsAttribute of(ClassEntry... exceptions) {
        return of(List.of(exceptions));
    }

    /**
     * {@return an {@code Exceptions} attribute}
     * @param exceptions the checked exceptions that may be thrown from this method
     */
    static ExceptionsAttribute ofSymbols(List<ClassDesc> exceptions) {
        return of(Util.entryList(exceptions));
    }

    /**
     * {@return an {@code Exceptions} attribute}
     * @param exceptions the checked exceptions that may be thrown from this method
     */
    static ExceptionsAttribute ofSymbols(ClassDesc... exceptions) {
        return ofSymbols(Arrays.asList(exceptions));
    }
}
