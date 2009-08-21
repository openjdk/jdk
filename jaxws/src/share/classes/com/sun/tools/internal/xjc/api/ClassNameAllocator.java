/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.xjc.api;

/**
 * Callback interface that allows the driver of the XJC API
 * to rename JAXB-generated classes/interfaces/enums.
 *
 * @author Kohsuke Kawaguchi
 */
public interface ClassNameAllocator {
    /**
     * Hook that allows the client of the XJC API to rename some of the JAXB-generated classes.
     *
     * <p>
     * When registered, this calllbcak is consulted for every package-level
     * classes/interfaces/enums (hereafter, simply "classes")
     * that the JAXB RI generates. Note that
     * the JAXB RI does not use this allocator for nested/inner classes.
     *
     * <p>
     * If the allocator chooses to rename some classes. It is
     * the allocator's responsibility to find unique names.
     * If the returned name collides with other classes, the JAXB RI will
     * report errors.
     *
     * @param packageName
     *      The package name, such as "" or "foo.bar". Never be null.
     * @param className
     *      The short name of the proposed class name. Such as
     *      "Foo" or "Bar". Never be null, never be empty.
     *      Always a valid Java identifier.
     *
     * @return
     *      The short name of the class name that should be used.
     *      The class will be generated into the same package with this name.
     *      The return value must be a valid Java identifier. May not be null.
     */
    String assignClassName( String packageName, String className );
}
