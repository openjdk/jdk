/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.classfile;

import java.io.IOException;

import static com.sun.tools.classfile.ConstantPool.*;

/**
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Matcher_attribute extends Attribute {
    public static final int PAT_DECONSTRUCTOR   = 0x0001;
    public static final int PAT_TOTAL           = 0x0002;

    Matcher_attribute(ClassReader cr, int name_index, int length) throws IOException, ConstantPoolException {
        super(name_index, length);
        matcher_name_index = cr.readUnsignedShort();
        matcher_flags = cr.readUnsignedShort();
        matcher_methodtype = new CONSTANT_MethodType_info(cr.getConstantPool(), cr.readUnsignedShort());
        attributes = new Attributes(cr);
    }

    public Matcher_attribute(int name_index, int pattern_flags, int matcher_name_index, CONSTANT_MethodType_info matcher_methodtype, Attributes attributes) {
        super(name_index, 4);
        this.matcher_name_index = matcher_name_index;
        this.matcher_flags = pattern_flags;
        this.matcher_methodtype = matcher_methodtype;
        this.attributes = attributes;
    }

    @Override
    public <R, D> R accept(Visitor<R, D> visitor, D data) {
        return visitor.visitMatcher(this, data);
    }

    public final int matcher_name_index;
    public final int matcher_flags;
    public final CONSTANT_MethodType_info matcher_methodtype;
    public final Attributes attributes;
}
