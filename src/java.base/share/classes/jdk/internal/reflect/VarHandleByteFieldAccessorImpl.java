/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.reflect;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

abstract class VarHandleByteFieldAccessorImpl extends VarHandleFieldAccessorImpl {
    static FieldAccessorImpl fieldAccessor(Field field, VarHandle varHandle, boolean isReadOnly) {
        return Modifier.isStatic(field.getModifiers())
                ? new StaticFieldAccessor(field, varHandle, isReadOnly)
                : new InstanceFieldAccessor(field, varHandle, isReadOnly);
    }

    VarHandleByteFieldAccessorImpl(Field field, VarHandle varHandle, boolean isReadOnly) {
        super(field, varHandle, isReadOnly);
    }

    abstract byte getValue(Object obj);
    abstract void setValue(Object obj, byte b) throws Throwable;

    static class StaticFieldAccessor extends VarHandleByteFieldAccessorImpl {
        StaticFieldAccessor(Field field, VarHandle varHandle, boolean isReadOnly) {
            super(field, varHandle, isReadOnly);
        }

        byte getValue(Object obj) {
            return accessor().getByte();
        }

        void setValue(Object obj, byte b) throws Throwable {
            accessor().setByte(b);
        }

        protected void ensureObj(Object o) {}
    }

    static class InstanceFieldAccessor extends VarHandleByteFieldAccessorImpl {
        InstanceFieldAccessor(Field field, VarHandle varHandle, boolean isReadOnly) {
            super(field, varHandle, isReadOnly);
        }

        byte getValue(Object obj) {
            return accessor().getByte(obj);
        }

        void setValue(Object obj, byte b) throws Throwable {
            accessor().setByte(obj, b);
        }
    }

    public Object get(Object obj) throws IllegalArgumentException {
        return Byte.valueOf(getByte(obj));
    }

    public boolean getBoolean(Object obj) throws IllegalArgumentException {
        throw newGetBooleanIllegalArgumentException();
    }

    public byte getByte(Object obj) throws IllegalArgumentException {
        try {
            return getValue(obj);
        } catch (IllegalArgumentException|NullPointerException e) {
            throw e;
        } catch (ClassCastException e) {
            throw newGetIllegalArgumentException(obj.getClass());
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }

    public char getChar(Object obj) throws IllegalArgumentException {
        throw newGetCharIllegalArgumentException();
    }

    public short getShort(Object obj) throws IllegalArgumentException {
        return getByte(obj);
    }

    public int getInt(Object obj) throws IllegalArgumentException {
        return getByte(obj);
    }

    public long getLong(Object obj) throws IllegalArgumentException {
        return getByte(obj);
    }

    public float getFloat(Object obj) throws IllegalArgumentException {
        return getByte(obj);
    }

    public double getDouble(Object obj) throws IllegalArgumentException {
        return getByte(obj);
    }

    public void set(Object obj, Object value)
            throws IllegalArgumentException, IllegalAccessException
    {
        ensureObj(obj);
        if (isReadOnly) {
            throwFinalFieldIllegalAccessException(value);
        }
        if (value == null) {
            throwSetIllegalArgumentException(value);
        }
        if (value instanceof Byte) {
            setByte(obj, ((Byte) value).byteValue());
            return;
        }
        throwSetIllegalArgumentException(value);
    }

    public void setBoolean(Object obj, boolean z)
        throws IllegalArgumentException, IllegalAccessException
    {
        throwSetIllegalArgumentException(z);
    }

    public void setByte(Object obj, byte b)
        throws IllegalArgumentException, IllegalAccessException
    {
        ensureObj(obj);
        if (isReadOnly) {
            throwFinalFieldIllegalAccessException(b);
        }
        try {
            setValue(obj, b);
        } catch (IllegalArgumentException|NullPointerException e) {
            throw e;
        } catch (ClassCastException e) {
            throw newSetIllegalArgumentException(obj.getClass());
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }

    public void setChar(Object obj, char c)
        throws IllegalArgumentException, IllegalAccessException
    {
        throwSetIllegalArgumentException(c);
    }

    public void setShort(Object obj, short s)
        throws IllegalArgumentException, IllegalAccessException
    {
        throwSetIllegalArgumentException(s);
    }

    public void setInt(Object obj, int i)
        throws IllegalArgumentException, IllegalAccessException
    {
        throwSetIllegalArgumentException(i);
    }

    public void setLong(Object obj, long l)
        throws IllegalArgumentException, IllegalAccessException
    {
        throwSetIllegalArgumentException(l);
    }

    public void setFloat(Object obj, float f)
        throws IllegalArgumentException, IllegalAccessException
    {
        throwSetIllegalArgumentException(f);
    }

    public void setDouble(Object obj, double d)
        throws IllegalArgumentException, IllegalAccessException
    {
        throwSetIllegalArgumentException(d);
    }
}
