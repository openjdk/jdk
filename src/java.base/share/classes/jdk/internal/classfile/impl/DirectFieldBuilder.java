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

import java.lang.classfile.CustomAttribute;
import java.lang.classfile.FieldBuilder;
import java.lang.classfile.FieldElement;
import java.lang.classfile.FieldModel;
import java.lang.classfile.constantpool.Utf8Entry;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public final class DirectFieldBuilder
        extends AbstractDirectBuilder<FieldModel>
        implements TerminalFieldBuilder, Util.Writable {
    private final Utf8Entry name;
    private final Utf8Entry desc;
    private int flags;

    public DirectFieldBuilder(SplitConstantPool constantPool,
                              ClassFileImpl context,
                              Utf8Entry name,
                              Utf8Entry type,
                              int flags,
                              FieldModel original) {
        super(constantPool, context);
        setOriginal(original);
        this.name = requireNonNull(name);
        this.desc = requireNonNull(type);
        this.flags = flags;
    }

    @Override
    public FieldBuilder with(FieldElement element) {
        if (element instanceof AbstractElement ae) {
            ae.writeTo(this);
        } else {
            writeAttribute((CustomAttribute<?>) requireNonNull(element));
        }
        return this;
    }

    public DirectFieldBuilder run(Consumer<? super FieldBuilder> handler) {
        handler.accept(this);
        return this;
    }

    @Override
    public FieldBuilder withFlags(int flags) {
        setFlags(flags);
        return this;
    }

    void setFlags(int flags) {
        this.flags = flags;
    }

    @Override
    public void writeTo(BufWriterImpl buf) {
        buf.writeU2U2U2(flags, buf.cpIndex(name), buf.cpIndex(desc));
        attributes.writeTo(buf);
    }
}
