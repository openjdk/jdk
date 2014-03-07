/*
 * Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
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
package javax.swing;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Set;

/**
 * <code>InputMap</code> provides a binding between an input event
 * (currently only <code>KeyStroke</code>s are used)
 * and an <code>Object</code>. <code>InputMap</code>s
 * are usually used with an <code>ActionMap</code>,
 * to determine an <code>Action</code> to perform
 * when a key is pressed.
 * An <code>InputMap</code> can have a parent
 * that is searched for bindings not defined in the <code>InputMap</code>.
 * <p>As with <code>ActionMap</code> if you create a cycle, eg:
 * <pre>
 *   InputMap am = new InputMap();
 *   InputMap bm = new InputMap():
 *   am.setParent(bm);
 *   bm.setParent(am);
 * </pre>
 * some of the methods will cause a StackOverflowError to be thrown.
 *
 * @author Scott Violet
 * @since 1.3
 */
@SuppressWarnings("serial")
public class InputMap implements Serializable {
    /** Handles the mapping between KeyStroke and Action name. */
    private transient ArrayTable     arrayTable;
    /** Parent that handles any bindings we don't contain. */
    private InputMap                                parent;


    /**
     * Creates an <code>InputMap</code> with no parent and no mappings.
     */
    public InputMap() {
    }

    /**
     * Sets this <code>InputMap</code>'s parent.
     *
     * @param map  the <code>InputMap</code> that is the parent of this one
     */
    public void setParent(InputMap map) {
        this.parent = map;
    }

    /**
     * Gets this <code>InputMap</code>'s parent.
     *
     * @return map  the <code>InputMap</code> that is the parent of this one,
     *              or null if this <code>InputMap</code> has no parent
     */
    public InputMap getParent() {
        return parent;
    }

    /**
     * Adds a binding for <code>keyStroke</code> to <code>actionMapKey</code>.
     * If <code>actionMapKey</code> is null, this removes the current binding
     * for <code>keyStroke</code>.
     */
    public void put(KeyStroke keyStroke, Object actionMapKey) {
        if (keyStroke == null) {
            return;
        }
        if (actionMapKey == null) {
            remove(keyStroke);
        }
        else {
            if (arrayTable == null) {
                arrayTable = new ArrayTable();
            }
            arrayTable.put(keyStroke, actionMapKey);
        }
    }

    /**
     * Returns the binding for <code>keyStroke</code>, messaging the
     * parent <code>InputMap</code> if the binding is not locally defined.
     */
    public Object get(KeyStroke keyStroke) {
        if (arrayTable == null) {
            InputMap    parent = getParent();

            if (parent != null) {
                return parent.get(keyStroke);
            }
            return null;
        }
        Object value = arrayTable.get(keyStroke);

        if (value == null) {
            InputMap    parent = getParent();

            if (parent != null) {
                return parent.get(keyStroke);
            }
        }
        return value;
    }

    /**
     * Removes the binding for <code>key</code> from this
     * <code>InputMap</code>.
     */
    public void remove(KeyStroke key) {
        if (arrayTable != null) {
            arrayTable.remove(key);
        }
    }

    /**
     * Removes all the mappings from this <code>InputMap</code>.
     */
    public void clear() {
        if (arrayTable != null) {
            arrayTable.clear();
        }
    }

    /**
     * Returns the <code>KeyStroke</code>s that are bound in this <code>InputMap</code>.
     */
    public KeyStroke[] keys() {
        if (arrayTable == null) {
            return null;
        }
        KeyStroke[] keys = new KeyStroke[arrayTable.size()];
        arrayTable.getKeys(keys);
        return keys;
    }

    /**
     * Returns the number of <code>KeyStroke</code> bindings.
     */
    public int size() {
        if (arrayTable == null) {
            return 0;
        }
        return arrayTable.size();
    }

    /**
     * Returns an array of the <code>KeyStroke</code>s defined in this
     * <code>InputMap</code> and its parent. This differs from <code>keys()</code> in that
     * this method includes the keys defined in the parent.
     */
    public KeyStroke[] allKeys() {
        int             count = size();
        InputMap        parent = getParent();

        if (count == 0) {
            if (parent != null) {
                return parent.allKeys();
            }
            return keys();
        }
        if (parent == null) {
            return keys();
        }
        KeyStroke[]    keys = keys();
        KeyStroke[]    pKeys =  parent.allKeys();

        if (pKeys == null) {
            return keys;
        }
        if (keys == null) {
            // Should only happen if size() != keys.length, which should only
            // happen if mutated from multiple threads (or a bogus subclass).
            return pKeys;
        }

        HashMap<KeyStroke, KeyStroke> keyMap = new HashMap<KeyStroke, KeyStroke>();
        int            counter;

        for (counter = keys.length - 1; counter >= 0; counter--) {
            keyMap.put(keys[counter], keys[counter]);
        }
        for (counter = pKeys.length - 1; counter >= 0; counter--) {
            keyMap.put(pKeys[counter], pKeys[counter]);
        }

        KeyStroke[]    allKeys = new KeyStroke[keyMap.size()];

        return keyMap.keySet().toArray(allKeys);
    }

    private void writeObject(ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();

        ArrayTable.writeArrayTable(s, arrayTable);
    }

    private void readObject(ObjectInputStream s) throws ClassNotFoundException,
                                                 IOException {
        s.defaultReadObject();
        for (int counter = s.readInt() - 1; counter >= 0; counter--) {
            put((KeyStroke)s.readObject(), s.readObject());
        }
    }
}
