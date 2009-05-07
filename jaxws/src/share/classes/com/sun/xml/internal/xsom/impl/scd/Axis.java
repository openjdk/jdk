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
package com.sun.xml.internal.xsom.impl.scd;

import com.sun.xml.internal.xsom.XSAttContainer;
import com.sun.xml.internal.xsom.XSAttGroupDecl;
import com.sun.xml.internal.xsom.XSAttributeDecl;
import com.sun.xml.internal.xsom.XSAttributeUse;
import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSComponent;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSFacet;
import com.sun.xml.internal.xsom.XSIdentityConstraint;
import com.sun.xml.internal.xsom.XSListSimpleType;
import com.sun.xml.internal.xsom.XSModelGroup;
import com.sun.xml.internal.xsom.XSModelGroup.Compositor;
import com.sun.xml.internal.xsom.XSModelGroupDecl;
import com.sun.xml.internal.xsom.XSNotation;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSRestrictionSimpleType;
import com.sun.xml.internal.xsom.XSSchema;
import com.sun.xml.internal.xsom.XSSimpleType;
import com.sun.xml.internal.xsom.XSType;
import com.sun.xml.internal.xsom.XSUnionSimpleType;
import com.sun.xml.internal.xsom.XSWildcard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Axis of traversal.
 *
 * @param <T>
 *      The kind of components that this axis may return.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Axis<T extends XSComponent> {
    Iterator<T> iterator(XSComponent contextNode);

    Iterator<T> iterator(Iterator<? extends XSComponent> contextNodes);

    /**
     * Returns true if this is one of the model group axis.
     */
    boolean isModelGroup();


    /**
     * Pseudo-axis that selects all the {@link XSSchema}s in the current set.
     * Used to implement the absolute path expression
     */
    public static final Axis<XSSchema> ROOT = new Axis<XSSchema>() {
        public Iterator<XSSchema> iterator(XSComponent contextNode) {
            return contextNode.getRoot().iterateSchema();
        }

        public Iterator<XSSchema> iterator(Iterator<? extends XSComponent> contextNodes) {
            if(!contextNodes.hasNext())
                return Iterators.empty();
            else
                // this assumes that all current nodes belong to the same owner.
                return iterator(contextNodes.next());
        }

        public boolean isModelGroup() {
            return false;
        }

        public String toString() {
            return "root::";
        }
    };

    /**
     * Pseudo-axis that visits all skipped intermediate steps.
     * Those are:
     * <ol>
     *  <li>complex type reachable from element
     *  <li>model groups
     *  <li>combination of above.
     * </ol>
     */
    public static final Axis<XSComponent> INTERMEDIATE_SKIP = new AbstractAxisImpl<XSComponent>() {
        public Iterator<XSComponent> elementDecl(XSElementDecl decl) {
            XSComplexType ct = decl.getType().asComplexType();
            if(ct==null)
                return empty();
            else {
                // also pick up model groups inside this complex type
                return new Iterators.Union<XSComponent>(singleton(ct),complexType(ct));
            }
        }

        public Iterator<XSComponent> modelGroupDecl(XSModelGroupDecl decl) {
            return descendants(decl.getModelGroup());
        }

        public Iterator<XSComponent> particle(XSParticle particle) {
            return descendants(particle.getTerm().asModelGroup());
        }

        /**
         * Iterate all descendant model groups of the given model group, including itself.
         */
        private Iterator<XSComponent> descendants(XSModelGroup mg) {
            // TODO: write a tree iterator
            // for now, we do it eagerly because I'm lazy
            List<XSComponent> r = new ArrayList<XSComponent>();
            visit(mg,r);
            return r.iterator();
        }

        private void visit(XSModelGroup mg, List<XSComponent> r) {
            // since model groups never form a cycle, no cycle check is needed
            r.add(mg);
            for (XSParticle p : mg) {
                XSModelGroup child = p.getTerm().asModelGroup();
                if(child!=null)
                    visit(child,r);
            }
        }

        public String toString() {
            return "(intermediateSkip)";
        }
    };

    /**
     * All descendants reachable via default axes. Used to implement the "//" semantics.
     *
     * So far the default axes together are guaranteed not to cause any cycle, so
     * no cycle check is needed (if it's needed, the life would be much harder!)
     */
    public static final Axis<XSComponent> DESCENDANTS = new Axis<XSComponent>() {
        public Iterator<XSComponent> iterator(XSComponent contextNode) {
            return new Visitor().iterator(contextNode);
        }
        public Iterator<XSComponent> iterator(Iterator<? extends XSComponent> contextNodes) {
            return new Visitor().iterator(contextNodes);
        }

        public boolean isModelGroup() {
            return false;
        }

        /**
         * Stateful visitor that remembers what's already traversed, to reduce the search space.
         */
        final class Visitor extends AbstractAxisImpl<XSComponent> {
            private final Set<XSComponent> visited = new HashSet<XSComponent>();

            /**
             * Recursively apply the {@link Axis#DESCENDANTS} axis.
             */
            final class Recursion extends Iterators.Map<XSComponent,XSComponent> {
                public Recursion(Iterator<? extends XSComponent> core) {
                    super(core);
                }

                protected Iterator<XSComponent> apply(XSComponent u) {
                    return DESCENDANTS.iterator(u);
                }
            }
            public Iterator<XSComponent> schema(XSSchema schema) {
                if(visited.add(schema))
                    return ret( schema, new Recursion(schema.iterateElementDecls()));
                else
                    return empty();
            }

            public Iterator<XSComponent> elementDecl(XSElementDecl decl) {
                if(visited.add(decl))
                    return ret(decl, iterator(decl.getType()) );
                else
                    return empty();
            }

            public Iterator<XSComponent> simpleType(XSSimpleType type) {
                if(visited.add(type))
                    return ret(type, FACET.iterator(type));
                else
                    return empty();
            }

            public Iterator<XSComponent> complexType(XSComplexType type) {
                if(visited.add(type))
                    return ret(type, iterator(type.getContentType()));
                else
                    return empty();
            }

            public Iterator<XSComponent> particle(XSParticle particle) {
                if(visited.add(particle))
                    return ret(particle, iterator(particle.getTerm()));
                else
                    return empty();
            }

            public Iterator<XSComponent> modelGroupDecl(XSModelGroupDecl decl) {
                if(visited.add(decl))
                    return ret(decl, iterator(decl.getModelGroup()));
                else
                    return empty();
            }

            public Iterator<XSComponent> modelGroup(XSModelGroup group) {
                if(visited.add(group))
                    return ret(group, new Recursion(group.iterator()));
                else
                    return empty();
            }

            public Iterator<XSComponent> attGroupDecl(XSAttGroupDecl decl) {
                if(visited.add(decl))
                    return ret(decl, new Recursion(decl.iterateAttributeUses()));
                else
                    return empty();
            }

            public Iterator<XSComponent> attributeUse(XSAttributeUse use) {
                if(visited.add(use))
                    return ret(use, iterator(use.getDecl()));
                else
                    return empty();
            }

            public Iterator<XSComponent> attributeDecl(XSAttributeDecl decl) {
                if(visited.add(decl))
                    return ret(decl, iterator(decl.getType()));
                else
                    return empty();
            }

            private Iterator<XSComponent> ret( XSComponent one, Iterator<? extends XSComponent> rest ) {
                return union(singleton(one),rest);
            }
        }

        public String toString() {
            return "/";
        }
    };

    public static final Axis<XSSchema> X_SCHEMA = new Axis<XSSchema>() {
        public Iterator<XSSchema> iterator(XSComponent contextNode) {
            return Iterators.singleton(contextNode.getOwnerSchema());
        }

        public Iterator<XSSchema> iterator(Iterator<? extends XSComponent> contextNodes) {
            return new Iterators.Adapter<XSSchema,XSComponent>(contextNodes) {
                protected XSSchema filter(XSComponent u) {
                    return u.getOwnerSchema();
                }
            };
        }

        public boolean isModelGroup() {
            return false;
        }

        public String toString() {
            return "x-schema::";
        }
    };

    public static final Axis<XSElementDecl> SUBSTITUTION_GROUP = new AbstractAxisImpl<XSElementDecl>() {
        public Iterator<XSElementDecl> elementDecl(XSElementDecl decl) {
            return singleton(decl.getSubstAffiliation());
        }

        public String toString() {
            return "substitutionGroup::";
        }
    };

    public static final Axis<XSAttributeDecl> ATTRIBUTE = new AbstractAxisImpl<XSAttributeDecl>() {
        public Iterator<XSAttributeDecl> complexType(XSComplexType type) {
            return attributeHolder(type);
        }

        public Iterator<XSAttributeDecl> attGroupDecl(XSAttGroupDecl decl) {
            return attributeHolder(decl);
        }

        private Iterator<XSAttributeDecl> attributeHolder(final XSAttContainer atts) {
            // TODO: check spec. is this correct?
            return new Iterators.Adapter<XSAttributeDecl,XSAttributeUse>(atts.iterateAttributeUses()) {
                protected XSAttributeDecl filter(XSAttributeUse u) {
                    return u.getDecl();
                }
            };
        }

        public Iterator<XSAttributeDecl> schema(XSSchema schema) {
            return schema.iterateAttributeDecls();
        }

        public String toString() {
            return "@";
        }
    };

    public static final Axis<XSElementDecl> ELEMENT = new AbstractAxisImpl<XSElementDecl>() {
        public Iterator<XSElementDecl> particle(XSParticle particle) {
            return singleton(particle.getTerm().asElementDecl());
        }

        public Iterator<XSElementDecl> schema(XSSchema schema) {
            return schema.iterateElementDecls();
        }

        public Iterator<XSElementDecl> modelGroupDecl(XSModelGroupDecl decl) {
            return modelGroup(decl.getModelGroup());
        }

        //public Iterator<XSElementDecl> modelGroup(XSModelGroup group) {
        //    return new Iterators.Map<XSElementDecl,XSParticle>(group.iterator()) {
        //        protected Iterator<XSElementDecl> apply(XSParticle p) {
        //            return particle(p);
        //        }
        //    };
        //}

        @Override
        public String getName() {
            return "";
        }

        public String toString() {
            return "element::";
        }
    };


    public static final Axis<XSType> TYPE_DEFINITION = new AbstractAxisImpl<XSType>() {
        public Iterator<XSType> schema(XSSchema schema) {
            return schema.iterateTypes();
        }

        public Iterator<XSType> attributeDecl(XSAttributeDecl decl) {
            return singleton(decl.getType());
        }

        public Iterator<XSType> elementDecl(XSElementDecl decl) {
            return singleton(decl.getType());
        }

        public String toString() {
            return "~";
        }
    };

    public static final Axis<XSType> BASETYPE = new AbstractAxisImpl<XSType>() {
        public Iterator<XSType> simpleType(XSSimpleType type) {
            return singleton(type.getBaseType());
        }

        public Iterator<XSType> complexType(XSComplexType type) {
            return singleton(type.getBaseType());
        }

        public String toString() {
            return "baseType::";
        }
    };

    public static final Axis<XSSimpleType> PRIMITIVE_TYPE = new AbstractAxisImpl<XSSimpleType>() {
        public Iterator<XSSimpleType> simpleType(XSSimpleType type) {
            return singleton(type.getPrimitiveType());
        }

        public String toString() {
            return "primitiveType::";
        }
    };

    public static final Axis<XSSimpleType> ITEM_TYPE = new AbstractAxisImpl<XSSimpleType>() {
        public Iterator<XSSimpleType> simpleType(XSSimpleType type) {
            XSListSimpleType baseList = type.getBaseListType();
            if(baseList==null)      return empty();
            return singleton(baseList.getItemType());
        }

        public String toString() {
            return "itemType::";
        }
    };

    public static final Axis<XSSimpleType> MEMBER_TYPE = new AbstractAxisImpl<XSSimpleType>() {
        public Iterator<XSSimpleType> simpleType(XSSimpleType type) {
            XSUnionSimpleType baseUnion = type.getBaseUnionType();
            if(baseUnion ==null)      return empty();
            return baseUnion.iterator();
        }

        public String toString() {
            return "memberType::";
        }
    };

    public static final Axis<XSComponent> SCOPE = new AbstractAxisImpl<XSComponent>() {
        public Iterator<XSComponent> complexType(XSComplexType type) {
            return singleton(type.getScope());
        }
        // TODO: attribute declaration has a scope, too.
        // TODO: element declaration has a scope

        public String toString() {
            return "scope::";
        }
    };

    public static final Axis<XSAttGroupDecl> ATTRIBUTE_GROUP = new AbstractAxisImpl<XSAttGroupDecl>() {
        public Iterator<XSAttGroupDecl> schema(XSSchema schema) {
            return schema.iterateAttGroupDecls();
        }

        public String toString() {
            return "attributeGroup::";
        }
    };

    public static final Axis<XSModelGroupDecl> MODEL_GROUP_DECL = new AbstractAxisImpl<XSModelGroupDecl>() {
        public Iterator<XSModelGroupDecl> schema(XSSchema schema) {
            return schema.iterateModelGroupDecls();
        }

        public Iterator<XSModelGroupDecl> particle(XSParticle particle) {
            return singleton(particle.getTerm().asModelGroupDecl());
        }

        public String toString() {
            return "group::";
        }
    };

    public static final Axis<XSIdentityConstraint> IDENTITY_CONSTRAINT = new AbstractAxisImpl<XSIdentityConstraint>() {
        public Iterator<XSIdentityConstraint> elementDecl(XSElementDecl decl) {
            return decl.getIdentityConstraints().iterator();
        }

        public Iterator<XSIdentityConstraint> schema(XSSchema schema) {
            // TODO: iterate all elements in this schema (local or global!) and its identity constraints
            return super.schema(schema);
        }

        public String toString() {
            return "identityConstraint::";
        }
    };

    public static final Axis<XSIdentityConstraint> REFERENCED_KEY = new AbstractAxisImpl<XSIdentityConstraint>() {
        public Iterator<XSIdentityConstraint> identityConstraint(XSIdentityConstraint decl) {
            return singleton(decl.getReferencedKey());
        }

        public String toString() {
            return "key::";
        }
    };

    public static final Axis<XSNotation> NOTATION = new AbstractAxisImpl<XSNotation>() {
        public Iterator<XSNotation> schema(XSSchema schema) {
            return schema.iterateNotations();
        }

        public String toString() {
            return "notation::";
        }
    };

    public static final Axis<XSWildcard> WILDCARD = new AbstractAxisImpl<XSWildcard>() {
        public Iterator<XSWildcard> particle(XSParticle particle) {
            return singleton(particle.getTerm().asWildcard());
        }

        public String toString() {
            return "any::";
        }
    };

    public static final Axis<XSWildcard> ATTRIBUTE_WILDCARD = new AbstractAxisImpl<XSWildcard>() {
        public Iterator<XSWildcard> complexType(XSComplexType type) {
            return singleton(type.getAttributeWildcard());
        }

        public Iterator<XSWildcard> attGroupDecl(XSAttGroupDecl decl) {
            return singleton(decl.getAttributeWildcard());
        }

        public String toString() {
            return "anyAttribute::";
        }
    };

    public static final Axis<XSFacet> FACET = new AbstractAxisImpl<XSFacet>() {
        public Iterator<XSFacet> simpleType(XSSimpleType type) {
            // TODO: it's not clear if "facets" mean all inherited facets or just declared facets
            XSRestrictionSimpleType r = type.asRestriction();
            if(r!=null)
                return r.iterateDeclaredFacets();
            else
                return empty();
        }

        public String toString() {
            return "facet::";
        }
    };

    public static final Axis<XSModelGroup> MODELGROUP_ALL = new ModelGroupAxis(Compositor.ALL);
    public static final Axis<XSModelGroup> MODELGROUP_CHOICE = new ModelGroupAxis(Compositor.CHOICE);
    public static final Axis<XSModelGroup> MODELGROUP_SEQUENCE = new ModelGroupAxis(Compositor.SEQUENCE);
    public static final Axis<XSModelGroup> MODELGROUP_ANY = new ModelGroupAxis(null);

    static final class ModelGroupAxis extends AbstractAxisImpl<XSModelGroup> {
        private final XSModelGroup.Compositor compositor;

        ModelGroupAxis(Compositor compositor) {
            this.compositor = compositor;
        }

        @Override
        public boolean isModelGroup() {
            return true;
        }

        public Iterator<XSModelGroup> particle(XSParticle particle) {
            return filter(particle.getTerm().asModelGroup());
        }

        public Iterator<XSModelGroup> modelGroupDecl(XSModelGroupDecl decl) {
            return filter(decl.getModelGroup());
        }

        private Iterator<XSModelGroup> filter(XSModelGroup mg) {
            if(mg==null)
                return empty();
            if(mg.getCompositor() == compositor || compositor == null)
                return singleton(mg);
            else
                return empty();
        }

        public String toString() {
            if(compositor==null)
                return "model::*";
            else
                return "model::"+compositor;
        }
    }
}
