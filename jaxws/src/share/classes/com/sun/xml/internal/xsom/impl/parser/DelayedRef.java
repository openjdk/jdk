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

import com.sun.xml.internal.xsom.XSAttGroupDecl;
import com.sun.xml.internal.xsom.XSAttributeDecl;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSDeclaration;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSIdentityConstraint;
import com.sun.xml.internal.xsom.XSModelGroupDecl;
import com.sun.xml.internal.xsom.XSSchemaSet;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSTerm;
import com.sun.xml.internal.xsom.XSType;
import com.sun.xml.internal.xsom.impl.Ref;
import com.sun.xml.internal.xsom.impl.SchemaImpl;
import com.sun.xml.internal.xsom.impl.UName;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * Reference by name.
 *
 * UName will be later resolved to a target object,
 * after all the schemas are parsed.
 */
public abstract class DelayedRef implements Patch {
    DelayedRef( PatcherManager _manager, Locator _source, SchemaImpl _schema, UName _name ) {

        this.schema = _schema.getRoot();
        this.manager = _manager;
        this.name = _name;
        this.source = _source;

        if(name==null)  throw new InternalError();

        manager.addPatcher(this);
    }

    /**
     * Patch implementation. Makes sure that the name resolves
     * to a schema component.
     */
    public void run() throws SAXException {
        if(ref==null)    // redefinition can set ref without actually resolving the reference
            resolve();
        manager = null;     // avoid keeping the reference too long
        name = null;
        source = null;
    }


    protected final XSSchemaSet schema;
    private PatcherManager manager;
    private UName name;
    /** location in the source file where this reference was made. */
    private Locator source;

    protected abstract Object resolveReference( UName name );
    protected abstract String getErrorProperty();

    private Object ref=null;
    protected final Object _get() {
        if(ref==null)   throw new InternalError("unresolved reference");
        return ref;
    }

    private void resolve() throws SAXException {
        ref = resolveReference(name);
        if(ref==null)
            manager.reportError(
                Messages.format(getErrorProperty(),name.getQualifiedName()),
                source );
    }

    /**
     * If this reference refers to the given declaration,
     * resolve the reference now. This is used to implement redefinition.
     */
    public void redefine(XSDeclaration d) {
        if( !d.getTargetNamespace().equals(name.getNamespaceURI())
        ||  !d.getName().equals(name.getName()) )
            return;

        ref = d;
        manager = null;
        name = null;
        source = null;
    }


    public static class Type extends DelayedRef implements Ref.Type {
        public Type( PatcherManager manager, Locator loc, SchemaImpl schema, UName name ) {
            super(manager,loc,schema,name);
        }
        protected Object resolveReference( UName name ) {
            Object o = super.schema.getSimpleType(
                name.getNamespaceURI(), name.getName() );
            if(o!=null)     return o;

            return super.schema.getComplexType(
                name.getNamespaceURI(),
                name.getName());
        }
        protected String getErrorProperty() {
            return Messages.ERR_UNDEFINED_TYPE;
        }

        public XSType getType() { return (XSType)super._get(); }
    }

    public static class SimpleType extends DelayedRef implements Ref.SimpleType {
        public SimpleType( PatcherManager manager, Locator loc, SchemaImpl schema, UName name ) {
            super(manager,loc,schema,name);
        }
        public XSSimpleType getType() { return (XSSimpleType)_get(); }

        protected Object resolveReference( UName name ) {
            return super.schema.getSimpleType(
                name.getNamespaceURI(),
                name.getName());
        }

        protected String getErrorProperty() {
            return Messages.ERR_UNDEFINED_SIMPLETYPE;
        }
    }

    public static class ComplexType extends DelayedRef implements Ref.ComplexType {
        public ComplexType( PatcherManager manager, Locator loc, SchemaImpl schema, UName name ) {
            super(manager,loc,schema,name);
        }
        protected Object resolveReference( UName name ) {
            return super.schema.getComplexType(
                name.getNamespaceURI(),
                name.getName());
        }

        protected String getErrorProperty() {
            return Messages.ERR_UNDEFINED_COMPLEXTYPE;
        }

        public XSComplexType getType() { return (XSComplexType)super._get(); }
    }

    public static class Element extends DelayedRef implements Ref.Element {
        public Element( PatcherManager manager, Locator loc, SchemaImpl schema, UName name ) {
            super(manager,loc,schema,name);
        }
        protected Object resolveReference( UName name ) {
            return super.schema.getElementDecl(
                name.getNamespaceURI(),
                name.getName());
        }

        protected String getErrorProperty() {
            return Messages.ERR_UNDEFINED_ELEMENT;
        }

        public XSElementDecl get() { return (XSElementDecl)super._get(); }
        public XSTerm getTerm() { return get(); }
    }

    public static class ModelGroup extends DelayedRef implements Ref.Term {
        public ModelGroup( PatcherManager manager, Locator loc, SchemaImpl schema, UName name ) {
            super(manager,loc,schema,name);
        }
        protected Object resolveReference( UName name ) {
            return super.schema.getModelGroupDecl(
                name.getNamespaceURI(),
                name.getName());
        }

        protected String getErrorProperty() {
            return Messages.ERR_UNDEFINED_MODELGROUP;
        }

        public XSModelGroupDecl get() { return (XSModelGroupDecl)super._get(); }
        public XSTerm getTerm() { return get(); }
    }

    public static class AttGroup extends DelayedRef implements Ref.AttGroup {
        public AttGroup( PatcherManager manager, Locator loc, SchemaImpl schema, UName name ) {
            super(manager,loc,schema,name);
        }
        protected Object resolveReference( UName name ) {
            return super.schema.getAttGroupDecl(
                name.getNamespaceURI(),
                name.getName());
        }

        protected String getErrorProperty() {
            return Messages.ERR_UNDEFINED_ATTRIBUTEGROUP;
        }

        public XSAttGroupDecl get() { return (XSAttGroupDecl)super._get(); }
    }

    public static class Attribute extends DelayedRef implements Ref.Attribute {
        public Attribute( PatcherManager manager, Locator loc, SchemaImpl schema, UName name ) {
            super(manager,loc,schema,name);
        }
        protected Object resolveReference( UName name ) {
            return super.schema.getAttributeDecl(
                name.getNamespaceURI(),
                name.getName());
        }

        protected String getErrorProperty() {
            return Messages.ERR_UNDEFINED_ATTRIBUTE;
        }

        public XSAttributeDecl getAttribute() { return (XSAttributeDecl)super._get(); }
    }

    public static class IdentityConstraint extends DelayedRef implements Ref.IdentityConstraint {
        public IdentityConstraint( PatcherManager manager, Locator loc, SchemaImpl schema, UName name ) {
            super(manager,loc,schema,name);
        }
        protected Object resolveReference( UName name ) {
            return super.schema.getIdentityConstraint(
                name.getNamespaceURI(),
                name.getName());
        }

        protected String getErrorProperty() {
            return Messages.ERR_UNDEFINED_IDENTITY_CONSTRAINT;
        }

        public XSIdentityConstraint get() { return (XSIdentityConstraint)super._get(); }
    }
}
