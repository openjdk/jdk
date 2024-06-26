/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.classfile.Attributes;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Signature;
import java.lang.classfile.attribute.LocalVariableInfo;

/**
 * Annotate instructions with details about local variables.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class LocalVariableTableWriter extends InstructionDetailWriter {
    public enum NoteKind {
        START("start") {
            @Override
            public boolean match(LocalVariableInfo entry, int pc) {
                return (pc == entry.startPc());
            }
        },
        END("end") {
            @Override
            public boolean match(LocalVariableInfo entry, int pc) {
                return (pc == entry.startPc() + entry.length());
            }
        };
        NoteKind(String text) {
            this.text = text;
        }
        public abstract boolean match(LocalVariableInfo entry, int pc);
        public final String text;
    }

    static LocalVariableTableWriter instance(Context context) {
        LocalVariableTableWriter instance = context.get(LocalVariableTableWriter.class);
        if (instance == null)
            instance = new LocalVariableTableWriter(context);
        return instance;
    }

    protected LocalVariableTableWriter(Context context) {
        super(context);
        context.put(LocalVariableTableWriter.class, this);
        classWriter = ClassWriter.instance(context);
    }

    public void reset(CodeModel attr) {
        codeAttr = attr;
        pcMap = new HashMap<>();
        var lvt = attr.findAttribute(Attributes.localVariableTable());

        if (lvt.isEmpty())
            return;

        for (var entry : lvt.get().localVariables()) {
            put(entry.startPc(), entry);
            put(entry.startPc() + entry.length(), entry);
        }
    }

    @Override
    public void writeDetails(int pc, Instruction instr) {
        writeLocalVariables(pc, NoteKind.END);
        writeLocalVariables(pc, NoteKind.START);
    }

    @Override
    public void flush(int pc) {
        writeLocalVariables(pc, NoteKind.END);
    }

    public void writeLocalVariables(int pc, NoteKind kind) {
        String indent = space(2); // get from Options?
        var entries = pcMap.get(pc);
        if (entries != null) {
            for (var iter = entries.listIterator(kind == NoteKind.END ? entries.size() : 0);
                    kind == NoteKind.END ? iter.hasPrevious() : iter.hasNext() ; ) {
                var entry = kind == NoteKind.END ? iter.previous() : iter.next();
                if (kind.match(entry, pc)) {
                    print(indent);
                    print(kind.text);
                    print(" local ");
                    print(entry.slot());
                    print(" // ");
                    print(classWriter.sigPrinter.print(
                            Signature.parseFrom(entry.type().stringValue())));
                    print(" ");
                    print(entry.name().stringValue());
                    println();
                }
            }
        }
    }

    private void put(int pc, LocalVariableInfo entry) {
        var list = pcMap.get(pc);
        if (list == null) {
            list = new ArrayList<>();
            pcMap.put(pc, list);
        }
        if (!list.contains(entry))
            list.add(entry);
    }

    private ClassWriter classWriter;
    private CodeModel codeAttr;
    private Map<Integer, List<LocalVariableInfo>> pcMap;
}
