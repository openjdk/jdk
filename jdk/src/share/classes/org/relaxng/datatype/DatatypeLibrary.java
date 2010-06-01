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

package org.relaxng.datatype;

/**
 * A Datatype library
 *
 * @author <a href="mailto:jjc@jclark.com">James Clark</a>
 * @author <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
public interface DatatypeLibrary {

        /**
         * Creates a new instance of DatatypeBuilder.
         *
         * The callee should throw a DatatypeException in case of an error.
         *
         * @param baseTypeLocalName
         *              The local name of the base type.
         *
         * @return
         *              A non-null valid datatype object.
         */
        DatatypeBuilder createDatatypeBuilder( String baseTypeLocalName )
                throws DatatypeException;

        /**
         * Gets or creates a pre-defined type.
         *
         * This is just a short-cut of
         * <code>createDatatypeBuilder(typeLocalName).createDatatype();</code>
         *
         * The callee should throw a DatatypeException in case of an error.
         *
         * @return
         *              A non-null valid datatype object.
         */
        Datatype createDatatype( String typeLocalName ) throws DatatypeException;
}
