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
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.reader.RawTypeSet;
import com.sun.tools.internal.xjc.reader.xmlschema.RawTypeSetBuilder;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIProperty;
import static com.sun.tools.internal.xjc.reader.xmlschema.ct.ComplexTypeBindingMode.FALLBACK_CONTENT;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSContentType;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSType;

/**
 * @author Kohsuke Kawaguchi
 */
final class MultiWildcardComplexTypeBuilder extends CTBuilder {

    public boolean isApplicable(XSComplexType ct) {
        if (!bgmBuilder.model.options.contentForWildcard) {
            return false;
        }
        XSType bt = ct.getBaseType();
        if (bt ==schemas.getAnyType() && ct.getContentType() != null) {
            XSParticle part = ct.getContentType().asParticle();
            if ((part != null) && (part.getTerm().isModelGroup())) {
                XSParticle[] parts = part.getTerm().asModelGroup().getChildren();
                int wildcardCount = 0;
                int i = 0;
                while ((i < parts.length) && (wildcardCount <= 1)) {
                    if (parts[i].getTerm().isWildcard()) {
                        wildcardCount += 1;
                    }
                    i++;
                }
                return (wildcardCount > 1);
            }
        }
        return false;
    }

    public void build(XSComplexType ct) {
        XSContentType contentType = ct.getContentType();

        builder.recordBindingMode(ct, FALLBACK_CONTENT);
        BIProperty prop = BIProperty.getCustomization(ct);

        CPropertyInfo p;

        if(contentType.asEmpty()!=null) {
            p = prop.createValueProperty("Content",false,ct,CBuiltinLeafInfo.STRING,null);
        } else {
            RawTypeSet ts = RawTypeSetBuilder.build(contentType.asParticle(),false);
            p = prop.createReferenceProperty("Content", false, ct, ts, true, false, true, false);
        }

        selector.getCurrentBean().addProperty(p);

        // adds attributes and we are through.
        green.attContainer(ct);
    }

}
