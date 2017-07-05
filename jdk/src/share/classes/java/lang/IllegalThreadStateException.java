/*
 * Copyright (c) 1994, 2008, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

/**
 * Thrown to indicate that a thread is not in an appropriate state
 * for the requested operation. See, for example, the
 * <code>suspend</code> and <code>resume</code> methods in class
 * <code>Thread</code>.
 *
 * @author  unascribed
 * @see     java.lang.Thread#resume()
 * @see     java.lang.Thread#suspend()
 * @since   JDK1.0
 */
public class IllegalThreadStateException extends IllegalArgumentException {
    private static final long serialVersionUID = -7626246362397460174L;

    /**
     * Constructs an <code>IllegalThreadStateException</code> with no
     * detail message.
     */
    public IllegalThreadStateException() {
        super();
    }

    /**
     * Constructs an <code>IllegalThreadStateException</code> with the
     * specified detail message.
     *
     * @param   s   the detail message.
     */
    public IllegalThreadStateException(String s) {
        super(s);
    }
}
