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

import com.sun.tools.internal.xjc.model.CClass;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.reader.RawTypeSet;
import com.sun.tools.internal.xjc.reader.xmlschema.RawTypeSetBuilder;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIGlobalBinding;
import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIProperty;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSContentType;
import com.sun.xml.internal.xsom.XSType;

/**
 * @author Kohsuke Kawaguchi
 */
final class MixedExtendedComplexTypeBuilder extends AbstractExtendedComplexTypeBuilder {

    public boolean isApplicable(XSComplexType ct) {

        if (!bgmBuilder.isGenerateMixedExtensions()) return false;

        XSType bt = ct.getBaseType();
        if (bt.isComplexType() &&
            bt.asComplexType().isMixed() &&
            ct.isMixed() &&
            ct.getDerivationMethod()==XSType.EXTENSION &&
            ct.getContentType().asParticle() != null &&
            ct.getExplicitContent().asEmpty() == null
            )  {
                return true;
        }

        return false;
    }

    public void build(XSComplexType ct) {
        XSComplexType baseType = ct.getBaseType().asComplexType();

        // build the base class
        CClass baseClass = selector.bindToType(baseType, ct, true);
        assert baseClass != null;   // global complex type must map to a class

        if (!checkIfExtensionSafe(baseType, ct)) {
            // error. We can't handle any further extension
            errorReceiver.error(ct.getLocator(),
                    Messages.ERR_NO_FURTHER_EXTENSION.format(
                    baseType.getName(), ct.getName() )
            );
            return;
        }

        selector.getCurrentBean().setBaseClass(baseClass);
        builder.recordBindingMode(ct, ComplexTypeBindingMode.FALLBACK_EXTENSION);

        BIProperty prop = BIProperty.getCustomization(ct);
        CPropertyInfo p;

        RawTypeSet ts = RawTypeSetBuilder.build(ct.getContentType().asParticle(), false);
        p = prop.createDummyExtendedMixedReferenceProperty("contentOverrideFor" + ct.getName(), ct, ts);

        selector.getCurrentBean().addProperty(p);

        // adds attributes and we are through.
        green.attContainer(ct);
    }

}
