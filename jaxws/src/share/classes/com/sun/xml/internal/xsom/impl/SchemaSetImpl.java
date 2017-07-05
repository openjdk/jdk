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

package com.sun.xml.internal.xsom.impl;

import com.sun.xml.internal.xsom.XSAttGroupDecl;
import com.sun.xml.internal.xsom.XSAttributeDecl;
import com.sun.xml.internal.xsom.XSAttributeUse;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSContentType;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSFacet;
import com.sun.xml.internal.xsom.XSIdentityConstraint;
import com.sun.xml.internal.xsom.XSListSimpleType;
import com.sun.xml.internal.xsom.XSModelGroup;
import com.sun.xml.internal.xsom.XSModelGroupDecl;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSRestrictionSimpleType;
import com.sun.xml.internal.xsom.XSSchema;
import com.sun.xml.internal.xsom.XSSchemaSet;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSType;
import com.sun.xml.internal.xsom.XSUnionSimpleType;
import com.sun.xml.internal.xsom.XSVariety;
import com.sun.xml.internal.xsom.XSWildcard;
import com.sun.xml.internal.xsom.visitor.XSContentTypeFunction;
import com.sun.xml.internal.xsom.visitor.XSContentTypeVisitor;
import com.sun.xml.internal.xsom.visitor.XSFunction;
import com.sun.xml.internal.xsom.visitor.XSSimpleTypeFunction;
import com.sun.xml.internal.xsom.visitor.XSSimpleTypeVisitor;
import com.sun.xml.internal.xsom.visitor.XSVisitor;
import org.xml.sax.Locator;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Vector;

public class SchemaSetImpl implements XSSchemaSet
{
    private final Map<String,XSSchema> schemas = new HashMap<String,XSSchema>();
    private final Vector<XSSchema> schemas2 = new Vector<XSSchema>();
    private final List<XSSchema> readonlySchemaList = Collections.unmodifiableList(schemas2);

    /**
     * Gets a reference to the existing schema or creates a new one
     * if none exists yet.
     */
    public SchemaImpl createSchema(String targetNamespace, Locator location) {
        SchemaImpl obj = (SchemaImpl)schemas.get(targetNamespace);
        if (obj == null) {
            obj = new SchemaImpl(this, location, targetNamespace);
            schemas.put(targetNamespace, obj);
            schemas2.add(obj);
        }
        return obj;
    }

    public int getSchemaSize() {
        return schemas.size();
    }
    public XSSchema getSchema(String targetNamespace) {
        return schemas.get(targetNamespace);
    }
    public XSSchema getSchema(int idx) {
        return schemas2.get(idx);
    }
    public Iterator<XSSchema> iterateSchema() {
        return schemas2.iterator();
    }

    public final Collection<XSSchema> getSchemas() {
        return readonlySchemaList;
    }

    public XSType getType(String ns, String localName) {
        XSSchema schema = getSchema(ns);
        if(schema==null)    return null;

        return schema.getType(localName);
    }

    public XSSimpleType getSimpleType( String ns, String localName ) {
        XSSchema schema = getSchema(ns);
        if(schema==null)    return null;

        return schema.getSimpleType(localName);
    }

    public XSElementDecl getElementDecl( String ns, String localName ) {
        XSSchema schema = getSchema(ns);
        if(schema==null)    return null;

        return schema.getElementDecl(localName);
    }

    public XSAttributeDecl getAttributeDecl( String ns, String localName ) {
        XSSchema schema = getSchema(ns);
        if(schema==null)    return null;

        return schema.getAttributeDecl(localName);
    }

    public XSModelGroupDecl getModelGroupDecl( String ns, String localName ) {
        XSSchema schema = getSchema(ns);
        if(schema==null)    return null;

        return schema.getModelGroupDecl(localName);
    }

    public XSAttGroupDecl getAttGroupDecl( String ns, String localName ) {
        XSSchema schema = getSchema(ns);
        if(schema==null)    return null;

        return schema.getAttGroupDecl(localName);
    }

    public XSComplexType getComplexType( String ns, String localName ) {
        XSSchema schema = getSchema(ns);
        if(schema==null)    return null;

        return schema.getComplexType(localName);
    }

    public XSIdentityConstraint getIdentityConstraint(String ns, String localName) {
        XSSchema schema = getSchema(ns);
        if(schema==null)    return null;

        return schema.getIdentityConstraint(localName);
    }

    private abstract class MultiSchemaIterator<T> implements Iterator<T> {
        private Iterator<XSSchema> sitr = iterateSchema();
        private Iterator<T> citr = null;
        /** The object to be returned from the next method. */
        private T next;

        public void remove() { throw new UnsupportedOperationException(); }
        public boolean hasNext() {
            getNext();
            return next!=null;
        }
        public T next() {
            getNext();
            T r = next;
            next = null;
            return r;
        }
        private void getNext() {
            if(next!=null)  return;

            if(citr!=null && citr.hasNext()) {
                next = citr.next();
                return;
            }
            // citr is empty
            if(sitr.hasNext()) {
                citr = nextIterator(sitr.next());
                getNext();
            }
            // else
            //      no more object
        }

        protected abstract Iterator<T> nextIterator( XSSchema s );
    }

    public Iterator iterateElementDecls() {
        return new MultiSchemaIterator() {
            protected Iterator nextIterator( XSSchema xs ) {
                return xs.iterateElementDecls();
            }
        };
    }

    public Iterator iterateTypes() {
        return new MultiSchemaIterator() {
            protected Iterator nextIterator( XSSchema xs ) {
                return xs.iterateTypes();
            }
        };
    }

    public Iterator iterateAttributeDecls() {
        return new MultiSchemaIterator() {
            protected Iterator nextIterator( XSSchema xs ) {
                return xs.iterateAttributeDecls();
            }
        };
    }
    public Iterator iterateAttGroupDecls() {
        return new MultiSchemaIterator() {
            protected Iterator nextIterator( XSSchema xs ) {
                return xs.iterateAttGroupDecls();
            }
        };
    }
    public Iterator iterateModelGroupDecls() {
        return new MultiSchemaIterator() {
            protected Iterator nextIterator( XSSchema xs ) {
                return xs.iterateModelGroupDecls();
            }
        };
    }
    public Iterator iterateSimpleTypes() {
        return new MultiSchemaIterator() {
            protected Iterator nextIterator( XSSchema xs ) {
                return xs.iterateSimpleTypes();
            }
        };
    }
    public Iterator iterateComplexTypes() {
        return new MultiSchemaIterator() {
            protected Iterator nextIterator( XSSchema xs ) {
                return xs.iterateComplexTypes();
            }
        };
    }
    public Iterator iterateNotations() {
        return new MultiSchemaIterator() {
            protected Iterator nextIterator( XSSchema xs ) {
                return xs.iterateNotations();
            }
        };
    }

    public Iterator<XSIdentityConstraint> iterateIdentityConstraints() {
        return new MultiSchemaIterator() {
            protected Iterator nextIterator( XSSchema xs ) {
                return xs.getIdentityConstraints().values().iterator();
            }
        };
    }


    public final EmptyImpl empty = new EmptyImpl();
    public XSContentType getEmpty() { return empty; }

    public XSSimpleType getAnySimpleType() { return anySimpleType; }
    public final AnySimpleType anySimpleType = new AnySimpleType();
    private class AnySimpleType extends DeclarationImpl
        implements XSRestrictionSimpleType, Ref.SimpleType {

        AnySimpleType() {
            super(null,null,null,null,"http://www.w3.org/2001/XMLSchema","anySimpleType",false);
        }
        public SchemaImpl getOwnerSchema() {
            return createSchema("http://www.w3.org/2001/XMLSchema",null);
        }
        public XSSimpleType asSimpleType() { return this; }
        public XSComplexType asComplexType() { return null; }

        public boolean isDerivedFrom(XSType t) {
            return t==this || t==anyType;
        }

        public boolean isSimpleType()       { return true; }
        public boolean isComplexType()      { return false; }
        public XSContentType asEmpty() { return null; }
        public XSParticle asParticle() { return null; }
        public XSType getBaseType() { return anyType; }
        public XSSimpleType getSimpleBaseType() { return null; }
        public int getDerivationMethod() { return RESTRICTION; }
        public Iterator iterateDeclaredFacets() { return emptyIterator; }
        public Collection<? extends XSFacet> getDeclaredFacets() { return Collections.EMPTY_LIST; }
        public void visit( XSSimpleTypeVisitor visitor ) {visitor.restrictionSimpleType(this); }
        public void visit( XSContentTypeVisitor visitor ) {visitor.simpleType(this); }
        public void visit( XSVisitor visitor ) {visitor.simpleType(this); }
        public <T> T apply( XSSimpleTypeFunction<T> f ) {return f.restrictionSimpleType(this); }
        public <T> T apply( XSContentTypeFunction<T> f ) { return f.simpleType(this); }
        public <T> T apply( XSFunction<T> f ) { return f.simpleType(this); }
        public XSVariety getVariety() { return XSVariety.ATOMIC; }
        public XSFacet getFacet(String name) { return null; }
        public XSFacet getDeclaredFacet(String name) { return null; }
        public List<XSFacet> getDeclaredFacets(String name) { return Collections.EMPTY_LIST; }

        public boolean isRestriction() { return true; }
        public boolean isList() { return false; }
        public boolean isUnion() { return false; }
        public boolean isFinal(XSVariety v) { return false; }
        public XSRestrictionSimpleType asRestriction() { return this; }
        public XSListSimpleType asList() { return null; }
        public XSUnionSimpleType asUnion() { return null; }
        public XSSimpleType getType() { return this; } // Ref.SimpleType implementation
        public XSSimpleType getRedefinedBy() { return null; }
        public int getRedefinedCount() { return 0; }

        public XSType[] listSubstitutables() {
            return Util.listSubstitutables(this);
        }
    };

    public XSComplexType getAnyType() { return anyType; }
    public final AnyType anyType = new AnyType();
    private class AnyType extends DeclarationImpl implements XSComplexType, Ref.Type {
        AnyType() {
            super(null,null,null,null,"http://www.w3.org/2001/XMLSchema","anyType",false);
        }
        public SchemaImpl getOwnerSchema() {
            return createSchema("http://www.w3.org/2001/XMLSchema",null);
        }
        public boolean isAbstract() { return false; }
        public XSWildcard getAttributeWildcard() { return anyWildcard; }
        public XSAttributeUse getAttributeUse( String nsURI, String localName ) { return null; }
        public Iterator iterateAttributeUses() { return emptyIterator; }
        public XSAttributeUse getDeclaredAttributeUse( String nsURI, String localName ) { return null; }
        public Iterator iterateDeclaredAttributeUses() { return emptyIterator; }
        public Iterator iterateAttGroups() { return emptyIterator; }
        public Collection<XSAttributeUse> getAttributeUses() { return Collections.EMPTY_LIST; }
        public Collection<? extends XSAttributeUse> getDeclaredAttributeUses() { return Collections.EMPTY_LIST; }
        public Collection<? extends XSAttGroupDecl> getAttGroups() { return Collections.EMPTY_LIST; }
        public boolean isFinal( int i ) { return false; }
        public boolean isSubstitutionProhibited( int i ) { return false; }
        public boolean isMixed() { return true; }
        public XSContentType getContentType() { return contentType; }
        public XSContentType getExplicitContent() { return null; }
        public XSType getBaseType() { return this; }
        public XSSimpleType asSimpleType() { return null; }
        public XSComplexType asComplexType() { return this; }

        public boolean isDerivedFrom(XSType t) {
            return t==this;
        }

        public boolean isSimpleType()       { return false; }
        public boolean isComplexType()      { return true; }
        public XSContentType asEmpty() { return null; }
        public int getDerivationMethod() { return XSType.RESTRICTION; }

        public XSElementDecl getScope() { return null; }
        public void visit( XSVisitor visitor ) { visitor.complexType(this); }
        public <T> T apply( XSFunction<T> f ) { return f.complexType(this); }

        public XSType getType() { return this; } // Ref.Type implementation

        public XSComplexType getRedefinedBy() { return null; }
        public int getRedefinedCount() { return 0; }

        public XSType[] listSubstitutables() {
            return Util.listSubstitutables(this);
        }

        private final WildcardImpl anyWildcard = new WildcardImpl.Any(null,null,null,null,XSWildcard.SKIP);
        private final XSContentType contentType = new ParticleImpl( null, null,
                new ModelGroupImpl(null, null, null, null,XSModelGroup.SEQUENCE, new ParticleImpl[]{
                    new ParticleImpl( null, null,
                        anyWildcard, null,
                        XSParticle.UNBOUNDED, 0 )
                })
                ,null,1,1);
    };

    private static final Iterator emptyIterator = new Iterator() {
        public boolean hasNext() { return false; }
        public Object next() { throw new NoSuchElementException(); }
        public void remove() { throw new UnsupportedOperationException(); }
    };

}
