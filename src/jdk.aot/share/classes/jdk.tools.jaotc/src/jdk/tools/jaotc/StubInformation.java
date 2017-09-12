/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jaotc;

public final class StubInformation {
    private int stubOffset;         // the offset inside the code (text + stubOffset)
    private int stubSize;           // the stub size
    private int dispatchJumpOffset; // offset after main dispatch jump instruction
    private int resolveJumpOffset;  // offset after jump instruction to runtime call resolution function.
    private int resolveJumpStart;   // offset of jump instruction to VM runtime call resolution function.
    private int c2iJumpOffset;      // offset after jump instruction to c2i adapter for static calls.
    private int movOffset;          // offset after move instruction which loads from got cell:
                                    // - Method* for static call
                                    // - Klass* for virtual call

    private boolean isVirtual;  // virtual call stub

    // maybe add type of stub as well, right now we only have static stubs

    StubInformation(int stubOffset, boolean isVirtual) {
        this.stubOffset = stubOffset;
        this.isVirtual = isVirtual;
        this.stubSize = -1;
        this.movOffset = -1;
        this.c2iJumpOffset = -1;
        this.resolveJumpOffset = -1;
        this.resolveJumpStart = -1;
        this.dispatchJumpOffset = -1;
    }

    int getOffset() {
        return stubOffset;
    }

    boolean isVirtual() {
        return isVirtual;
    }

    public void setSize(int stubSize) {
        this.stubSize = stubSize;
    }

    int getSize() {
        return stubSize;
    }

    public void setMovOffset(int movOffset) {
        this.movOffset = movOffset + stubOffset;
    }

    int getMovOffset() {
        return movOffset;
    }

    public void setC2IJumpOffset(int c2iJumpOffset) {
        this.c2iJumpOffset = c2iJumpOffset + stubOffset;
    }

    int getC2IJumpOffset() {
        return c2iJumpOffset;
    }

    public void setResolveJumpOffset(int resolveJumpOffset) {
        this.resolveJumpOffset = resolveJumpOffset + stubOffset;
    }

    int getResolveJumpOffset() {
        return resolveJumpOffset;
    }

    public void setResolveJumpStart(int resolveJumpStart) {
        this.resolveJumpStart = resolveJumpStart + stubOffset;
    }

    int getResolveJumpStart() {
        return resolveJumpStart;
    }

    public void setDispatchJumpOffset(int dispatchJumpOffset) {
        this.dispatchJumpOffset = dispatchJumpOffset + stubOffset;
    }

    int getDispatchJumpOffset() {
        return dispatchJumpOffset;
    }

    void verify() {
        assert stubOffset > 0 : "incorrect stubOffset: " + stubOffset;
        assert stubSize > 0 : "incorrect stubSize: " + stubSize;
        assert movOffset > 0 : "incorrect movOffset: " + movOffset;
        assert dispatchJumpOffset > 0 : "incorrect dispatchJumpOffset: " + dispatchJumpOffset;
        assert resolveJumpStart > 0 : "incorrect resolveJumpStart: " + resolveJumpStart;
        assert resolveJumpOffset > 0 : "incorrect resolveJumpOffset: " + resolveJumpOffset;
        if (!isVirtual) {
            assert c2iJumpOffset > 0 : "incorrect c2iJumpOffset: " + c2iJumpOffset;
        }
    }
}
