/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.internal.xjc.model.CClass;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.reader.RawTypeSet;
import com.sun.tools.internal.xjc.reader.xmlschema.RawTypeSetBuilder;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIGlobalBinding;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIProperty;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSType;

/**
 * Binds a complex type derived from another complex type
 * by restriction.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
final class RestrictedComplexTypeBuilder extends CTBuilder {

    public boolean isApplicable(XSComplexType ct) {
        XSType baseType = ct.getBaseType();
        return baseType!=schemas.getAnyType()
            &&  baseType.isComplexType()
            &&  ct.getDerivationMethod()==XSType.RESTRICTION;
    }

    public void build(XSComplexType ct) {

        if (bgmBuilder.getGlobalBinding().isRestrictionFreshType()) {
            // handle derivation-by-restriction like a whole new type
            new FreshComplexTypeBuilder().build(ct);
            return;
        }

        XSComplexType baseType = ct.getBaseType().asComplexType();

        // build the base class
        CClass baseClass = selector.bindToType(baseType,ct,true);
        assert baseClass!=null;   // global complex type must map to a class

        selector.getCurrentBean().setBaseClass(baseClass);

        if (bgmBuilder.isGenerateMixedExtensions()) {
            boolean forceFallbackInExtension = baseType.isMixed() &&
                                               ct.isMixed() &&
                                               (ct.getExplicitContent() != null) &&
                                               bgmBuilder.inExtensionMode;
            if (forceFallbackInExtension) {
                builder.recordBindingMode(ct, ComplexTypeBindingMode.NORMAL);

                BIProperty prop = BIProperty.getCustomization(ct);
                CPropertyInfo p;

                XSParticle particle = ct.getContentType().asParticle();
                if (particle != null) {
                    RawTypeSet ts = RawTypeSetBuilder.build(particle, false);
                    p = prop.createDummyExtendedMixedReferenceProperty("Content", ct, ts);
                    selector.getCurrentBean().addProperty(p);
                }
            } else {
                // determine the binding of this complex type.
                builder.recordBindingMode(ct,builder.getBindingMode(baseType));
            }
        } else {
            builder.recordBindingMode(ct,builder.getBindingMode(baseType));
        }
    }
}
