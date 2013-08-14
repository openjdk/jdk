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

package jdk.nashorn.internal.runtime.arrays;

import java.util.Iterator;
import java.util.List;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.ScriptObject;

/**
 * Superclass for array iterators
 * TODO: rewrite these
 *
 * @param <T> element type
 */
abstract public class ArrayLikeIterator<T> implements Iterator<T> {

    /** current element index in iteration */
    protected long index;

    /** should undefined elements be included in the iteration? */
    protected final boolean includeUndefined;

    /**
     * Constructor
     *
     * @param includeUndefined should undefined elements be included in the iteration?
     */
    ArrayLikeIterator(final boolean includeUndefined) {
        this.includeUndefined = includeUndefined;
        this.index = 0;
    }

    /**
     * Is this a reverse order iteration?
     * @return true if reverse
     */
    public boolean isReverse() {
        return false;
    }

    /**
     * Go the the next valid element index of the iterator
     * @return next index
     */
    protected long bumpIndex() {
        return index++;
    }

    /**
     * Return the next valid element index of the iterator
     * @return next index
     */
    public long nextIndex() {
        return index;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove");
    }

    /**
     * Get the length of the iteration
     * @return length
     */
    public abstract long getLength();

    /**
     * ArrayLikeIterator factory
     *
     * @param object object over which to do element iteration
     * @return iterator
     */
    public static ArrayLikeIterator<Object> arrayLikeIterator(final Object object) {
        return arrayLikeIterator(object, false);
    }

    /**
     * ArrayLikeIterator factory (reverse order)
     * @param object object over which to do reverse element iteration
     * @return iterator
     */
    public static ArrayLikeIterator<Object> reverseArrayLikeIterator(final Object object) {
        return reverseArrayLikeIterator(object, false);
    }

    /**
     * ArrayLikeIterator factory
     * @param object object over which to do reverse element iteration
     * @param includeUndefined should undefined elements be included in the iteration
     * @return iterator
     */
    public static ArrayLikeIterator<Object> arrayLikeIterator(final Object object, final boolean includeUndefined) {
        Object obj = object;

        if (ScriptObject.isArray(obj)) {
            return new ScriptArrayIterator((ScriptObject) obj, includeUndefined);
        }

        obj = JSType.toScriptObject(obj);
        if (obj instanceof ScriptObject) {
            return new ScriptObjectIterator((ScriptObject)obj, includeUndefined);
        }

        if (obj instanceof ScriptObjectMirror) {
            return new ScriptObjectMirrorIterator((ScriptObjectMirror)obj, includeUndefined);
        }

        if (obj instanceof List) {
            return new JavaListIterator((List)obj, includeUndefined);
        }

        if (obj != null && obj.getClass().isArray()) {
            return new JavaArrayIterator(obj, includeUndefined);
        }

        return new EmptyArrayLikeIterator();
    }

    /**
     * ArrayLikeIterator factory (reverse order)
     * @param object object over which to do reverse element iteration
     * @param includeUndefined should undefined elements be included in the iteration
     * @return iterator
     */
    public static ArrayLikeIterator<Object> reverseArrayLikeIterator(final Object object, final boolean includeUndefined) {
        Object obj = object;

        if (ScriptObject.isArray(obj)) {
            return new ReverseScriptArrayIterator((ScriptObject) obj, includeUndefined);
        }

        obj = JSType.toScriptObject(obj);
        if (obj instanceof ScriptObject) {
            return new ReverseScriptObjectIterator((ScriptObject)obj, includeUndefined);
        }

        if (obj instanceof ScriptObjectMirror) {
            return new ReverseScriptObjectMirrorIterator((ScriptObjectMirror)obj, includeUndefined);
        }

        if (obj instanceof List) {
            return new ReverseJavaListIterator((List)obj, includeUndefined);
        }

        if (obj != null && obj.getClass().isArray()) {
            return new ReverseJavaArrayIterator(obj, includeUndefined);
        }

        return new EmptyArrayLikeIterator();
    }

}
