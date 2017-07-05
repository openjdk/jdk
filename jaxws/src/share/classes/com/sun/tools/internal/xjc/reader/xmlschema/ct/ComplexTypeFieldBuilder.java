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
package com.sun.tools.internal.xjc.reader.xmlschema.ct;

import java.util.HashMap;
import java.util.Map;

import com.sun.tools.internal.xjc.reader.xmlschema.BGMBuilder;
import com.sun.tools.internal.xjc.reader.xmlschema.BindingComponent;
import com.sun.xml.internal.xsom.XSComplexType;

/**
 * single entry point of building a field expression from a complex type.
 *
 * One object is created for one {@link BGMBuilder}.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class ComplexTypeFieldBuilder extends BindingComponent {

    /**
     * All installed available complex type builders.
     *
     * <p>
     * Builders are tried in this order, to put specific ones first.
     */
    private final CTBuilder[] complexTypeBuilders = new CTBuilder[]{
//        new ChoiceContentComplexTypeBuilder(),
        new MixedExtendedComplexTypeBuilder(),
        new MixedComplexTypeBuilder(),
        new FreshComplexTypeBuilder(),
        new ExtendedComplexTypeBuilder(),
        new RestrictedComplexTypeBuilder(),
        new STDerivedComplexTypeBuilder()
    };

    /** Records ComplexTypeBindingMode for XSComplexType. */
    private final Map<XSComplexType,ComplexTypeBindingMode> complexTypeBindingModes =
        new HashMap<XSComplexType,ComplexTypeBindingMode>();

    /**
     * Binds a complex type to a field expression.
     */
    public void build( XSComplexType type ) {
        for( CTBuilder ctb : complexTypeBuilders )
            if( ctb.isApplicable(type) ) {
                ctb.build(type);
                return;
            }

        assert false; // shall never happen
    }

    /**
     * Records the binding mode of the given complex type.
     *
     * <p>
     * Binding of a derived complex type often depends on that of the
     * base complex type. For example, when a base type is bound to
     * the getRest() method, all the derived complex types will be bound
     * in the same way.
     *
     * <p>
     * For this reason, we have to record how each complex type is being
     * bound.
     */
    public void recordBindingMode( XSComplexType type, ComplexTypeBindingMode flag ) {
        // it is an error to override the flag.
        Object o = complexTypeBindingModes.put(type,flag);
        assert o==null;
    }

    /**
     * Obtains the binding mode recorded through
     * {@link #recordBindingMode(XSComplexType, ComplexTypeBindingMode)}.
     */
    protected ComplexTypeBindingMode getBindingMode( XSComplexType type ) {
        ComplexTypeBindingMode r = complexTypeBindingModes.get(type);
        assert r!=null;
        return r;
    }
}
