/*
 * Copyright 1995-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.lang;

/**
 * Thrown if an application tries to access or modify a specified
 * field of an object, and that object no longer has that field.
 * <p>
 * Normally, this error is caught by the compiler; this error can
 * only occur at run time if the definition of a class has
 * incompatibly changed.
 *
 * @author  unascribed
 * @since   JDK1.0
 */
public
class NoSuchFieldError extends IncompatibleClassChangeError {
    private static final long serialVersionUID = -3456430195886129035L;

    /**
     * Constructs a <code>NoSuchFieldException</code> with no detail  message.
     */
    public NoSuchFieldError() {
        super();
    }

    /**
     * Constructs a <code>NoSuchFieldException</code> with the specified
     * detail message.
     *
     * @param   s   the detail message.
     */
    public NoSuchFieldError(String s) {
        super(s);
    }
}
