/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.internal.classfile.Attributes;
import jdk.internal.classfile.Classfile;

import jdk.internal.classfile.Instruction;
import jdk.internal.classfile.attribute.CodeAttribute;
import jdk.internal.classfile.attribute.StackMapFrameInfo;
import jdk.internal.classfile.attribute.StackMapTableAttribute;

/**
 * Annotate instructions with stack map.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class StackMapWriter extends InstructionDetailWriter {
    static StackMapWriter instance(Context context) {
        StackMapWriter instance = context.get(StackMapWriter.class);
        if (instance == null)
            instance = new StackMapWriter(context);
        return instance;
    }

    protected StackMapWriter(Context context) {
        super(context);
        context.put(StackMapWriter.class, this);
    }

    public void reset(CodeAttribute code) {
        setStackMap(code);
    }

    void setStackMap(CodeAttribute code) {
        StackMapTableAttribute attr = code.findAttribute(Attributes.STACK_MAP_TABLE)
                .orElse(null);
        if (attr == null) {
            map = null;
            return;
        }
        var m = code.parent().get();
        if ((m.flags().flagsMask() & Classfile.ACC_STATIC) == 0) {
            thisClassName =  m.parent().get().thisClass().asInternalName();
        } else {
            thisClassName = null;
        }

        map = new HashMap<>();
        this.code = code;
        for (var fr : attr.entries())
            map.put(code.labelToBci(fr.target()), fr);
    }

    public void writeInitialDetails() {
        writeDetails(-1);
    }

    @Override
    public void writeDetails(int pc, Instruction instr) {
        writeDetails(pc);
    }

    private void writeDetails(int pc) {
        if (map == null)
            return;

        var m = map.get(pc);
        if (m != null) {
            print("StackMap locals: ", m.locals(), true);
            print("StackMap stack: ", m.stack(), false);
        }

    }

    void print(String label, List<StackMapFrameInfo.VerificationTypeInfo> entries,
            boolean firstThis) {
        print(label);
        for (var e : entries) {
            print(" ");
            print(e, firstThis);
            firstThis = false;
        }
        println();
    }

    void print(StackMapFrameInfo.VerificationTypeInfo entry, boolean firstThis) {
        if (entry == null) {
            print("ERROR");
            return;
        }

        switch (entry) {
            case StackMapFrameInfo.SimpleVerificationTypeInfo s -> {
                switch (s) {
                    case ITEM_TOP ->
                        print("top");

                    case ITEM_INTEGER ->
                        print("int");

                    case ITEM_FLOAT ->
                        print("float");

                    case ITEM_LONG ->
                        print("long");

                    case ITEM_DOUBLE ->
                        print("double");

                    case ITEM_NULL ->
                        print("null");

                    case ITEM_UNINITIALIZED_THIS ->
                        print("uninit_this");
                }
            }

            case StackMapFrameInfo.ObjectVerificationTypeInfo o -> {
                String cln = o.className().asInternalName();
                print(firstThis && cln.equals(thisClassName) ? "this" : cln);
            }

            case StackMapFrameInfo.UninitializedVerificationTypeInfo u ->
                print(code.labelToBci(u.newTarget()));
        }

    }

    private Map<Integer, StackMapFrameInfo> map;
    private String thisClassName;
    private CodeAttribute code;
}
