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
import java.lang.foreign.UnionLayout;
import java.util.List;
import java.util.Optional;

public final class UnionLayoutImpl extends AbstractGroupLayout<UnionLayoutImpl> implements UnionLayout {

    private UnionLayoutImpl(List<MemoryLayout> elements, long byteSize, long byteAlignment, long minByteAlignment, Optional<String> name) {
        super(Kind.UNION, elements, byteSize, byteAlignment, minByteAlignment, name);
    }

    @Override
    UnionLayoutImpl dup(long byteAlignment, Optional<String> name) {
        return new UnionLayoutImpl(memberLayouts(), byteSize(), byteAlignment, minByteAlignment, name);
    }

    public static UnionLayout of(List<MemoryLayout> elements) {
        long size = 0;
        long align = 1;
        for (MemoryLayout elem : elements) {
            size = Math.max(size, elem.byteSize());
            align = Math.max(align, elem.byteAlignment());
        }
        return new UnionLayoutImpl(elements, size, align, align, Optional.empty());
    }

}
