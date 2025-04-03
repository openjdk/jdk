/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.classfile.AttributeMapper;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodElement;
import java.lang.reflect.Executable;
import java.util.List;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models the {@link Attributes#methodParameters() MethodParameters} attribute
 * (JVMS {@jvms 4.7.24}), which records reflective information about this
 * method's parameters such as access modifiers.
 * <p>
 * This attribute only appears on methods, and does not permit {@linkplain
 * AttributeMapper#allowMultiple multiple instances} in a method.  It has a
 * data dependency on the {@linkplain AttributeMapper.AttributeStability#CP_REFS
 * constant pool}.
 * <p>
 * The attribute was introduced in the Java SE Platform version 8, major version
 * {@value ClassFile#JAVA_8_VERSION}.
 *
 * @see Attributes#methodParameters()
 * @see Executable#getParameters()
 * @jvms 4.7.24 The {@code MethodParameters} Attribute
 * @since 24
 */
public sealed interface MethodParametersAttribute
        extends Attribute<MethodParametersAttribute>, MethodElement
        permits BoundAttribute.BoundMethodParametersAttribute,
                UnboundAttribute.UnboundMethodParametersAttribute {

    /**
     * {@return information about the parameters of the method}  The i'th entry
     * in the list corresponds to the i'th parameter in the method descriptor.
     */
    List<MethodParameterInfo> parameters();

    /**
     * {@return a {@code MethodParameters} attribute}
     * @param parameters the method parameter descriptions
     */
    static MethodParametersAttribute of(List<MethodParameterInfo> parameters) {
        return new UnboundAttribute.UnboundMethodParametersAttribute(parameters);
    }

    /**
     * {@return a {@code MethodParameters} attribute}
     * @param parameters the method parameter descriptions
     */
    static MethodParametersAttribute of(MethodParameterInfo... parameters) {
        return of(List.of(parameters));
    }
}
