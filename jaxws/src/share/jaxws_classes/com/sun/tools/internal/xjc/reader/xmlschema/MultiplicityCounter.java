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

package com.sun.tools.internal.xjc.reader.xmlschema;

import java.math.BigInteger;
import com.sun.tools.internal.xjc.model.Multiplicity;
import com.sun.xml.internal.xsom.XSElementDecl;
import com.sun.xml.internal.xsom.XSModelGroup;
import com.sun.xml.internal.xsom.XSModelGroupDecl;
import com.sun.xml.internal.xsom.XSParticle;
import com.sun.xml.internal.xsom.XSWildcard;
import com.sun.xml.internal.xsom.visitor.XSTermFunction;

import static com.sun.tools.internal.xjc.model.Multiplicity.ONE;
import static com.sun.tools.internal.xjc.model.Multiplicity.ZERO;

/**
 * Counts {@link Multiplicity} for a particle/term.
 *
 * @author Kohsuke Kawaguchi
 */
public final class MultiplicityCounter implements XSTermFunction<Multiplicity> {

    public static final MultiplicityCounter theInstance = new MultiplicityCounter();

    private MultiplicityCounter() {}

    public Multiplicity particle( XSParticle p ) {
        Multiplicity m = p.getTerm().apply(this);

        BigInteger max;
        if (m.max==null || (BigInteger.valueOf(XSParticle.UNBOUNDED).equals(p.getMaxOccurs())))
            max=null;
        else
            max=p.getMaxOccurs();

        return Multiplicity.multiply( m, Multiplicity.create(p.getMinOccurs(),max) );
    }

    public Multiplicity wildcard(XSWildcard wc) {
        return ONE;
    }

    public Multiplicity modelGroupDecl(XSModelGroupDecl decl) {
        return modelGroup(decl.getModelGroup());
    }

    public Multiplicity modelGroup(XSModelGroup group) {
        boolean isChoice = group.getCompositor() == XSModelGroup.CHOICE;

        Multiplicity r = ZERO;

        for( XSParticle p : group.getChildren()) {
            Multiplicity m = particle(p);

            if(r==null) {
                r=m;
                continue;
            }
            if(isChoice) {
                r = Multiplicity.choice(r,m);
            } else {
                r = Multiplicity.group(r,m);
            }
        }
        return r;
    }

    public Multiplicity elementDecl(XSElementDecl decl) {
        return ONE;
    }
}
