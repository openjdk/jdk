/*
 * Copyright 1996-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.io;

/**
 * Thrown when the Serialization runtime detects one of the following
 * problems with a Class.
 * <UL>
 * <LI> The serial version of the class does not match that of the class
 *      descriptor read from the stream
 * <LI> The class contains unknown datatypes
 * <LI> The class does not have an accessible no-arg constructor
 * </UL>
 *
 * @author  unascribed
 * @since   JDK1.1
 */
public class InvalidClassException extends ObjectStreamException {

    private static final long serialVersionUID = -4333316296251054416L;

    /**
     * Name of the invalid class.
     *
     * @serial Name of the invalid class.
     */
    public String classname;

    /**
     * Report an InvalidClassException for the reason specified.
     *
     * @param reason  String describing the reason for the exception.
     */
    public InvalidClassException(String reason) {
        super(reason);
    }

    /**
     * Constructs an InvalidClassException object.
     *
     * @param cname   a String naming the invalid class.
     * @param reason  a String describing the reason for the exception.
     */
    public InvalidClassException(String cname, String reason) {
        super(reason);
        classname = cname;
    }

    /**
     * Produce the message and include the classname, if present.
     */
    public String getMessage() {
        if (classname == null)
            return super.getMessage();
        else
            return classname + "; " + super.getMessage();
    }
}
