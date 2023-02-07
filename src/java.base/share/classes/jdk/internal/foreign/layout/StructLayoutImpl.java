/*
 *  Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */
package jdk.internal.foreign.layout;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.util.List;
import java.util.Optional;

public final class StructLayoutImpl extends AbstractGroupLayout<StructLayoutImpl> implements StructLayout {

    private StructLayoutImpl(List<MemoryLayout> elements) {
        super(Kind.STRUCT, elements);
    }

    private StructLayoutImpl(List<MemoryLayout> elements, long bitAlignment, Optional<String> name) {
        super(Kind.STRUCT, elements, bitAlignment, name);
    }

    @Override
    StructLayoutImpl dup(long bitAlignment, Optional<String> name) {
        return new StructLayoutImpl(memberLayouts(), bitAlignment, name);
    }

    public static StructLayout of(List<MemoryLayout> elements) {
        return new StructLayoutImpl(elements);
    }

}
