/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package p1;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;

public interface Mutator {
    void set(Field f, Object obj, Object value) throws IllegalAccessException;
    void setBoolean(Field f, Object obj, boolean value) throws IllegalAccessException;
    void setByte(Field f, Object obj, byte value) throws IllegalAccessException;
    void setChar(Field f, Object obj, char value) throws IllegalAccessException;
    void setShort(Field f, Object obj, short value) throws IllegalAccessException;
    void setInt(Field f, Object obj, short value) throws IllegalAccessException;
    void setInt(Field f, Object obj, int value) throws IllegalAccessException;
    void setLong(Field f, Object obj, long value) throws IllegalAccessException;
    void setFloat(Field f, Object obj, float value) throws IllegalAccessException;
    void setDouble(Field f, Object obj, double value) throws IllegalAccessException;
    MethodHandle unreflectSetter(Field f) throws IllegalAccessException;

    static Mutator throwing() {
        return new Mutator() {
            @Override
            public void set(Field f, Object obj, Object value) throws IllegalAccessException {
                throw new IllegalAccessException();
            }
            @Override
            public void setBoolean(Field f, Object obj, boolean value) throws IllegalAccessException {
                throw new IllegalAccessException();
            }
            @Override
            public void setByte(Field f, Object obj, byte value) throws IllegalAccessException {
                throw new IllegalAccessException();
            }
            @Override
            public void setChar(Field f, Object obj, char value) throws IllegalAccessException {
                throw new IllegalAccessException();
            }
            @Override
            public void setShort(Field f, Object obj, short value) throws IllegalAccessException {
                throw new IllegalAccessException();
            }
            @Override
            public void setInt(Field f, Object obj, short value) throws IllegalAccessException {
                throw new IllegalAccessException();
            }
            @Override
            public void setInt(Field f, Object obj, int value) throws IllegalAccessException {
                throw new IllegalAccessException();
            }
            @Override
            public void setLong(Field f, Object obj, long value) throws IllegalAccessException {
                throw new IllegalAccessException();
            }
            @Override
            public void setFloat(Field f, Object obj, float value) throws IllegalAccessException {
                throw new IllegalAccessException();
            }
            @Override
            public void setDouble(Field f, Object obj, double value) throws IllegalAccessException {
                throw new IllegalAccessException();
            }
            @Override
            public MethodHandle unreflectSetter(Field f) throws IllegalAccessException {
                throw new IllegalAccessException();
            }
            @Override
            public String toString() {
                return "<throwing>";
            }
        };
    }
}
