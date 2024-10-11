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

import java.util.List;

import java.lang.classfile.Attribute;
import java.lang.classfile.ClassReader;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.classfile.constantpool.Utf8Entry;

public final class BoundRecordComponentInfo
        implements RecordComponentInfo {

    private final ClassReader reader;
    private final int startPos, attributesPos;
    private List<Attribute<?>> attributes;

    public BoundRecordComponentInfo(ClassReader reader, int startPos) {
        this.reader = reader;
        this.startPos = startPos;
        attributesPos = startPos + 4;
    }

    @Override
    public Utf8Entry name() {
        return reader.readEntry(startPos, Utf8Entry.class);
    }

    @Override
    public Utf8Entry descriptor() {
        return reader.readEntry(startPos + 2, Utf8Entry.class);
    }

    @Override
    public List<Attribute<?>> attributes() {
        if (attributes == null) {
            attributes = BoundAttribute.readAttributes(null, reader, attributesPos, reader.customAttributes());
        }
        return attributes;
    }
}
