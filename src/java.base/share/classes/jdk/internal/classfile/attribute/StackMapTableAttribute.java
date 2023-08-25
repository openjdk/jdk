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

import java.util.List;

import jdk.internal.classfile.Attribute;
import jdk.internal.classfile.CodeElement;
import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models the {@code StackMapTable} attribute {@jvms 4.7.4}, which can appear
 * on a {@code Code} attribute.
 */
public sealed interface StackMapTableAttribute
        extends Attribute<StackMapTableAttribute>, CodeElement
        permits BoundAttribute.BoundStackMapTableAttribute, UnboundAttribute.UnboundStackMapTableAttribute {

    /**
     * {@return the stack map frames}
     */
    List<StackMapFrameInfo> entries();

    public static StackMapTableAttribute of(List<StackMapFrameInfo> entries) {
        return new UnboundAttribute.UnboundStackMapTableAttribute(entries);
    }
}
