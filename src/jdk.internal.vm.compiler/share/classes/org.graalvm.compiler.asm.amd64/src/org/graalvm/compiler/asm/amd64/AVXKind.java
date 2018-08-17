/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.asm.amd64;

import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.DWORD;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.QWORD;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.ZMM;

import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.amd64.AMD64Kind;

/**
 * Helper methods for dealing with AVX and SSE {@link AMD64Kind AMD64Kinds}.
 */
public final class AVXKind {

    public enum AVXSize {
        DWORD,
        QWORD,
        XMM,
        YMM,
        ZMM;

        public int getBytes() {
            switch (this) {
                case DWORD:
                    return 4;
                case QWORD:
                    return 8;
                case XMM:
                    return 16;
                case YMM:
                    return 32;
                case ZMM:
                    return 64;
                default:
                    return 0;
            }
        }
    }

    private AVXKind() {
    }

    public static AVXSize getRegisterSize(Value a) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.isXMM()) {
            return getRegisterSize(kind);
        } else {
            return XMM;
        }
    }

    public static AVXSize getDataSize(AMD64Kind kind) {
        assert kind.isXMM() : "unexpected kind " + kind;
        switch (kind.getSizeInBytes()) {
            case 4:
                return DWORD;
            case 8:
                return QWORD;
            case 16:
                return XMM;
            case 32:
                return YMM;
            case 64:
                return ZMM;
            default:
                throw GraalError.shouldNotReachHere("unsupported kind: " + kind);
        }
    }

    public static AVXSize getRegisterSize(AMD64Kind kind) {
        assert kind.isXMM() : "unexpected kind " + kind;
        int size = kind.getSizeInBytes();
        if (size > 32) {
            return ZMM;
        } else if (size > 16) {
            return YMM;
        } else {
            return XMM;
        }
    }

    public static AMD64Kind changeSize(AMD64Kind kind, AVXSize newSize) {
        return getAVXKind(kind.getScalar(), newSize);
    }

    public static AMD64Kind getAVXKind(AMD64Kind base, AVXSize size) {
        for (AMD64Kind ret : AMD64Kind.values()) {
            if (ret.getScalar() == base && ret.getSizeInBytes() == size.getBytes()) {
                return ret;
            }
        }
        throw GraalError.shouldNotReachHere(String.format("unsupported vector kind: %s x %s", size, base));
    }

    public static AMD64Kind getAVXKind(AMD64Kind base, int length) {
        for (AMD64Kind ret : AMD64Kind.values()) {
            if (ret.getScalar() == base && ret.getVectorLength() == length) {
                return ret;
            }
        }
        throw GraalError.shouldNotReachHere(String.format("unsupported vector kind: %d x %s", length, base));
    }
}
