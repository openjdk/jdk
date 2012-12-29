/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.reader.xmlschema.ct;

import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.model.TypeUse;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIProperty;
import com.sun.tools.internal.xjc.reader.xmlschema.BGMBuilder;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSType;

/**
 * Binds a complex type derived from a simple type.
 * When a complex type is derived from a simple type, it is always
 * by extension.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
final class STDerivedComplexTypeBuilder extends CTBuilder {

    public boolean isApplicable(XSComplexType ct) {
        return ct.getBaseType().isSimpleType();
    }

    public void build(XSComplexType ct) {
        assert ct.getDerivationMethod()==XSType.EXTENSION;

        // base type is a simple type
        XSSimpleType baseType = ct.getBaseType().asSimpleType();

        // determine the binding of this complex type.
        builder.recordBindingMode(ct,ComplexTypeBindingMode.NORMAL);

        simpleTypeBuilder.refererStack.push(ct);
        TypeUse use = simpleTypeBuilder.build(baseType);
        simpleTypeBuilder.refererStack.pop();

        BIProperty prop = BIProperty.getCustomization(ct);
        CPropertyInfo p = prop.createValueProperty("Value",false,baseType,use, BGMBuilder.getName(baseType));
        selector.getCurrentBean().addProperty(p);

        // adds attributes and we are through.
        green.attContainer(ct);
    }

}
