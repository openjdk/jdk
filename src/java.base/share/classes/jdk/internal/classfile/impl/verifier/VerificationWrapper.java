/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package jdk.internal.classfile.impl.verifier;

import java.util.LinkedList;
import java.util.List;

import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.DynamicConstantPoolEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.reflect.AccessFlag;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.LocalVariableInfo;
import java.lang.classfile.Attributes;
import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.CodeImpl;
import jdk.internal.classfile.impl.Util;

public final class VerificationWrapper {
    private final ClassModel clm;
    private final ConstantPoolWrapper cp;

    public VerificationWrapper(ClassModel clm) {
        this.clm = clm;
        this.cp = new ConstantPoolWrapper(clm.constantPool());
     }

    String thisClassName() {
        return clm.thisClass().asInternalName();
    }

    int majorVersion() {
        return clm.majorVersion();
    }

    String superclassName() {
        return clm.superclass().map(ClassEntry::asInternalName).orElse(null);
    }

    Iterable<String> interfaceNames() {
        return Util.mappedList(clm.interfaces(), ClassEntry::asInternalName);
    }

    Iterable<MethodWrapper> methods() {
        return clm.methods().stream().map(m -> new MethodWrapper(m)).toList();
    }

    boolean findField(String name, String sig) {
        for (var f : clm.fields())
            if (f.fieldName().stringValue().equals(name) && f.fieldType().stringValue().equals(sig))
                return true;
        return false;
    }

    class MethodWrapper {

        final MethodModel m;
        private final CodeImpl c;
        private final List<int[]> exc;

        MethodWrapper(MethodModel m) {
            this.m = m;
            this.c = (CodeImpl)m.code().orElse(null);
            exc = new LinkedList<>();
            if (c != null) c.iterateExceptionHandlers((start, end, handler, catchType) -> {
                exc.add(new int[] {start, end, handler, catchType});
            });
        }

        ConstantPoolWrapper constantPool() {
            return cp;
        }

        boolean isNative() {
            return m.flags().has(AccessFlag.NATIVE);
        }

        boolean isAbstract() {
            return m.flags().has(AccessFlag.ABSTRACT);
        }

        boolean isBridge() {
            return m.flags().has(AccessFlag.BRIDGE);
        }

        boolean isStatic() {
            return m.flags().has(AccessFlag.STATIC);
        }

        String name() {
            return m.methodName().stringValue();
        }

        int maxStack() {
            return c == null ? 0 : c.maxStack();
        }

        int maxLocals() {
            return c == null ? 0 : c.maxLocals();
        }

        String descriptor() {
            return m.methodType().stringValue();
        }

        int codeLength() {
            return c == null ? 0 : c.codeLength();
        }

        byte[] codeArray() {
            return c == null ? null : c.codeArray();
        }

        List<int[]> exceptionTable() {
            return exc;
        }

        List<LocalVariableInfo> localVariableTable() {
            var attro = c.findAttribute(Attributes.LOCAL_VARIABLE_TABLE);
            return attro.map(lvta -> lvta.localVariables()).orElse(List.of());
        }

        byte[] stackMapTableRawData() {
            var attro = c.findAttribute(Attributes.STACK_MAP_TABLE);
            return attro.map(attr -> ((BoundAttribute) attr).contents()).orElse(null);
        }

    }

    static class ConstantPoolWrapper {

        private final ConstantPool cp;

        ConstantPoolWrapper(ConstantPool cp) {
            this.cp = cp;
        }

        int entryCount() {
            return cp.size();
        }

        String classNameAt(int index) {
            return ((ClassEntry)cp.entryByIndex(index)).asInternalName();
        }

        String dynamicConstantSignatureAt(int index) {
            return ((DynamicConstantPoolEntry)cp.entryByIndex(index)).type().stringValue();
        }

        int tagAt(int index) {
            return cp.entryByIndex(index).tag();
        }

        private NameAndTypeEntry _refNameType(int index) {
            var e = cp.entryByIndex(index);
            return (e instanceof DynamicConstantPoolEntry de) ? de.nameAndType() :
                    e != null ? ((MemberRefEntry)e).nameAndType() : null;
        }

        String refNameAt(int index) {
            return _refNameType(index).name().stringValue();
        }

        String refSignatureAt(int index) {
            return _refNameType(index).type().stringValue();
        }

        int refClassIndexAt(int index) {
            return ((MemberRefEntry)cp.entryByIndex(index)).owner().index();
        }
    }
}
