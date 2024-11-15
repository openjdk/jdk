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
package java.lang.classfile.constantpool;

import java.lang.constant.MethodTypeDesc;

import jdk.internal.classfile.impl.AbstractPoolEntry;
import jdk.internal.classfile.impl.Util;

/**
 * Models a {@code CONSTANT_InterfaceMethodRef_info} structure, or a symbolic
 * reference to an interface method, in the constant pool of a {@code class}
 * file.
 * <p>
 * Conceptually, an interface method reference entry is a record:
 * {@snippet lang=text :
 * // @link region=1 substring="InterfaceMethodRefEntry" target="ConstantPoolBuilder#interfaceMethodRefEntry(ClassDesc, String, MethodTypeDesc)"
 * // @link region=2 substring="ClassDesc owner" target="#owner()"
 * // @link substring="String name" target="#name()" :
 * InterfaceMethodRefEntry(ClassDesc owner, String name, MethodTypeDesc type) // @link substring="MethodTypeDesc type" target="#typeSymbol()"
 * // @end region=1
 * // @end region=2
 * }
 * where the {@code ClassDesc owner} represents an interface.
 * <p>
 * Physically, an interface method reference entry is a record:
 * {@snippet lang=text :
 * // @link region=1 substring="InterfaceMethodRefEntry" target="ConstantPoolBuilder#interfaceMethodRefEntry(ClassEntry, NameAndTypeEntry)"
 * // @link substring="ClassEntry owner" target="#owner()" :
 * InterfaceMethodRefEntry(ClassEntry owner, NameAndTypeEntry) // @link substring="NameAndTypeEntry" target="#nameAndType()"
 * // @end region=1
 * }
 * where the type in the {@code NameAndTypeEntry} is a {@linkplain #typeSymbol()
 * method descriptor} string.
 *
 * @see ConstantPoolBuilder#interfaceMethodRefEntry
 *      ConstantPoolBuilder::interfaceMethodRefEntry
 * @jvms 4.4.2 The {@code CONSTANT_Fieldref_info}, {@code
 *             CONSTANT_Methodref_info}, and {@code
 *             CONSTANT_InterfaceMethodref_info} Structures
 * @since 24
 */
public sealed interface InterfaceMethodRefEntry
        extends MemberRefEntry
        permits AbstractPoolEntry.InterfaceMethodRefEntryImpl {

    /**
     * {@return a symbolic descriptor for the {@linkplain #type() method type}}
     */
    default MethodTypeDesc typeSymbol() {
        return Util.methodTypeSymbol(type());
    }
}
