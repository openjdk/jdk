/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl;

import java.util.ArrayList;
import java.util.List;

import java.lang.classfile.Attribute;
import java.lang.classfile.AttributeMapper;

public class AttributeHolder {
    private final List<Attribute<?>> attributes = new ArrayList<>();

    public <A extends Attribute<A>> void withAttribute(Attribute<?> a) {
        if (a == null)
            return;

        @SuppressWarnings("unchecked")
        AttributeMapper<A> am = (AttributeMapper<A>) a.attributeMapper();
        if (!am.allowMultiple() && isPresent(am)) {
            remove(am);
        }
        attributes.add(a);
    }

    public int size() {
        return attributes.size();
    }

    public void writeTo(BufWriterImpl buf) {
        Util.writeAttributes(buf, attributes);
    }

    boolean isPresent(AttributeMapper<?> am) {
        for (Attribute<?> a : attributes)
            if (a.attributeMapper() == am)
                return true;
        return false;
    }

    private void remove(AttributeMapper<?> am) {
        attributes.removeIf(a -> a.attributeMapper() == am);
    }
}
