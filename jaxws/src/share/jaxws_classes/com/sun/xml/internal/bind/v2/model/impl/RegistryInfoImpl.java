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

package com.sun.xml.internal.bind.v2.model.impl;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlElementDecl;

import com.sun.xml.internal.bind.v2.model.annotation.Locatable;
import com.sun.xml.internal.bind.v2.model.annotation.MethodLocatable;
import com.sun.xml.internal.bind.v2.model.core.RegistryInfo;
import com.sun.xml.internal.bind.v2.model.core.TypeInfo;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.bind.v2.runtime.IllegalAnnotationException;
import com.sun.xml.internal.bind.v2.runtime.Location;
import com.sun.xml.internal.bind.v2.ContextFactory;

/**
 * Implementation of {@link RegistryInfo}.
 *
 * @author Kohsuke Kawaguchi
 */
// experimenting with shorter type parameters for <T,C,F,M> quadruple.
// the idea is that they show so often that you'd understand the meaning
// without relying on the whole name.
final class RegistryInfoImpl<T,C,F,M> implements Locatable, RegistryInfo<T,C> {

    final C registryClass;
    private final Locatable upstream;
    private final Navigator<T,C,F,M> nav;

    /**
     * Types that are referenced from this registry.
     */
    private final Set<TypeInfo<T,C>> references = new LinkedHashSet<TypeInfo<T,C>>();

    /**
     * Picks up references in this registry to other types.
     */
    RegistryInfoImpl(ModelBuilder<T,C,F,M> builder, Locatable upstream, C registryClass) {
        this.nav = builder.nav;
        this.registryClass = registryClass;
        this.upstream = upstream;
        builder.registries.put(getPackageName(),this);

        if(nav.getDeclaredField(registryClass,ContextFactory.USE_JAXB_PROPERTIES)!=null) {
            // the user is trying to use ObjectFactory that we generate for interfaces,
            // that means he's missing jaxb.properties
            builder.reportError(new IllegalAnnotationException(
                Messages.MISSING_JAXB_PROPERTIES.format(getPackageName()),
                this
            ));
            // looking at members will only add more errors, so just abort now
            return;
        }

        for( M m : nav.getDeclaredMethods(registryClass) ) {
            XmlElementDecl em = builder.reader.getMethodAnnotation(
                XmlElementDecl.class, m, this );

            if(em==null) {
                if(nav.getMethodName(m).startsWith("create")) {
                    // this is a factory method. visit this class
                    references.add(
                        builder.getTypeInfo(nav.getReturnType(m),
                            new MethodLocatable<M>(this,m,nav)));
                }

                continue;
            }

            ElementInfoImpl<T,C,F,M> ei;
            try {
                ei = builder.createElementInfo(this,m);
            } catch (IllegalAnnotationException e) {
                builder.reportError(e);
                continue;   // recover by ignoring this element
            }

            // register this mapping
            // TODO: any chance this could cause a stack overflow (by recursively visiting classes)?
            builder.typeInfoSet.add(ei,builder);
            references.add(ei);
        }
    }

    public Locatable getUpstream() {
        return upstream;
    }

    public Location getLocation() {
        return nav.getClassLocation(registryClass);
    }

    public Set<TypeInfo<T,C>> getReferences() {
        return references;
    }

    /**
     * Gets the name of the package that this registry governs.
     */
    public String getPackageName() {
        return nav.getPackageName(registryClass);
    }

    public C getClazz() {
        return registryClass;
    }
}
