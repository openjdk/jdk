/*
 * Copyright 1998-1999 Sun Microsystems, Inc.  All Rights Reserved.
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
/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package org.omg.CORBA.portable;
/**
 * The org.omg.CORBA.portable.UnknownException is used for reporting
 * unknown exceptions between ties and ORBs and between ORBs and stubs.
 * It provides a Java representation of an UNKNOWN system exception
 * that has an UnknownExceptionInfo service context.
 * If the CORBA system exception org.omg.CORBA.portable.UnknownException
 * is thrown, then the stub does one of the following:
 * (1) Translates it to org.omg.CORBA.UNKNOWN.
 * (2) Translates it to the nested exception that the UnknownException contains.
 * (3) Passes it on directly to the user.
 */
public class UnknownException extends org.omg.CORBA.SystemException {
    /**
     * A throwable--the original exception that was wrapped in a CORBA
     * UnknownException.
     */
    public Throwable originalEx;
    /**
     * Constructs an UnknownException object.
     * @param ex a Throwable object--to be wrapped in this exception.
     */
    public UnknownException(Throwable ex) {
        super("", 0, org.omg.CORBA.CompletionStatus.COMPLETED_MAYBE);
        originalEx = ex;
    }
}
