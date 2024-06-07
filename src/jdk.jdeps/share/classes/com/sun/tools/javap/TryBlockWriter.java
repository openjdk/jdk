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
import java.lang.classfile.Instruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.attribute.CodeAttribute;

/**
 * Annotate instructions with details about try blocks.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class TryBlockWriter extends InstructionDetailWriter {
    public enum NoteKind {
        START("try") {
            public boolean match(ExceptionCatch entry, int pc, CodeAttribute lr) {
                return (pc == lr.labelToBci(entry.tryStart()));
            }
        },
        END("end try") {
            public boolean match(ExceptionCatch entry, int pc, CodeAttribute lr) {
                return (pc == lr.labelToBci(entry.tryEnd()));
            }
        },
        HANDLER("catch") {
            public boolean match(ExceptionCatch entry, int pc, CodeAttribute lr) {
                return (pc == lr.labelToBci(entry.handler()));
            }
        };
        NoteKind(String text) {
            this.text = text;
        }
        public abstract boolean match(ExceptionCatch entry, int pc, CodeAttribute lr);
        public final String text;
    }

    static TryBlockWriter instance(Context context) {
        TryBlockWriter instance = context.get(TryBlockWriter.class);
        if (instance == null)
            instance = new TryBlockWriter(context);
        return instance;
    }

    protected TryBlockWriter(Context context) {
        super(context);
        context.put(TryBlockWriter.class, this);
        constantWriter = ConstantWriter.instance(context);
    }

    public void reset(CodeAttribute attr) {
        indexMap = new HashMap<>();
        pcMap = new HashMap<>();
        lr = attr;
        var excs = attr.exceptionHandlers();
        for (int i = 0; i < excs.size(); i++) {
            var entry = excs.get(i);
            indexMap.put(entry, i);
            put(lr.labelToBci(entry.tryStart()), entry);
            put(lr.labelToBci(entry.tryEnd()), entry);
            put(lr.labelToBci(entry.handler()), entry);
        }
    }

    @Override
    public void writeDetails(int pc, Instruction instr) {
        writeTrys(pc, instr, NoteKind.END);
        writeTrys(pc, instr, NoteKind.START);
        writeTrys(pc, instr, NoteKind.HANDLER);
    }

    public void writeTrys(int pc, Instruction instr, NoteKind kind) {
        String indent = space(2); // get from Options?
        var entries = pcMap.get(pc);
        if (entries != null) {
            for (var iter =
                    entries.listIterator(kind == NoteKind.END ? entries.size() : 0);
                    kind == NoteKind.END ? iter.hasPrevious() : iter.hasNext() ; ) {
                var entry =
                        kind == NoteKind.END ? iter.previous() : iter.next();
                if (kind.match(entry, pc, lr)) {
                    print(indent);
                    print(kind.text);
                    print("[");
                    print(indexMap.get(entry));
                    print("] ");
                    var ct = entry.catchType();
                    if (ct.isEmpty())
                        print("finally");
                    else {
                        print("#" + ct.get().index());
                        print(" // ");
                        constantWriter.write(ct.get().index());
                    }
                    println();
                }
            }
        }
    }

    private void put(int pc, ExceptionCatch entry) {
        var list = pcMap.get(pc);
        if (list == null) {
            list = new ArrayList<>();
            pcMap.put(pc, list);
        }
        if (!list.contains(entry))
            list.add(entry);
    }

    private Map<Integer, List<ExceptionCatch>> pcMap;
    private Map<ExceptionCatch, Integer> indexMap;
    private ConstantWriter constantWriter;
    private CodeAttribute lr;
}
