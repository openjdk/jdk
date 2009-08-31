/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.xml.internal.xsom.impl.parser;

import com.sun.xml.internal.xsom.impl.Ref;
import com.sun.xml.internal.xsom.XSContentType;
import com.sun.xml.internal.xsom.XSType;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

public final class BaseContentRef implements Ref.ContentType, Patch {
    private final Ref.Type baseType;
    private final Locator loc;

    public BaseContentRef(final NGCCRuntimeEx $runtime, Ref.Type _baseType) {
        this.baseType = _baseType;
        $runtime.addPatcher(this);
        $runtime.addErrorChecker(new Patch() {
            public void run() throws SAXException {
                XSType t = baseType.getType();
                if (t.isComplexType() && t.asComplexType().getContentType().asParticle()!=null) {
                    $runtime.reportError(
                        Messages.format(Messages.ERR_SIMPLE_CONTENT_EXPECTED,
                            t.getTargetNamespace(), t.getName()), loc);
                }
            }
        });
        this.loc = $runtime.copyLocator();
    }

    public XSContentType getContentType() {
        XSType t = baseType.getType();
        if(t.asComplexType()!=null)
            return t.asComplexType().getContentType();
        else
            return t.asSimpleType();
    }

    public void run() throws SAXException {
        if (baseType instanceof Patch)
            ((Patch) baseType).run();
    }
}
