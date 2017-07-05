/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

package org.relaxng.datatype.helpers;

import org.relaxng.datatype.*;

/**
 * Dummy implementation of {@link DatatypeBuilder}.
 *
 * This implementation can be used for Datatypes which have no parameters.
 * Any attempt to add parameters will be rejected.
 *
 * <p>
 * Typical usage would be:
 * <PRE><XMP>
 * class MyDatatypeLibrary implements DatatypeLibrary {
 *     ....
 *     DatatypeBuilder createDatatypeBuilder( String typeName ) {
 *         return new ParameterleessDatatypeBuilder(createDatatype(typeName));
 *     }
 *     ....
 * }
 * </XMP></PRE>
 *
 * @author <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
public final class ParameterlessDatatypeBuilder implements DatatypeBuilder {

        /** This type object is returned for the derive method. */
        private final Datatype baseType;

        public ParameterlessDatatypeBuilder( Datatype baseType ) {
                this.baseType = baseType;
        }

        public void addParameter( String name, String strValue, ValidationContext context )
                        throws DatatypeException {
                throw new DatatypeException();
        }

        public Datatype createDatatype() throws DatatypeException {
                return baseType;
        }
}
