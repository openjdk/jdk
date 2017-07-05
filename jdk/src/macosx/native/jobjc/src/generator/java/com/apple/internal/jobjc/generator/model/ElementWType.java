/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.internal.jobjc.generator.model;

import com.apple.internal.jobjc.generator.model.types.NType;
import org.w3c.dom.Node;

import com.apple.internal.jobjc.generator.model.types.Type;
import com.apple.internal.jobjc.generator.utils.NTypeParser;

/**
 * An ElementWType has a type but does not necessarily represent a type. Examples are constants, enums, arguments, return values.
 */
public class ElementWType<P extends Element<?>> extends Element<P> {
    public final Type type;

    public ElementWType(final String name, final Type t, final P parent) {
        super(name, parent);
        this.type = t;
    }

    public ElementWType(final Node node, final Type t, final P parent) {
        super(node, parent);
        this.type = t;
    }

    public ElementWType(final Node node, final String declType, final P parent) {
        super(node, parent);
        final String type32 = getAttr(node, "type");
        final String type64 = getAttr(node, "type64");
        this.type = Type.getType(declType,
                        type32 == null ? NType.NUnknown.inst() : NTypeParser.parseFrom(type32),
                        type64 == null ? null : NTypeParser.parseFrom(type64));
    }

    public ElementWType(final Node node, final P parent){
        this(node, getAttr(node, "declared_type"), parent);
    }
}
