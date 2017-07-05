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

package org.omg.CORBA.portable;

/**
This class is used for reporting application level exceptions between ORBs and stubs.
*/

public class ApplicationException extends Exception {
    /**
     * Constructs an ApplicationException from the CORBA repository ID of the exception
     * and an input stream from which the exception data can be read as its parameters.
     * @param id the repository id of the user exception
     * @param ins the stream which contains the user exception data
     */
    public ApplicationException(String id,
                                InputStream ins) {
        this.id = id;
        this.ins = ins;
    }

    /**
     * Returns the CORBA repository ID of the exception
     * without removing it from the exceptions input stream.
     * @return The CORBA repository ID of this exception
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the input stream from which the exception data can be read as its parameters.
     * @return The stream which contains the user exception data
     */
    public InputStream getInputStream() {
        return ins;
    }

    private String id;
    private InputStream ins;
}
