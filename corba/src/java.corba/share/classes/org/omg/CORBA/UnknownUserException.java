/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * A class that contains user exceptions returned by the server.
 * When the client uses the DII to make an invocation, any user exception
 * returned from the server is enclosed in an <code>Any</code> object contained in the
 * <code>UnknownUserException</code> object. This is available from the
 * <code>Environment</code> object returned by the method <code>Request.env</code>.
 *
 * @see <A href="../../../../technotes/guides/idl/jidlExceptions.html">documentation on
 * Java&nbsp;IDL exceptions</A>
 * @see Request
 */

public final class UnknownUserException extends UserException {

    /** The <code>Any</code> instance that contains the actual user exception thrown
     *  by the server.
     * @serial
     */
    public Any except;

    /**
     * Constructs an <code>UnknownUserException</code> object.
     */
    public UnknownUserException() {
        super();
    }

    /**
     * Constructs an <code>UnknownUserException</code> object that contains the given
     * <code>Any</code> object.
     *
     * @param a an <code>Any</code> object that contains a user exception returned
     *         by the server
     */
    public UnknownUserException(Any a) {
        super();
        except = a;
    }
}
