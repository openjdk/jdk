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
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSContentType;
import com.sun.xml.internal.xsom.XSType;


/**
 * Binds a complex type derived from another complex type by extension.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
final class ExtendedComplexTypeBuilder extends AbstractExtendedComplexTypeBuilder {

    public boolean isApplicable(XSComplexType ct) {
        XSType baseType = ct.getBaseType();
        return baseType!=schemas.getAnyType()
            &&  baseType.isComplexType()
            &&  ct.getDerivationMethod()==XSType.EXTENSION;
    }

    public void build(XSComplexType ct) {
        XSComplexType baseType = ct.getBaseType().asComplexType();

        // build the base class
        CClass baseClass = selector.bindToType(baseType, ct, true);
        assert baseClass != null;   // global complex type must map to a class

        selector.getCurrentBean().setBaseClass(baseClass);

        // derivation by extension.
        ComplexTypeBindingMode baseTypeFlag = builder.getBindingMode(baseType);

        XSContentType explicitContent = ct.getExplicitContent();

        if (!checkIfExtensionSafe(baseType, ct)) {
            // error. We can't handle any further extension
            errorReceiver.error(ct.getLocator(),
                    Messages.ERR_NO_FURTHER_EXTENSION.format(
                    baseType.getName(), ct.getName() )
            );
            return;
        }

        // explicit content is always either empty or a particle.
        if (explicitContent != null && explicitContent.asParticle() != null) {
            if (baseTypeFlag == ComplexTypeBindingMode.NORMAL) {
                // if we have additional explicit content, process them.
                builder.recordBindingMode(ct,
                        bgmBuilder.getParticleBinder().checkFallback(explicitContent.asParticle())
                        ? ComplexTypeBindingMode.FALLBACK_REST
                        : ComplexTypeBindingMode.NORMAL);

                bgmBuilder.getParticleBinder().build(explicitContent.asParticle());

            } else {
                // the base class has already done the fallback.
                // don't add anything new
                builder.recordBindingMode(ct, baseTypeFlag );
            }
        } else {
            // if it's empty, no additional processing is necessary
            builder.recordBindingMode(ct, baseTypeFlag);
        }

        // adds attributes and we are through.
        green.attContainer(ct);
    }

}
