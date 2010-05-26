/*
 * Copyright (c) 1995, 2006, Oracle and/or its affiliates. All rights reserved.
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

package org.omg.CORBA;

/**
 * The root class for CORBA IDL-defined user exceptions.
 * All CORBA user exceptions are checked exceptions, which
 * means that they need to
 * be declared in method signatures.
 *
 * @see <A href="../../../../technotes/guides/idl/jidlExceptions.html">documentation on
 * Java&nbsp;IDL exceptions</A>
 */
public abstract class UserException extends java.lang.Exception implements org.omg.CORBA.portable.IDLEntity {

    /**
     * Constructs a <code>UserException</code> object.
     * This method is called only by subclasses.
     */
    protected UserException() {
        super();
    }

    /**
     * Constructs a <code>UserException</code> object with a
     * detail message. This method is called only by subclasses.
     *
     * @param reason a <code>String</code> object giving the reason for this
     *         exception
     */
    protected UserException(String reason) {
        super(reason);
    }
}
