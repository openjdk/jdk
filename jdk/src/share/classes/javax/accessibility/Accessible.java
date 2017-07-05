/*
 * Copyright 1997-1999 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.accessibility;

/**
 * Interface Accessible is the main interface for the accessibility package.
 * All components that support
 * the accessibility package must implement this interface.
 * It contains a single method, {@link #getAccessibleContext}, which
 * returns an instance of the class {@link AccessibleContext}.
 *
 * @author      Peter Korn
 * @author      Hans Muller
 * @author      Willie Walker
 */
public interface Accessible {

    /**
     * Returns the AccessibleContext associated with this object.  In most
     * cases, the return value should not be null if the object implements
     * interface Accessible.  If a component developer creates a subclass
     * of an object that implements Accessible, and that subclass
     * is not Accessible, the developer should override the
     * getAccessibleContext method to return null.
     */
    public AccessibleContext getAccessibleContext();
}
