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
import com.sun.tools.internal.txw2.model.prop.ElementProp;
import com.sun.tools.internal.txw2.model.prop.LeafElementProp;
import com.sun.tools.internal.txw2.model.prop.Prop;
import com.sun.xml.internal.txw2.TypedXmlWriter;
import com.sun.xml.internal.txw2.annotation.XmlElement;
import org.xml.sax.Locator;

import javax.xml.namespace.QName;
import java.util.HashSet;
import java.util.Set;

/**
 * Element declaration.
 *
 * @author Kohsuke Kawaguchi
 */
public class Element extends XmlNode {
    /**
     * True if this element can be a root element.
     */
    public boolean isRoot;

    private Strategy strategy;

    public Element(Locator location, QName name, Leaf leaf) {
        super(location, name, leaf);
    }

    /**
     * Returns true if this element should generate an interface.
     */
    private Strategy decideStrategy() {
        if(isRoot)
            return new ToInterface();

        if(hasOneChild() && leaf instanceof Ref && !((Ref)leaf).isInline())
            return new HasOneRef((Ref)leaf);

        Set<Leaf> children = collectChildren();
        for( Leaf l : children ) {
            if( l instanceof XmlNode )
                // if it has attributes/elements in children
                // generate an interface
                return new ToInterface();
        }

        // otherwise this element only has data, so just generate methods for them.
        return new DataOnly();
    }

    void declare(NodeSet nset) {
        strategy = decideStrategy();
        strategy.declare(nset);
    }

    void generate(NodeSet nset) {
        strategy.generate(nset);
    }

    void generate(JDefinedClass clazz, NodeSet nset, Set<Prop> props) {
        strategy.generate(clazz,nset,props);
    }


    private JMethod generateMethod(JDefinedClass clazz, NodeSet nset, JType retT) {
        String methodName = NameUtil.toMethodName(name.getLocalPart());

        JMethod m = clazz.method(JMod.PUBLIC, retT, methodName);

        JAnnotationUse a = m.annotate(XmlElement.class);
        if(!methodName.equals(name.getLocalPart()))
            a.param("value",name.getLocalPart());
        if(nset.defaultNamespace==null || !nset.defaultNamespace.equals(name.getNamespaceURI()))
            a.param("ns",name.getNamespaceURI());

        return m;
    }

    public String toString() {
        return "Element "+name;
    }


    interface Strategy {
        void declare(NodeSet nset);
        void generate(NodeSet nset);
        void generate(JDefinedClass clazz, NodeSet nset, Set<Prop> props);
    }

    /**
     * Maps to an interface
     */
    private class ToInterface implements Strategy {
        private JDefinedClass clazz;

        public void declare(NodeSet nset) {
            String cname;
            if(alternativeName!=null)
                cname = alternativeName;
            else
                cname = name.getLocalPart();
            clazz = nset.createClass(cname);
            clazz._implements(TypedXmlWriter.class);

            clazz.annotate(XmlElement.class)
                .param("value",name.getLocalPart());
            // TODO: namespace
        }

        public void generate(NodeSet nset) {
            HashSet<Prop> props = new HashSet<Prop>();
            for( Leaf l : Element.this )
                l.generate(clazz,nset, props);
        }

        public void generate(JDefinedClass outer, NodeSet nset, Set<Prop> props) {
            if(props.add(new ElementProp(name,clazz)))
                generateMethod(outer, nset, clazz);
        }
    }

    /**
     * For things like "element foo {refToAnotherPattern}"
     */
    private class HasOneRef implements Strategy {
        private final Ref ref;

        public HasOneRef(Ref ref) {
            this.ref = ref;
        }

        public void declare(NodeSet nset) {
        }
        public void generate(NodeSet nset) {
        }

        public void generate(JDefinedClass clazz, NodeSet nset, Set<Prop> props) {
            if(props.add(new ElementProp(name,ref.def.clazz)))
                generateMethod(clazz, nset, ref.def.clazz);
        }
    }

    private class DataOnly implements Strategy {
        public void declare(NodeSet nset) {
        }
        public void generate(NodeSet nset) {
        }

        // TODO: code share with Attribute
        public void generate(JDefinedClass clazz, NodeSet nset, Set<Prop> props) {
            Set<JType> types = new HashSet<JType>();

            for( Leaf l : collectChildren() ) {
                if (l instanceof Text) {
                    types.add(((Text)l).getDatatype(nset));
                }
            }

            for( JType t : types ) {
                if(!props.add(new LeafElementProp(name,t)))
                    continue;
                generateMethod(clazz,
                        nset, nset.opts.chainMethod? (JType)clazz : nset.codeModel.VOID
                ).param(t,"value");
            }
        }
    }
}
