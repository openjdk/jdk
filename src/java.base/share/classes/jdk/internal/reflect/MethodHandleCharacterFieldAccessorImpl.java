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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

class MethodHandleCharacterFieldAccessorImpl extends MethodHandleFieldAccessorImpl {
    static FieldAccessorImpl fieldAccessor(Field field, MethodHandle getter, MethodHandle setter, boolean isReadOnly) {
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        if (isStatic) {
            getter = getter.asType(MethodType.methodType(char.class));
            if (setter != null) {
                setter = setter.asType(MethodType.methodType(void.class, char.class));
            }
        } else {
            getter = getter.asType(MethodType.methodType(char.class, Object.class));
            if (setter != null) {
                setter = setter.asType(MethodType.methodType(void.class, Object.class, char.class));
            }
        }
        return new MethodHandleCharacterFieldAccessorImpl(field, getter, setter, isReadOnly, isStatic);
    }

    MethodHandleCharacterFieldAccessorImpl(Field field, MethodHandle getter, MethodHandle setter, boolean isReadOnly, boolean isStatic) {
        super(field, getter, setter, isReadOnly, isStatic);
    }

    public Object get(Object obj) throws IllegalArgumentException {
        return Character.valueOf(getChar(obj));
    }

    public boolean getBoolean(Object obj) throws IllegalArgumentException {
        throw newGetBooleanIllegalArgumentException();
    }

    public byte getByte(Object obj) throws IllegalArgumentException {
        throw newGetByteIllegalArgumentException();
    }

    public char getChar(Object obj) throws IllegalArgumentException {
        try {
            if (isStatic()) {
                return (char) getter.invokeExact();
            } else {
                return (char) getter.invokeExact(obj);
            }
        } catch (IllegalArgumentException|NullPointerException e) {
            throw e;
        } catch (ClassCastException e) {
            throw newGetIllegalArgumentException(obj);
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }

    public short getShort(Object obj) throws IllegalArgumentException {
        throw newGetShortIllegalArgumentException();
    }

    public int getInt(Object obj) throws IllegalArgumentException {
        return getChar(obj);
    }

    public long getLong(Object obj) throws IllegalArgumentException {
        return getChar(obj);
    }

    public float getFloat(Object obj) throws IllegalArgumentException {
        return getChar(obj);
    }

    public double getDouble(Object obj) throws IllegalArgumentException {
        return getChar(obj);
    }

    public void set(Object obj, Object value)
            throws IllegalArgumentException, IllegalAccessException
    {
        ensureObj(obj);
        if (isReadOnly()) {
            throwFinalFieldIllegalAccessException(value);
        }

        if (value == null) {
            throwSetIllegalArgumentException(value);
        }

        if (value instanceof Character c) {
            setChar(obj, c.charValue());
        } else {
            throwSetIllegalArgumentException(value);
        }
    }

    public void setBoolean(Object obj, boolean z)
        throws IllegalArgumentException, IllegalAccessException
    {
        throwSetIllegalArgumentException(z);
    }

    public void setByte(Object obj, byte b)
        throws IllegalArgumentException, IllegalAccessException
    {
        throwSetIllegalArgumentException(b);
    }

    public void setChar(Object obj, char c)
        throws IllegalArgumentException, IllegalAccessException
    {
        if (isReadOnly()) {
            ensureObj(obj);     // throw NPE if obj is null on instance field
            throwFinalFieldIllegalAccessException(c);
        }
        try {
            if (isStatic()) {
                setter.invokeExact(c);
            } else {
                setter.invokeExact(obj, c);
            }
        } catch (IllegalArgumentException|NullPointerException e) {
            throw e;
        } catch (ClassCastException e) {
            // receiver is of invalid type
            throw newSetIllegalArgumentException(obj);
        } catch (Throwable e) {
            throw new InternalError(e);
        }
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
