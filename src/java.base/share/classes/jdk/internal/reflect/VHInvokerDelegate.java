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

import jdk.internal.vm.annotation.Hidden;

import java.lang.invoke.VarHandle;

/**
 * Delegate the field access directly to the target VarHandle.
 */
final class VHInvokerDelegate implements VHInvoker {
    private final VarHandle varHandle;
    VHInvokerDelegate(VarHandle varHandle) {
        this.varHandle = varHandle;
    }

    // accessors for instance fields
    @Hidden public Object get(Object obj) {
        return varHandle.get(obj);
    }

    @Hidden public boolean getBoolean(Object obj) {
        return (boolean) varHandle.get(obj);
    }

    @Hidden public byte getByte(Object obj) {
        return (byte) varHandle.get(obj);
    }

    @Hidden public char getChar(Object obj) {
        return (char) varHandle.get(obj);
    }

    @Hidden public short getShort(Object obj) {
        return (short) varHandle.get(obj);
    }

    @Hidden public int getInt(Object obj) {
        return (int) varHandle.get(obj);
    }

    @Hidden public long getLong(Object obj) {
        return (long) varHandle.get(obj);
    }

    @Hidden public float getFloat(Object obj) {
        return (float) varHandle.get(obj);
    }

    @Hidden public double getDouble(Object obj) {
        return (double) varHandle.get(obj);
    }

    @Hidden public void set(Object obj, Object value) throws Throwable {
        varHandle.set(obj, value);
    }

    @Hidden public void setBoolean(Object obj, boolean z) throws Throwable {
        varHandle.set(obj, z);
    }

    @Hidden public void setByte(Object obj, byte b) throws Throwable {
        varHandle.set(obj, b);
    }

    @Hidden public void setChar(Object obj, char c) throws Throwable {
        varHandle.set(obj, c);
    }

    @Hidden public void setShort(Object obj, short s) throws Throwable {
        varHandle.set(obj, s);
    }

    @Hidden public void setInt(Object obj, int i) throws Throwable {
        varHandle.set(obj, i);
    }

    @Hidden public void setLong(Object obj, long l) throws Throwable {
        varHandle.set(obj, l);
    }

    @Hidden public void setFloat(Object obj, float f) throws Throwable {
        varHandle.set(obj, f);
    }

    @Hidden public void setDouble(Object obj, double d) throws Throwable {
        varHandle.set(obj, d);
    }

    // accessors for static fields
    @Hidden public Object get() {
        return varHandle.get();
    }

    @Hidden public boolean getBoolean() {
        return (boolean) varHandle.get();
    }

    @Hidden public byte getByte() {
        return (byte) varHandle.get();
    }

    @Hidden public char getChar() {
        return (char) varHandle.get();
    }

    @Hidden public short getShort() {
        return (short) varHandle.get();
    }

    @Hidden public int getInt() {
        return (int) varHandle.get();
    }

    @Hidden public long getLong() {
        return (long) varHandle.get();
    }

    @Hidden public float getFloat() {
        return (float) varHandle.get();
    }

    @Hidden public double getDouble() {
        return (double) varHandle.get();
    }

    @Hidden public void set(Object value) throws Throwable {
        varHandle.set(value);
    }

    @Hidden public void setBoolean(boolean z) throws Throwable {
        varHandle.set(z);
    }

    @Hidden public void setByte(byte b) throws Throwable {
        varHandle.set(b);
    }

    @Hidden public void setChar(char c) throws Throwable {
        varHandle.set(c);
    }

    @Hidden public void setShort(short s) throws Throwable {
        varHandle.set(s);
    }

    @Hidden public void setInt(int i) throws Throwable {
        varHandle.set(i);
    }

    @Hidden public void setLong(long l) throws Throwable {
        varHandle.set(l);
    }

    @Hidden public void setFloat(float f) throws Throwable {
        varHandle.set(f);
    }

    @Hidden public void setDouble(double d) throws Throwable {
        varHandle.set(d);
    }
}
