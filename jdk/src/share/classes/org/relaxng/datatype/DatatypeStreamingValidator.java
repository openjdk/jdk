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
 * Datatype streaming validator.
 *
 * <p>
 * The streaming validator is an optional feature that is useful for
 * certain Datatypes. It allows the caller to incrementally provide
 * the literal.
 *
 * @author <a href="mailto:jjc@jclark.com">James Clark</a>
 * @author <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
public interface DatatypeStreamingValidator {

        /**
         * Passes an additional fragment of the literal.
         *
         * <p>
         * The application can call this method several times, then call
         * the isValid method (or the checkValid method) to check the validity
         * of the accumulated characters.
         */
        void addCharacters( char[] buf, int start, int len );

        /**
         * Tells if the accumulated literal is valid with respect to
         * the underlying Datatype.
         *
         * @return
         *              True if it is valid. False if otherwise.
         */
        boolean isValid();

        /**
         * Similar to the isValid method, but this method throws
         * Exception (with possibly diagnostic information), instead of
         * returning false.
         *
         * @exception DatatypeException
         *              If the callee supports the diagnosis and the accumulated
         *              literal is invalid, then this exception that possibly
         *              contains diagnosis information is thrown.
         */
        void checkValid() throws DatatypeException;
}
