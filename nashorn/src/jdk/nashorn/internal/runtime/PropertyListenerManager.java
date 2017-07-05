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

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Helper class to manage property listeners and notification.
 */
public class PropertyListenerManager implements PropertyListener {

    /** property listeners for this object. */
    private Map<PropertyListener,Boolean> listeners;

    // These counters are updated in debug mode
    private static int listenersAdded;
    private static int listenersRemoved;

    /**
     * @return the listenersAdded
     */
    public static int getListenersAdded() {
        return listenersAdded;
    }

    /**
     * @return the listenersRemoved
     */
    public static int getListenersRemoved() {
        return listenersRemoved;
    }

    // Property listener management methods

    /**
     * Add a property listener to this object.
     *
     * @param listener The property listener that is added.
     */
    public synchronized final void addPropertyListener(final PropertyListener listener) {
        if (listeners == null) {
            listeners = new WeakHashMap<>();
        }

        if (Context.DEBUG) {
            listenersAdded++;
        }
        listeners.put(listener, Boolean.TRUE);
    }

    /**
     * Remove a property listener from this object.
     *
     * @param listener The property listener that is removed.
     */
    public synchronized final void removePropertyListener(final PropertyListener listener) {
        if (listeners != null) {
            if (Context.DEBUG) {
                listenersRemoved++;
            }
            listeners.remove(listener);
        }
    }

    /**
     * This method can be called to notify property addition to this object's listeners.
     *
     * @param object The ScriptObject to which property was added.
     * @param prop The property being added.
     */
    protected synchronized final void notifyPropertyAdded(final ScriptObject object, final Property prop) {
        if (listeners != null) {
            for (PropertyListener listener : listeners.keySet()) {
                listener.propertyAdded(object, prop);
            }
        }
    }

    /**
     * This method can be called to notify property deletion to this object's listeners.
     *
     * @param object The ScriptObject from which property was deleted.
     * @param prop The property being deleted.
     */
    protected synchronized final void notifyPropertyDeleted(final ScriptObject object, final Property prop) {
        if (listeners != null) {
            for (PropertyListener listener : listeners.keySet()) {
                listener.propertyDeleted(object, prop);
            }
        }
    }

    /**
     * This method can be called to notify property modification to this object's listeners.
     *
     * @param object The ScriptObject to which property was modified.
     * @param oldProp The old property being replaced.
     * @param newProp The new property that replaces the old property.
     */
    protected synchronized final void notifyPropertyModified(final ScriptObject object, final Property oldProp, final Property newProp) {
        if (listeners != null) {
            for (PropertyListener listener : listeners.keySet()) {
                listener.propertyModified(object, oldProp, newProp);
            }
        }
    }

    // PropertyListener methods

    @Override
    public final void propertyAdded(final ScriptObject object, final Property prop) {
        notifyPropertyAdded(object, prop);
    }

    @Override
    public final void propertyDeleted(final ScriptObject object, final Property prop) {
        notifyPropertyDeleted(object, prop);
    }

    @Override
    public final void propertyModified(final ScriptObject object, final Property oldProp, final Property newProp) {
        notifyPropertyModified(object, oldProp, newProp);
    }
}
