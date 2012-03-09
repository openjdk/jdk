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

import com.sun.tools.internal.xjc.model.CBuiltinLeafInfo;
import com.sun.tools.internal.xjc.model.CClass;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.reader.RawTypeSet;
import com.sun.tools.internal.xjc.reader.xmlschema.RawTypeSetBuilder;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIProperty;
import static com.sun.tools.internal.xjc.reader.xmlschema.ct.ComplexTypeBindingMode.FALLBACK_CONTENT;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSContentType;
import com.sun.xml.internal.xsom.XSType;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
final class MixedComplexTypeBuilder extends CTBuilder {

    public boolean isApplicable(XSComplexType ct) {
        XSType bt = ct.getBaseType();
        if(bt ==schemas.getAnyType() && ct.isMixed())
            return true;    // fresh mixed complex type

        // there's no complex type in the inheritance tree yet
        if (bt.isComplexType() &&
            !bt.asComplexType().isMixed() &&
            ct.isMixed() &&
            ct.getDerivationMethod() == XSType.EXTENSION) {
                if (!bgmBuilder.isGenerateMixedExtensions() && (ct.getContentType().asParticle() == null)) {
                    return false;
                }
                return true;
        }

        return false;
    }

    public void build(XSComplexType ct) {
        XSContentType contentType = ct.getContentType();

        boolean generateMixedExtensions = bgmBuilder.isGenerateMixedExtensions();
        if (generateMixedExtensions) {
            if (!(ct.getBaseType() == schemas.getAnyType() && ct.isMixed())) {
                XSComplexType baseType = ct.getBaseType().asComplexType();
                // build the base class
                CClass baseClass = selector.bindToType(baseType, ct, true);
                selector.getCurrentBean().setBaseClass(baseClass);
            }
        }

        builder.recordBindingMode(ct, FALLBACK_CONTENT);
        BIProperty prop = BIProperty.getCustomization(ct);

        CPropertyInfo p;

        if (generateMixedExtensions) {
            List<XSComplexType> cType = ct.getSubtypes();
            boolean isSubtyped = (cType != null) && (cType.size() > 0);

            if (contentType.asEmpty() != null) {
                if (isSubtyped) {
                    p = prop.createContentExtendedMixedReferenceProperty("Content", ct, null);
                } else {
                    p = prop.createValueProperty("Content",false,ct,CBuiltinLeafInfo.STRING,null);
                }
            } else if (contentType.asParticle() == null) {
                p = prop.createContentExtendedMixedReferenceProperty("Content", ct, null);
            } else {
                RawTypeSet ts = RawTypeSetBuilder.build(contentType.asParticle(), false);
                p = prop.createContentExtendedMixedReferenceProperty("Content", ct, ts);
            }

        } else {
            if(contentType.asEmpty()!=null) {
                p = prop.createValueProperty("Content",false,ct,CBuiltinLeafInfo.STRING,null);
            } else {
                RawTypeSet ts = RawTypeSetBuilder.build(contentType.asParticle(),false);
                p = prop.createReferenceProperty("Content", false, ct, ts, true, false, true, false);
            }
        }

        selector.getCurrentBean().addProperty(p);

        // adds attributes and we are through.
        green.attContainer(ct);
    }

}
