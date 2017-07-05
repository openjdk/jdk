/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.java.util.jar.pack;

import com.sun.java.util.jar.pack.ConstantPool.ClassEntry;
import com.sun.java.util.jar.pack.ConstantPool.DescriptorEntry;
import com.sun.java.util.jar.pack.ConstantPool.LiteralEntry;
import com.sun.java.util.jar.pack.ConstantPool.MemberEntry;
import com.sun.java.util.jar.pack.ConstantPool.MethodHandleEntry;
import com.sun.java.util.jar.pack.ConstantPool.MethodTypeEntry;
import com.sun.java.util.jar.pack.ConstantPool.InvokeDynamicEntry;
import com.sun.java.util.jar.pack.ConstantPool.BootstrapMethodEntry;
import com.sun.java.util.jar.pack.ConstantPool.SignatureEntry;
import com.sun.java.util.jar.pack.ConstantPool.Utf8Entry;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;

/*
 * @author ksrini
 */

/*
 * This class provides a container to hold the global variables, for packer
 * and unpacker instances. This is typically stashed away in a ThreadLocal,
 * and the storage is destroyed upon completion. Therefore any local
 * references to these members must be eliminated appropriately to prevent a
 * memory leak.
 */
class TLGlobals {
    // Global environment
    final PropMap props;

    // Needed by ConstantPool.java
    private final Map<String, Utf8Entry> utf8Entries;
    private final Map<String, ClassEntry> classEntries;
    private final Map<Object, LiteralEntry> literalEntries;
    private final Map<String, SignatureEntry> signatureEntries;
    private final Map<String, DescriptorEntry> descriptorEntries;
    private final Map<String, MemberEntry> memberEntries;
    private final Map<String, MethodHandleEntry> methodHandleEntries;
    private final Map<String, MethodTypeEntry> methodTypeEntries;
    private final Map<String, InvokeDynamicEntry> invokeDynamicEntries;
    private final Map<String, BootstrapMethodEntry> bootstrapMethodEntries;

    TLGlobals() {
        utf8Entries = new HashMap<>();
        classEntries = new HashMap<>();
        literalEntries = new HashMap<>();
        signatureEntries = new HashMap<>();
        descriptorEntries = new HashMap<>();
        memberEntries = new HashMap<>();
        methodHandleEntries = new HashMap<>();
        methodTypeEntries = new HashMap<>();
        invokeDynamicEntries = new HashMap<>();
        bootstrapMethodEntries = new HashMap<>();
        props = new PropMap();
    }

    SortedMap<String, String> getPropMap() {
        return props;
    }

    Map<String, Utf8Entry> getUtf8Entries() {
        return utf8Entries;
    }

    Map<String, ClassEntry> getClassEntries() {
        return classEntries;
    }

    Map<Object, LiteralEntry> getLiteralEntries() {
        return literalEntries;
    }

    Map<String, DescriptorEntry> getDescriptorEntries() {
         return descriptorEntries;
    }

    Map<String, SignatureEntry> getSignatureEntries() {
        return signatureEntries;
    }

    Map<String, MemberEntry> getMemberEntries() {
        return memberEntries;
    }

    Map<String, MethodHandleEntry> getMethodHandleEntries() {
        return methodHandleEntries;
    }

    Map<String, MethodTypeEntry> getMethodTypeEntries() {
        return methodTypeEntries;
    }

    Map<String, InvokeDynamicEntry> getInvokeDynamicEntries() {
        return invokeDynamicEntries;
    }

    Map<String, BootstrapMethodEntry> getBootstrapMethodEntries() {
        return bootstrapMethodEntries;
    }
}
