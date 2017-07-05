/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package org.relaxng.datatype;

/**
 * An interface that must be implemented by caller to
 * provide context information that is necessary to
 * perform validation of some Datatypes.
 *
 * @author <a href="mailto:jjc@jclark.com">James Clark</a>
 * @author <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
public interface ValidationContext {

        /**
         * Resolves a namespace prefix to the corresponding namespace URI.
         *
         * This method is used for validating the QName type, for example.
         *
         * <p>
         * If the prefix is "" (empty string), it indicates
         * an unprefixed value. The callee
         * should resolve it as for an unprefixed
         * element, rather than for an unprefixed attribute.
         *
         * <p>
         * If the prefix is "xml", then the callee must resolve
         * this prefix into "http://www.w3.org/XML/1998/namespace",
         * as defined in the XML Namespaces Recommendation.
         *
         * @return
         *              namespace URI of this prefix.
         *              If the specified prefix is not declared,
         *              the implementation must return null.
         */
        String resolveNamespacePrefix( String prefix );

        /**
         * Returns the base URI of the context.  The null string may be returned
         * if no base URI is known.
         */
        String getBaseUri();

        /**
         * Checks if an unparsed entity is declared with the
         * specified name.
         *
         * @return
         *  true
         *              if the DTD has an unparsed entity declaration for
         *              the specified name.
         *  false
         *              otherwise.
         */
        boolean isUnparsedEntity( String entityName );

        /**
         * Checks if a notation is declared with the
         * specified name.
         *
         * @return
         *  true
         *              if the DTD has a notation declaration for the specified name.
         *  false
         *              otherwise.
         */
        boolean isNotation( String notationName );
}
