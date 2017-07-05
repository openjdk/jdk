/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jdi;

import java.util.List;

/**
 * A mirror of an interface in the target VM. An InterfaceType is
 * a refinement of {@link ReferenceType} that applies to true interfaces
 * in the JLS  sense of the definition (not a class, not an array type).
 * An interface type will never be returned by
 * {@link ObjectReference#referenceType}, but it may be in the list
 * of implemented interfaces for a {@link ClassType} that is returned
 * by that method.
 *
 * @see ObjectReference
 *
 * @author Robert Field
 * @author Gordon Hirsch
 * @author James McIlree
 * @since  1.3
 */
@jdk.Exported
public interface InterfaceType extends ReferenceType {
    /**
     * Gets the interfaces directly extended by this interface.
     * The returned list contains only those interfaces this
     * interface has declared to be extended.
     *
     * @return a List of {@link InterfaceType} objects each mirroring
     * an interface extended by this interface.
     * If none exist, returns a zero length List.
     * @throws ClassNotPreparedException if this class not yet been
     * prepared.
     */
    List<InterfaceType> superinterfaces();

    /**
     * Gets the currently prepared interfaces which directly extend this
     * interface. The returned list contains only those interfaces that
     * declared this interface in their "extends" clause.
     *
     * @return a List of {@link InterfaceType} objects each mirroring
     * an interface extending this interface.
     * If none exist, returns a zero length List.
     */
    List<InterfaceType> subinterfaces();

    /**
     * Gets the currently prepared classes which directly implement this
     * interface. The returned list contains only those classes that
     * declared this interface in their "implements" clause.
     *
     * @return a List of {@link ClassType} objects each mirroring
     * a class implementing this interface.
     * If none exist, returns a zero length List.
     */
    List<ClassType> implementors();
}
