/*
 * Copyright 2000-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.imageio.spi;

/**
 * An optional interface that may be provided by service provider
 * objects that will be registered with a
 * <code>ServiceRegistry</code>.  If this interface is present,
 * notification of registration and deregistration will be performed.
 *
 * @see ServiceRegistry
 *
 */
public interface RegisterableService {

    /**
     * Called when an object implementing this interface is added to
     * the given <code>category</code> of the given
     * <code>registry</code>.  The object may already be registered
     * under another category or categories.
     *
     * @param registry a <code>ServiceRegistry</code> where this
     * object has been registered.
     * @param category a <code>Class</code> object indicating the
     * registry category under which this object has been registered.
     */
    void onRegistration(ServiceRegistry registry, Class<?> category);

    /**
     * Called when an object implementing this interface is removed
     * from the given <code>category</code> of the given
     * <code>registry</code>.  The object may still be registered
     * under another category or categories.
     *
     * @param registry a <code>ServiceRegistry</code> from which this
     * object is being (wholly or partially) deregistered.
     * @param category a <code>Class</code> object indicating the
     * registry category from which this object is being deregistered.
     */
    void onDeregistration(ServiceRegistry registry, Class<?> category);
}
