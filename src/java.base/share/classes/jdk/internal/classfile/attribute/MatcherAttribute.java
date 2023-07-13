/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.classfile.attribute;

import jdk.internal.classfile.AccessFlags;
import jdk.internal.classfile.Attribute;
import jdk.internal.classfile.AttributedElement;
import jdk.internal.classfile.MethodElement;
import jdk.internal.classfile.constantpool.IntegerEntry;
import jdk.internal.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;

import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.List;
import java.util.Set;

/**
 * Models the {@code Matcher} attribute {@jvms X.X.XX}, which can
 * appear on matchers, and records additional information about the
 * nature of this matcher represented as a method.
 *
 * TODO
 * Delivered as a {@link MethodElement} when
 * traversing the elements of a {@link jdk.internal.classfile.MethodModel}.
 */
public sealed interface MatcherAttribute
        extends Attribute<MatcherAttribute>, MethodElement, AttributedElement
        permits BoundAttribute.BoundMatcherAttribute,
                UnboundAttribute.UnboundMatcherAttribute {

    /**
     * {@return the the module flags of the module, as a bit mask}
     */
    int matcherFlagsMask();

    /**
     * {@return the the module flags of the module, as a set of enum constants}
     */
    default Set<AccessFlag> matcherFlags() {
        return AccessFlag.maskToAccessFlags(matcherFlagsMask(), AccessFlag.Location.MATCHER);
    }

    /** {@return the name of this method} */
    Utf8Entry matcherName();

    /** {@return the method descriptor of this method} */
    Utf8Entry matcherMethodType();

    /** {@return the method descriptor of this method, as a symbolic descriptor} */
    default MethodTypeDesc matcherTypeSymbol() {
        return MethodTypeDesc.ofDescriptor(matcherMethodType().stringValue());
    }

    static MatcherAttribute of(String matcherName,
                               int matcherFlags,
                               MethodTypeDesc matcherDescriptor,
                               List<Attribute<?>> matcherAttributes) {
        return new UnboundAttribute.UnboundMatcherAttribute(
                TemporaryConstantPool.INSTANCE.utf8Entry(matcherName),
                matcherFlags,
                TemporaryConstantPool.INSTANCE.utf8Entry(matcherDescriptor.descriptorString()),
                matcherAttributes);
    }

    static MatcherAttribute of(Utf8Entry matcherName,
                               int matcherFlags,
                               Utf8Entry matcherDescriptor,
                               List<Attribute<?>> matcherAttributes) {
        return new UnboundAttribute.UnboundMatcherAttribute(matcherName, matcherFlags, matcherDescriptor, matcherAttributes);
    }
}
