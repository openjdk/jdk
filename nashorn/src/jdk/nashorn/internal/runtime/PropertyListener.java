/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

/**
 * Property change listener gets notified whenever properties are added/deleted/modified.
 */
public interface PropertyListener {
    /**
     * A new property is being added.
     *
     * @param object The ScriptObject to which property was added.
     * @param prop The new Property added.
     */
    public void propertyAdded(ScriptObject object, Property prop);

    /**
     * An existing property is being deleted.
     *
     * @param object The ScriptObject whose property is being deleted.
     * @param prop The property being deleted.
     */
    public void propertyDeleted(ScriptObject object, Property prop);

    /**
     * An existing Property is being replaced with a new Property.
     *
     * @param object The ScriptObject whose property is being modified.
     * @param oldProp The old property that is being replaced.
     * @param newProp The new property that replaces the old property.
     *
     */
    public void propertyModified(ScriptObject object, Property oldProp, Property newProp);

    /**
     * Given object's __proto__ has changed.
     *
     * @param object object whose __proto__ has changed.
     * @param oldProto old __proto__
     * @param newProto new __proto__
     */
    public void protoChanged(ScriptObject object, ScriptObject oldProto, ScriptObject newProto);
}
