/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.txw2.model;

import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import com.sun.tools.internal.txw2.NameUtil;
import com.sun.tools.internal.txw2.model.prop.AttributeProp;
import com.sun.tools.internal.txw2.model.prop.Prop;
import com.sun.xml.internal.txw2.annotation.XmlAttribute;
import org.xml.sax.Locator;

import javax.xml.namespace.QName;
import java.util.HashSet;
import java.util.Set;

/**
 * Attribute declaration.
 *
 * @author Kohsuke Kawaguchi
 */
public class Attribute extends XmlNode {
    public Attribute(Locator location, QName name, Leaf leaf) {
        super(location, name, leaf);
    }

    void declare(NodeSet nset) {
        ; // attributes won't produce a class
    }

    void generate(NodeSet nset) {
        ; // nothing
    }

    void generate(JDefinedClass clazz, NodeSet nset, Set<Prop> props) {
        Set<JType> types = new HashSet<JType>();

        for( Leaf l : collectChildren() ) {
            if (l instanceof Text) {
                types.add(((Text)l).getDatatype(nset));
            }
        }

        String methodName = NameUtil.toMethodName(name.getLocalPart());

        for( JType t : types ) {
            if(!props.add(new AttributeProp(name,t)))
                continue;

            JMethod m = clazz.method(JMod.PUBLIC,
                nset.opts.chainMethod? (JType)clazz : nset.codeModel.VOID,
                methodName);
            m.param(t,"value");

            JAnnotationUse a = m.annotate(XmlAttribute.class);
            if(!methodName.equals(name.getLocalPart()))
                a.param("value",name.getLocalPart());
            if(!name.getNamespaceURI().equals(""))
                a.param("ns",name.getNamespaceURI());

        }
    }

    public String toString() {
        return "Attribute "+name;
    }
}
