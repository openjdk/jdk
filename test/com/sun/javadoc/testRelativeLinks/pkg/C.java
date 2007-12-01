/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package pkg;

/**
 * Here is a relative link in a class:
 * <a href="relative-class-link.html">relative class link</a>.
 */
public class C {

    /**
     * Here is a relative link in a field:
     * <a href="relative-field-link.html">relative field link</a>.
     */
    public C field = null;

    /**
     * Here is a relative link in a method:
     * <a href="relative-method-link.html">relative method link</a>.
     */
    public C method() { return null;}

    /**
     * Here is a relative link in a method:
     * <a
     * href="relative-multi-line-link.html">relative-multi-line-link</a>.
     */
    public C multipleLineTest() { return null;}

}
