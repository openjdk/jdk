/*
 * Copyright (c) 1995, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * This exception indicates an internal failure in an ORB, for
 * example, if an ORB has detected corruption of its internal
 * data structures.<P>
 * It contains a minor code, which gives more detailed information about
 * what caused the exception, and a completion status. It may also contain
 * a string describing the exception.
 * <P>
 * See the section {@extLink jidlexception_minorcodes meaning of minor codes}
 * to see the minor codes for this exception.
 *
 * <p>See also {@extLink jidlexception documentation on Java&nbsp;IDL exceptions}.
 * </p>
 * @since       JDK1.2
 */

public final class INTERNAL extends SystemException {
    /**
     * Constructs an <code>INTERNAL</code> exception with a default
     * minor code of 0 and a completion state of COMPLETED_NO.
     */
    public INTERNAL() {
        this("");
    }

    /**
     * Constructs an <code>INTERNAL</code> exception with the specified detail
     * message, a minor code of 0, and a completion state of COMPLETED_NO.
     * @param s the String containing a detail message
     */
    public INTERNAL(String s) {
        this(s, 0, CompletionStatus.COMPLETED_NO);
    }

    /**
     * Constructs an <code>INTERNAL</code> exception with the specified
     * minor code and completion status.
     * @param minor the minor code
     * @param completed an instance of <code>CompletionStatus</code>
     *                  that indicates the completion status of the method
     *                  that threw this exception
     */
    public INTERNAL(int minor, CompletionStatus completed) {
        this("", minor, completed);
    }

    /**
     * Constructs an <code>INTERNAL</code> exception with the specified detail
     * message, minor code, and completion status.
     * A detail message is a String that describes this particular exception.
     * @param s the String containing a detail message
     * @param minor the minor code
     * @param completed an instance of <code>CompletionStatus</code>
     *                  that indicates the completion status of the method
     *                  that threw this exception
     */
    public INTERNAL(String s, int minor, CompletionStatus completed) {
        super(s, minor, completed);
    }
}
