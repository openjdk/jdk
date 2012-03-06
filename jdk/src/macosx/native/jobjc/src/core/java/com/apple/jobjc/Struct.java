/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.jobjc;

/**
 * A struct is malloced on the C heap and accessed in Java through a ByteBuffer.
 */
public abstract class Struct{
    protected final NativeBuffer raw;
    private final JObjCRuntime runtime;
    protected final JObjCRuntime getRuntime(){ return runtime; }

    /**
     * Create a brand new struct from nothing.
     */
    protected Struct(final JObjCRuntime runtime, final int SIZEOF){
        this(runtime, new NativeBuffer(SIZEOF), SIZEOF);
    }

    /**
     * Create a struct by taking ownership of an existing buffer.
     * Used for struct fields of type struct. For example, the origin and size fields
     * in NSRect would be initialized with this constructor.
     */
    protected Struct(final JObjCRuntime runtime, final NativeBuffer buffer, final int SIZEOF){
        if(runtime == null) throw new NullPointerException("runtime");
        this.runtime = runtime;
        this.raw = buffer;
        this.raw.limit(SIZEOF);
    }

    abstract public Coder getCoder();
}
