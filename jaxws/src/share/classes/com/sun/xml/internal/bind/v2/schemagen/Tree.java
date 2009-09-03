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

package com.sun.xml.internal.bind.v2.schemagen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sun.xml.internal.bind.v2.schemagen.xmlschema.ContentModelContainer;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.Particle;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.TypeDefParticle;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.Occurs;

/**
 * Normalized representation of the content model.
 *
 * <p>
 * This is built from bottom up so that we can eliminate redundant constructs,
 * and produce the most concise content model definition in XML.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class Tree {

    /**
     * Returns "T?" from "T".
     *
     * @param really
     *      if false this method becomes no-op. This is so that we can write
     *      the caller fluently.
     */
    Tree makeOptional(boolean really) {
        return really?new Optional(this) :this;
    }

    /**
     * Returns "T+" from "T".
     *
     * @param really
     *      if false this method becomes no-op. This is so that we can write
     *      the caller fluently.
     */
    Tree makeRepeated(boolean really) {
        return really?new Repeated(this) :this;
    }

    /**
     * Returns a group tree.
     */
    static Tree makeGroup(GroupKind kind, List<Tree> children ) {
        // pointless binary operator.
        if(children.size()==1)
            return children.get(0);

        // we neither have epsilon or emptySet, so can't handle children.length==0 nicely

        // eliminated nesting groups of the same kind.
        // this is where binary tree would have shined.
        List<Tree> normalizedChildren = new ArrayList<Tree>(children.size());
        for (Tree t : children) {
            if (t instanceof Group) {
                Group g = (Group) t;
                if(g.kind==kind) {
                    normalizedChildren.addAll(Arrays.asList(g.children));
                    continue;
                }
            }
            normalizedChildren.add(t);
        }

        return new Group(kind,normalizedChildren.toArray(new Tree[normalizedChildren.size()]));
    }

    /**
     * Returns true if this tree accepts empty sequence.
     */
    abstract boolean isNullable();

    /**
     * Returns true if the top node of this tree can
     * appear as a valid top-level content model in XML Schema.
     *
     * <p>
     * Model groups and occurrences that have model group in it can.
     */
    boolean canBeTopLevel() { return false; }

    /**
     * Writes out the content model.
     *
     * Normall this runs recursively until we write out the whole content model.
     */
    protected abstract void write(ContentModelContainer parent, boolean isOptional, boolean repeated);

    /**
     * Writes inside the given complex type.
     */
    protected void write(TypeDefParticle ct) {
        if(canBeTopLevel())
            write(ct._cast(ContentModelContainer.class), false, false);
        else
            // need a dummy wrapper
            new Group(GroupKind.SEQUENCE,this).write(ct);
    }

    /**
     * Convenience method to write occurrence constraints.
     */
    protected final void writeOccurs(Occurs o, boolean isOptional, boolean repeated) {
        if(isOptional)
            o.minOccurs(0);
        if(repeated)
            o.maxOccurs("unbounded");
    }

    /**
     * Represents a terminal tree node, such as element, wildcard, etc.
     */
    abstract static class Term extends Tree {
        boolean isNullable() {
            return false;
        }
    }

    /**
     * "T?"
     */
    private static final class Optional extends Tree {
        private final Tree body;

        private Optional(Tree body) {
            this.body = body;
        }

        @Override
        boolean isNullable() {
            return true;
        }

        @Override
        Tree makeOptional(boolean really) {
            return this;
        }

        @Override
        protected void write(ContentModelContainer parent, boolean isOptional, boolean repeated) {
            body.write(parent,true,repeated);
        }
    }

    /**
     * "T+"
     */
    private static final class Repeated extends Tree {
        private final Tree body;

        private Repeated(Tree body) {
            this.body = body;
        }

        @Override
        boolean isNullable() {
            return body.isNullable();
        }

        @Override
        Tree makeRepeated(boolean really) {
            return this;
        }

        @Override
        protected void write(ContentModelContainer parent, boolean isOptional, boolean repeated) {
            body.write(parent,isOptional,true);
        }
    }

    /**
     * "S|T", "S,T", and "S&amp;T".
     */
    private static final class Group extends Tree {
        private final GroupKind kind;
        private final Tree[] children;

        private Group(GroupKind kind, Tree... children) {
            this.kind = kind;
            this.children = children;
        }

        @Override
        boolean canBeTopLevel() {
            return true;
        }

        @Override
        boolean isNullable() {
            if(kind== GroupKind.CHOICE) {
                for (Tree t : children) {
                    if(t.isNullable())
                        return true;
                }
                return false;
            } else {
                for (Tree t : children) {
                    if(!t.isNullable())
                        return false;
                }
                return true;
            }
        }

        @Override
        protected void write(ContentModelContainer parent, boolean isOptional, boolean repeated) {
            Particle c = kind.write(parent);
            writeOccurs(c,isOptional,repeated);

            for (Tree child : children) {
                child.write(c,false,false);
            }
        }
    }
}
