/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.jules;

public class TrapezoidList {
    public static final int TRAP_START_INDEX = 5;
    public static final int TRAP_SIZE = 10;

    int[] trapArray;

    public TrapezoidList(int[] trapArray) {
        this.trapArray = trapArray;
    }

    public final int[] getTrapArray() {
        return trapArray;
    }

    public final int getSize() {
        return trapArray[0];
    }

    public final void setSize(int size) {
        trapArray[0] = 0;
    }

    public final int getLeft() {
        return trapArray[1];
    }

    public final int getTop() {
        return trapArray[2];
    }

    public final int getRight() {
        return trapArray[3];
    }

    public final int getBottom() {
        return trapArray[4];
    }


    private final int getTrapStartAddresse(int pos) {
        return TRAP_START_INDEX + TRAP_SIZE * pos;
    }

    public final int getTop(int pos) {
        return trapArray[getTrapStartAddresse(pos) + 0];
    }

    public final int getBottom(int pos) {
        return trapArray[getTrapStartAddresse(pos) + 1];
    }

    public final int getP1XLeft(int pos) {
        return trapArray[getTrapStartAddresse(pos) + 2];
    }

    public final int getP1YLeft(int pos) {
        return trapArray[getTrapStartAddresse(pos) + 3];
    }

    public final int getP2XLeft(int pos) {
        return trapArray[getTrapStartAddresse(pos) + 4];
    }

    public final int getP2YLeft(int pos) {
        return trapArray[getTrapStartAddresse(pos) + 5];
    }

    public final int getP1XRight(int pos) {
        return trapArray[getTrapStartAddresse(pos) + 6];
    }

    public final int getP1YRight(int pos) {
        return trapArray[getTrapStartAddresse(pos) + 7];
    }

    public final int getP2XRight(int pos) {
        return trapArray[getTrapStartAddresse(pos) + 8];
    }

    public final int getP2YRight(int pos) {
        return trapArray[getTrapStartAddresse(pos) + 9];
    }
}
