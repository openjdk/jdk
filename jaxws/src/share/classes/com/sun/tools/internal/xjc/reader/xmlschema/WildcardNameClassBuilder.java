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
package com.sun.tools.internal.xjc.reader.xmlschema;

import java.util.Iterator;

import com.sun.xml.internal.xsom.XSWildcard;
import com.sun.xml.internal.xsom.visitor.XSWildcardFunction;

import com.sun.xml.internal.rngom.nc.AnyNameExceptNameClass;
import com.sun.xml.internal.rngom.nc.ChoiceNameClass;
import com.sun.xml.internal.rngom.nc.NameClass;
import com.sun.xml.internal.rngom.nc.NsNameClass;

/**
 * Builds a name class representation of a wildcard.
 *
 * <p>
 * Singleton. Use the build method to create a NameClass.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public final class WildcardNameClassBuilder implements XSWildcardFunction<NameClass> {
    private WildcardNameClassBuilder() {}

    private static final XSWildcardFunction<NameClass> theInstance =
        new WildcardNameClassBuilder();

    public static NameClass build( XSWildcard wc ) {
        return wc.apply(theInstance);
    }

    public NameClass any(XSWildcard.Any wc) {
        return NameClass.ANY;
    }

    public NameClass other(XSWildcard.Other wc) {
        return new AnyNameExceptNameClass(
            new ChoiceNameClass(
                new NsNameClass(""),
                new NsNameClass(wc.getOtherNamespace())));
    }

    public NameClass union(XSWildcard.Union wc) {
        NameClass nc = null;
        for (Iterator itr = wc.iterateNamespaces(); itr.hasNext();) {
            String ns = (String) itr.next();

            if(nc==null)    nc = new NsNameClass(ns);
            else
                nc = new ChoiceNameClass(nc,new NsNameClass(ns));
        }

        // there should be at least one.
        assert nc!=null;

        return nc;
    }

}
