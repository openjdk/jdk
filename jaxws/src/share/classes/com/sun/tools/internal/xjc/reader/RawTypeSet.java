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
package com.sun.tools.internal.xjc.reader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.activation.MimeType;

import com.sun.tools.internal.xjc.model.CElementPropertyInfo;
import static com.sun.tools.internal.xjc.model.CElementPropertyInfo.CollectionMode.*;
import com.sun.tools.internal.xjc.model.CReferencePropertyInfo;
import com.sun.tools.internal.xjc.model.CTypeRef;
import com.sun.tools.internal.xjc.model.Multiplicity;
import com.sun.tools.internal.xjc.model.nav.NType;
import com.sun.xml.internal.bind.v2.model.core.Element;
import com.sun.xml.internal.bind.v2.model.core.ID;

/**
 * Set of {@link Ref}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class RawTypeSet {


    public final Set<Ref> refs;

    /**
     * True if this type set can form references to types.
     */
    public final Mode canBeTypeRefs;

    /**
     * The occurence of the whole references.
     */
    public final Multiplicity mul;

    // computed inside canBeTypeRefs()
    private CElementPropertyInfo.CollectionMode collectionMode;

    /**
     * Should be called from one of the raw type set builders.
     */
    public RawTypeSet( Set<Ref> refs, Multiplicity m ) {
        this.refs = refs;
        mul = m;
        canBeTypeRefs = canBeTypeRefs();
    }

    public CElementPropertyInfo.CollectionMode getCollectionMode() {
        return collectionMode;
    }

    public boolean isRequired() {
        return mul.min>0;
    }


    /**
     * Represents the possible binding option for this {@link RawTypeSet}.
     */
    public enum Mode {
        /**
         * This {@link RawTypeSet} can be either an reference property or
         * an element property, and XJC recommends element property.
         */
        SHOULD_BE_TYPEREF(0),
        /**
         * This {@link RawTypeSet} can be either an reference property or
         * an element property, and XJC recommends reference property.
         */
        CAN_BE_TYPEREF(1),
        /**
         * This {@link RawTypeSet} can be only bound to a reference property.
         */
        MUST_BE_REFERENCE(2);

        private final int rank;

        Mode(int rank) {
           this.rank = rank;
        }

        Mode or(Mode that) {
            switch(Math.max(this.rank,that.rank)) {
            case 0:     return SHOULD_BE_TYPEREF;
            case 1:     return CAN_BE_TYPEREF;
            case 2:     return MUST_BE_REFERENCE;
            }
            throw new AssertionError();
        }
    }

    /**
     * Returns true if {@link #refs} can form refs of types.
     *
     * If there are multiple {@link Ref}s with the same type,
     * we cannot make them into type refs. Or if any of the {@link Ref}
     * says they cannot be in type refs, we cannot do that either.
     *
     * TODO: just checking if the refs are the same is not suffice.
     * If two refs derive from each other, they cannot form a list of refs
     * (because of a possible ambiguity).
     */
    private Mode canBeTypeRefs() {
        Set<NType> types = new HashSet<NType>();

        collectionMode = mul.isAtMostOnce()?NOT_REPEATED:REPEATED_ELEMENT;

        // the way we compute this is that we start from the most optimistic value,
        // and then gradually degrade as we find something problematic.
        Mode mode = Mode.SHOULD_BE_TYPEREF;

        for( Ref r : refs ) {
            mode = mode.or(r.canBeType(this));
            if(mode== Mode.MUST_BE_REFERENCE)
                return mode;    // no need to continue the processing

            if(!types.add(r.toTypeRef(null).getTarget().getType()))
                return Mode.MUST_BE_REFERENCE;   // collision
            if(r.isListOfValues()) {
                if(refs.size()>1 || !mul.isAtMostOnce())
                    return Mode.MUST_BE_REFERENCE;   // restriction on @XmlList
                collectionMode = REPEATED_VALUE;
            }
        }
        return mode;
    }




    public void addTo(CElementPropertyInfo prop) {
        assert canBeTypeRefs!= Mode.MUST_BE_REFERENCE;
        if(mul.isZero())
            return; // the property can't have any value

        List<CTypeRef> dst = prop.getTypes();
        for( Ref t : refs )
            dst.add(t.toTypeRef(prop));
    }

    public void addTo(CReferencePropertyInfo prop) {
        if(mul.isZero())
            return; // the property can't have any value
        for( Ref t : refs )
            t.toElementRef(prop);
    }

    public ID id() {
        for( Ref t : refs ) {
            ID id = t.id();
            if(id!=ID.NONE)    return id;
        }
        return ID.NONE;
    }

    public MimeType getExpectedMimeType() {
        for( Ref t : refs ) {
            MimeType mt = t.getExpectedMimeType();
            if(mt!=null)    return mt;
        }
        return null;
    }


    /**
     * A reference to something.
     *
     * <p>
     * A {@link Ref} can be either turned into {@link CTypeRef} to form
     * an element property, or {@link Element} to form a reference property.
     */
    public static abstract class Ref {
        /**
         * @param ep
         *      the property to which the returned {@link CTypeRef} will be
         *      added to.
         */
        protected abstract CTypeRef toTypeRef(CElementPropertyInfo ep);
        protected abstract void toElementRef(CReferencePropertyInfo prop);
        /**
         * Can this {@link Ref} be a type ref?
         * @return false to veto.
         * @param parent
         */
        protected abstract Mode canBeType(RawTypeSet parent);
        protected abstract boolean isListOfValues();
        /**
         * When this {@link RawTypeSet} binds to a {@link CElementPropertyInfo},
         * this method is used to determine if the property is ID or not.
         */
        protected abstract ID id();

        /**
         * When this {@link RawTypeSet} binds to a {@link CElementPropertyInfo},
         * this method is used to determine if the property has an associated expected MIME type or not.
         */
        protected MimeType getExpectedMimeType() { return null; }
    }
}
