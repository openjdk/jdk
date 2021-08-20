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

public interface VHInvoker {
    // accessors for instance fields
    default Object get(Object obj) {
        throw new UnsupportedOperationException();
    }

    default boolean getBoolean(Object obj) {
        throw new UnsupportedOperationException();
    }

    default byte getByte(Object obj) {
        throw new UnsupportedOperationException();
    }

    default char getChar(Object obj) {
        throw new UnsupportedOperationException();
    }

    default short getShort(Object obj) {
        throw new UnsupportedOperationException();
    }

    default int getInt(Object obj) {
        throw new UnsupportedOperationException();
    }

    default long getLong(Object obj) {
        throw new UnsupportedOperationException();
    }

    default float getFloat(Object obj) {
        throw new UnsupportedOperationException();
    }

    default double getDouble(Object obj) {
        throw new UnsupportedOperationException();
    }

    default void set(Object obj, Object value) throws Throwable {
        throw new UnsupportedOperationException();
    }

    default void setBoolean(Object obj, boolean z) throws Throwable {
        throw new UnsupportedOperationException();
    }

    default void setByte(Object obj, byte b) throws Throwable {
        throw new UnsupportedOperationException();
    }

    default void setChar(Object obj, char c) throws Throwable {
        throw new UnsupportedOperationException();
    }

    default void setShort(Object obj, short s) throws Throwable {
        throw new UnsupportedOperationException();
    }

    default void setInt(Object obj, int i) throws Throwable {
        throw new UnsupportedOperationException();
    }

    default void setLong(Object obj, long l) throws Throwable {
        throw new UnsupportedOperationException();
    }

    default void setFloat(Object obj, float f) throws Throwable {
        throw new UnsupportedOperationException();
    }

    default void setDouble(Object obj, double d) throws Throwable {
        throw new UnsupportedOperationException();
    }

    // accessors for static fields
    default Object get() {
        throw new UnsupportedOperationException();
    }

    default boolean getBoolean() {
        throw new UnsupportedOperationException();
    }

    default byte getByte() {
        throw new UnsupportedOperationException();
    }

    default char getChar() {
        throw new UnsupportedOperationException();
    }

    default short getShort() {
        throw new UnsupportedOperationException();
    }

    default int getInt() {
        throw new UnsupportedOperationException();
    }

    default long getLong() {
        throw new UnsupportedOperationException();
    }

    default float getFloat() {
        throw new UnsupportedOperationException();
    }

    default double getDouble() {
        throw new UnsupportedOperationException();
    }

    default void set(Object value) throws Throwable {
        throw new UnsupportedOperationException();
    }

    default void setBoolean(boolean z) throws Throwable {
        throw new UnsupportedOperationException();
    }

    default void setByte(byte b) throws Throwable {
        throw new UnsupportedOperationException();
    }

    default void setChar(char c) throws Throwable {
        throw new UnsupportedOperationException();
    }

    default void setShort(short s) throws Throwable {
        throw new UnsupportedOperationException();
    }

    default void setInt(int i) throws Throwable {
        throw new UnsupportedOperationException();
    }

    default void setLong(long l) throws Throwable {
        throw new UnsupportedOperationException();
    }

    default void setFloat(float f) throws Throwable {
        throw new UnsupportedOperationException();
    }

    default void setDouble(double d) throws Throwable {
        throw new UnsupportedOperationException();
    }
}
