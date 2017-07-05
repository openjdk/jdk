/*
 * Copyright 2000-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.management.openmbean;

/**
 * This runtime exception is thrown to indicate that the index of a row to be added to a <i>tabular data</i> instance
 * is already used to refer to another row in this <i>tabular data</i> instance.
 *
 *
 * @since 1.5
 */
public class KeyAlreadyExistsException extends IllegalArgumentException {

    private static final long serialVersionUID = 1845183636745282866L;

    /**
     * A KeyAlreadyExistsException with no detail message.
     */
    public KeyAlreadyExistsException() {
        super();
    }

    /**
     * A KeyAlreadyExistsException with a detail message.
     *
     * @param msg the detail message.
     */
    public KeyAlreadyExistsException(String msg) {
        super(msg);
    }

}
