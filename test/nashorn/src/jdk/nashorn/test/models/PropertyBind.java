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

package jdk.nashorn.test.models;

@SuppressWarnings("javadoc")
public class PropertyBind {
    public static int publicStaticInt;
    public static final int publicStaticFinalInt = 2112;

    private static int staticReadWrite;
    private static int staticReadOnly = 1230;
    private static int staticWriteOnly;

    public int publicInt;
    public final int publicFinalInt = 42;

    private int readWrite;
    private final int readOnly = 123;
    private int writeOnly;

    public int getReadWrite() {
        return readWrite;
    }

    public void setReadWrite(final int readWrite) {
        this.readWrite = readWrite;
    }

    public int getReadOnly() {
        return readOnly;
    }

    public void setWriteOnly(final int writeOnly) {
        this.writeOnly = writeOnly;
    }

    public int peekWriteOnly() {
        return writeOnly;
    }

    public static int getStaticReadWrite() {
        return staticReadWrite;
    }

    public static void setStaticReadWrite(final int staticReadWrite) {
        PropertyBind.staticReadWrite = staticReadWrite;
    }

    public static int getStaticReadOnly() {
        return staticReadOnly;
    }

    public static void setStaticWriteOnly(final int staticWriteOnly) {
        PropertyBind.staticWriteOnly = staticWriteOnly;
    }

    public static int peekStaticWriteOnly() {
        return PropertyBind.staticWriteOnly;
    }
}
