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
 * Creates a user-defined type by adding parameters to
 * the pre-defined type.
 *
 * @author <a href="mailto:jjc@jclark.com">James Clark</a>
 * @author <a href="mailto:kohsuke.kawaguchi@sun.com">Kohsuke KAWAGUCHI</a>
 */
public interface DatatypeBuilder {

        /**
         * Adds a new parameter.
         *
         * @param name
         *              The name of the parameter to be added.
         * @param strValue
         *              The raw value of the parameter. Caller may not normalize
         *              this value because any white space is potentially significant.
         * @param context
         *              The context information which can be used by the callee to
         *              acquire additional information. This context object is
         *              valid only during this method call. The callee may not
         *              keep a reference to this object.
         * @exception   DatatypeException
         *              When the given parameter is inappropriate for some reason.
         *              The callee is responsible to recover from this error.
         *              That is, the object should behave as if no such error
         *              was occured.
         */
        void addParameter( String name, String strValue, ValidationContext context )
                throws DatatypeException;

        /**
         * Derives a new Datatype from a Datatype by parameters that
         * were already set through the addParameter method.
         *
         * @exception DatatypeException
         *              DatatypeException must be thrown if the derivation is
         *              somehow invalid. For example, a required parameter is missing,
         *              etc. The exception should contain a diagnosis message
         *              if possible.
         */
        Datatype createDatatype() throws DatatypeException;
}
