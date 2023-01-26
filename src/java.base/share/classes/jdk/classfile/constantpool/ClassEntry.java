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
 */
package jdk.classfile.constantpool;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.util.ArrayList;
import java.util.List;

import jdk.classfile.impl.ConcreteEntry;
import jdk.classfile.impl.TemporaryConstantPool;
import jdk.classfile.impl.Util;


/**
 * Models a {@code CONSTANT_Class_info} constant in the constant pool of a
 * classfile.
 */
public sealed interface ClassEntry
        extends LoadableConstantEntry
        permits ConcreteEntry.ConcreteClassEntry {

    @Override
    default ConstantDesc constantValue() {
        return asSymbol();
    }

    /**
     * {@return the UTF8 constant pool entry for the class name}
     */
    Utf8Entry name();

    /**
     * {@return the class name, as an internal binary name}
     */
    String asInternalName();

    /**
     * {@return the class name, as a symbolic descriptor}
     */
    ClassDesc asSymbol();

    /**
     * Return a List composed by appending the additions to the base list.
     * @param base The base elements for the list, must not include null
     * @param additions The ClassEntrys to add to the list, must not include null
     * @return the combined List
     */
    static List<ClassEntry> adding(List<ClassEntry> base, List<ClassEntry> additions) {
        ArrayList<ClassEntry> members = new ArrayList<>(base);
        members.addAll(additions);
        return List.copyOf(members);
    }

    /**
     * Return a List composed by appending the additions to the base list.
     * @param base The base elements for the list, must not include null
     * @param additions The ClassEntrys to add to the list, must not include null
     * @return the combined List
     */
    static List<ClassEntry> adding(List<ClassEntry> base, ClassEntry... additions) {
        ArrayList<ClassEntry> members = new ArrayList<>(base);
        for (ClassEntry e : additions) {
            members.add(e);
        }
        return List.copyOf(members);
    }

    /**
     * Return a List composed by appending the additions to the base list.
     * @param base The base elements for the list, must not include null
     * @param additions The ClassDescs to add to the list, must not include null
     * @return the combined List
     */
    static List<ClassEntry> addingSymbols(List<ClassEntry> base, List<ClassDesc> additions) {
        ArrayList<ClassEntry> members = new ArrayList<>(base);
        members.addAll(Util.entryList(additions));
        return List.copyOf(members);
    }

      /**
     * Return a List composed by appending the additions to the base list.
     * @param base The base elements for the list, must not include null
     * @param additions The ClassDescs to add to the list, must not include null
     * @return the combined List
     */
    static List<ClassEntry> addingSymbols(List<ClassEntry> base, ClassDesc...additions) {
        ArrayList<ClassEntry> members = new ArrayList<>(base);
        for (ClassDesc e : additions) {
            members.add(TemporaryConstantPool.INSTANCE.classEntry(TemporaryConstantPool.INSTANCE.utf8Entry(Util.toInternalName(e))));
        }
        return List.copyOf(members);
    }

    /**
     * Remove duplicate ClassEntry elements from the List.
     *
     * @param original The list to deduplicate
     * @return a List without any duplicate ClassEntry
     */
    static List<ClassEntry> deduplicate(List<ClassEntry> original) {
        ArrayList<ClassEntry> newList = new ArrayList<>(original.size());
        for (ClassEntry e : original) {
            if (!newList.contains(e)) {
                newList.add(e);
            }
        }
        return List.copyOf(newList);
    }
}
