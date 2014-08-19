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
 * If your ScriptObject or similar PropertyAccess implementation only provides the most
 * generic getters and setters and does nothing fancy with other, more primitive, types,
 * then it is convenient to inherit this class and just fill out the methods left
 * abstract
 */
public abstract class DefaultPropertyAccess implements PropertyAccess {

    @Override
    public int getInt(final Object key, final int programPoint) {
        return JSType.toInt32(get(key));
    }

    @Override
    public int getInt(final double key, final int programPoint) {
        return getInt(JSType.toObject(key), programPoint);
    }

    @Override
    public int getInt(final long key, final int programPoint) {
        return getInt(JSType.toObject(key), programPoint);
    }

    @Override
    public int getInt(final int key, final int programPoint) {
        return getInt(JSType.toObject(key), programPoint);
    }

    @Override
    public long getLong(final Object key, final int programPoint) {
        return JSType.toLong(get(key));
    }

    @Override
    public long getLong(final double key, final int programPoint) {
        return getLong(JSType.toObject(key), programPoint);
    }

    @Override
    public long getLong(final long key, final int programPoint) {
        return getLong(JSType.toObject(key), programPoint);
    }

    @Override
    public long getLong(final int key, final int programPoint) {
        return getLong(JSType.toObject(key), programPoint);
    }

    @Override
    public double getDouble(final Object key, final int programPoint) {
        return JSType.toNumber(get(key));
    }

    @Override
    public double getDouble(final double key, final int programPoint) {
        return getDouble(JSType.toObject(key), programPoint);
    }

    @Override
    public double getDouble(final long key, final int programPoint) {
        return getDouble(JSType.toObject(key), programPoint);
    }

    @Override
    public double getDouble(final int key, final int programPoint) {
        return getDouble(JSType.toObject(key), programPoint);
    }

    @Override
    public abstract Object get(Object key);

    @Override
    public Object get(final double key) {
        return get(JSType.toObject(key));
    }

    @Override
    public Object get(final long key) {
        return get(JSType.toObject(key));
    }

    @Override
    public Object get(final int key) {
        return get(JSType.toObject(key));
    }

    @Override
    public void set(final double key, final int value, final boolean strict) {
        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final double key, final long value, final boolean strict) {
        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final double key, final double value, final boolean strict) {
        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final double key, final Object value, final boolean strict) {
        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final long key, final int value, final boolean strict) {
        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final long key, final long value, final boolean strict) {
        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final long key, final double value, final boolean strict) {
        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final long key, final Object value, final boolean strict) {
        set(JSType.toObject(key), value, strict);
    }

    @Override
    public void set(final int key, final int value, final boolean strict) {
        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final int key, final long value, final boolean strict) {
        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final int key, final double value, final boolean strict) {
        set(JSType.toObject(key), JSType.toObject(value), strict);
    }

    @Override
    public void set(final int key, final Object value, final boolean strict) {
        set(JSType.toObject(key), value, strict);
    }

    @Override
    public void set(final Object key, final int value, final boolean strict) {
        set(key, JSType.toObject(value), strict);
    }

    @Override
    public void set(final Object key, final long value, final boolean strict) {
        set(key, JSType.toObject(value), strict);
    }

    @Override
    public void set(final Object key, final double value, final boolean strict) {
        set(key, JSType.toObject(value), strict);
    }

    @Override
    public abstract void set(Object key, Object value, boolean strict);

    @Override
    public abstract boolean has(Object key);

    @Override
    public boolean has(final int key) {
        return has(JSType.toObject(key));
    }

    @Override
    public boolean has(final long key) {
        return has(JSType.toObject(key));
    }

    @Override
    public boolean has(final double key) {
        return has(JSType.toObject(key));
    }

    @Override
    public boolean hasOwnProperty(final int key) {
        return hasOwnProperty(JSType.toObject(key));
    }

    @Override
    public boolean hasOwnProperty(final long key) {
        return hasOwnProperty(JSType.toObject(key));
    }

    @Override
    public boolean hasOwnProperty(final double key) {
        return hasOwnProperty(JSType.toObject(key));
    }

    @Override
    public abstract boolean hasOwnProperty(Object key);

    @Override
    public boolean delete(final int key, final boolean strict) {
        return delete(JSType.toObject(key), strict);
    }

    @Override
    public boolean delete(final long key, final boolean strict) {
        return delete(JSType.toObject(key), strict);
    }

    @Override
    public boolean delete(final double key, final boolean strict) {
        return delete(JSType.toObject(key), strict);
    }

}
