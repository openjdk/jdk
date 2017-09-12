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

package com.sun.tools.internal.xjc.reader.xmlschema.bindinfo;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.namespace.QName;

import com.sun.xml.internal.xsom.XSComponent;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.reader.Ring;
import com.sun.tools.internal.xjc.reader.Const;
import com.sun.tools.internal.xjc.reader.xmlschema.BGMBuilder;

/**
 * Controls the {@code ObjectFactory} method name.
 *
 * @author Kohsuke Kawaguchi
 */
@XmlRootElement(name="factoryMethod")
public class BIFactoryMethod extends AbstractDeclarationImpl {
    @XmlAttribute
    public String name;

    /**
     * If the given component has {@link BIInlineBinaryData} customization,
     * reflect that to the specified property.
     */
    public static void handle(XSComponent source, CPropertyInfo prop) {
        BIInlineBinaryData inline = Ring.get(BGMBuilder.class).getBindInfo(source).get(BIInlineBinaryData.class);
        if(inline!=null) {
            prop.inlineBinaryData = true;
            inline.markAsAcknowledged();
        }
    }


    public final QName getName() { return NAME; }

    /** Name of the declaration. */
    public static final QName NAME = new QName(Const.JAXB_NSURI,"factoryMethod");
}
