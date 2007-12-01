/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.util;

/**
 * Unchecked exception thrown when the format width is a negative value other
 * than <tt>-1</tt> or is otherwise unsupported.
 *
 * @since 1.5
 */
public class IllegalFormatWidthException extends IllegalFormatException {

    private static final long serialVersionUID = 16660902L;

    private int w;

    /**
     * Constructs an instance of this class with the specified width.
     *
     * @param  w
     *         The width
     */
    public IllegalFormatWidthException(int w) {
        this.w = w;
    }

    /**
     * Returns the width
     *
     * @return  The width
     */
    public int getWidth() {
        return w;
    }

    public String getMessage() {
        return Integer.toString(w);
    }
}
