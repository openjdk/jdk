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
package com.sun.xml.internal.rngom.dt.builtin;

import org.relaxng.datatype.DatatypeLibrary;
import org.relaxng.datatype.DatatypeLibraryFactory;

import com.sun.xml.internal.rngom.xml.util.WellKnownNamespaces;

/**
 * {@link DatatypeLibraryFactory} for
 * RELAX NG Built-in datatype library and compatibility datatype library.
 */
public class BuiltinDatatypeLibraryFactory implements DatatypeLibraryFactory {
    private final DatatypeLibrary builtinDatatypeLibrary;
    private final DatatypeLibrary compatibilityDatatypeLibrary;
    /**
     * Target of delegation.
     */
    private final DatatypeLibraryFactory core;

    public BuiltinDatatypeLibraryFactory( DatatypeLibraryFactory coreFactory ) {
        builtinDatatypeLibrary = new BuiltinDatatypeLibrary(coreFactory);
        compatibilityDatatypeLibrary = new CompatibilityDatatypeLibrary(coreFactory);
        this.core = coreFactory;
    }

    public DatatypeLibrary createDatatypeLibrary(String uri) {
        if (uri.equals(""))
            return builtinDatatypeLibrary;
        if (uri.equals(WellKnownNamespaces.RELAX_NG_COMPATIBILITY_DATATYPES))
            return compatibilityDatatypeLibrary;
        return core.createDatatypeLibrary(uri);
    }
}
