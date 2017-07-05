/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * An extension of BinaryCode that allows code to be printed.
 * Includes printing of disassembled byte codes, exception info,
 * local variable and line number info.
 *
 */

package ilib;

import java.io.ByteArrayInputStream;
import java.io.CharArrayWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.PrintStream;
import java.util.Map;
import java.util.HashMap;

class InjectBytecodes implements RuntimeConstants {

    private final ClassReaderWriter c;
    private final PrintStream output;
    private final int length;
    private final int[] map;
    private final byte[] widening;
    private final Injector[] before = new Injector[256];
    private final Injector[] after  = new Injector[256];
    private final String className;
    private final String methodName;
    private final Map<Integer,byte[]> snippets = new HashMap<>();

    private int pos;
    private int newPos;

    private class Span {
        final int delta;
        final int target;
        final int newDelta;
        final int newTarget;

        Span(int delta) {
            this.delta = delta;
            this.target = pos + delta;
            this.newTarget = map[target];
            this.newDelta = newTarget - newPos;
        }
    }

    /**
     * Constructor
     */
    InjectBytecodes(ClassReaderWriter c, int length,
                    String className, String methodName) {
        this.c = c;
        this.output = System.out;
        this.length = length;
        this.map = new int[length + 1];
        this.widening = new byte[length + 1];
        this.className = className;
        this.methodName = methodName;
        c.markLocalPositionStart();
        for (int i = 0; i <= length; ++i) {
            map[i] = i;
        }
    }

    public void inject(int at, byte[] newCode) {
        snippets.put(new Integer(at), newCode);
        trace("external ");
        inject(at, newCode.length);
    }

    private void inject(int at, int len) {
        if (Inject.verbose) {
            traceln("Injecting " + len + " at " + at);
        }
        for (int i = at; i <= length; ++i) {
            map[i] += len;
        }
    }

    private void widen(int at, int len) {
        int delta = len - widening[at];
        if (Inject.verbose) {
            traceln();
            traceln("Widening to " + len + " at " + at);
        }
        inject(c.localPosition(), delta);  // inject at end of instruction
        widening[at] = (byte)len;          // mark at beginning of instruction
    }

    public void injectBefore(int code, Injector inj) {
        before[code] = inj;
    }

    public void injectAfter(int code, Injector inj) {
        after[code] = inj;
    }

    private void trace(String str) {
        if (Inject.verbose) {
            output.print(str);
        }
    }

    private void traceln(String str) {
        if (Inject.verbose) {
            output.println(str);
        }
    }

    private void traceln() {
        if (Inject.verbose) {
            output.println();
        }
    }

    private void trace(int i) {
        if (Inject.verbose) {
            output.print(i);
        }
    }

    /**
     * Print an integer so that it takes 'length' characters in
     * the output.  Temporary until formatting code is stable.
     */
    private void traceFixedWidthInt(int x, int length) {
        if (Inject.verbose) {
            CharArrayWriter baStream = new CharArrayWriter();
            PrintWriter pStream = new PrintWriter(baStream);
            pStream.print(x);
            String str = baStream.toString();
            for (int cnt = length - str.length(); cnt > 0; --cnt)
                trace(" ");
            trace(str);
        }
    }

    void adjustOffsets() throws IOException {
        if (Inject.verbose) {
            traceln();
            traceln("Method " + methodName);
            traceln();
        }
        c.rewind();
        while (c.localPosition() < length) {
            insertAtInstruction();
        }
        trace("Searching for adjustments...");
        c.rewind();
        while (c.localPosition() < length) {
            if (!adjustInstruction()) {
                c.rewind();
                traceln();
                traceln("Restarting adjustments after change...");
            }
        }
        // write the new bytecodes
        traceln();
        traceln();
        trace("Writing new code...");
        c.rewind();
        while (c.localPosition() < length) {
            writeInstruction();
        }
        if (!snippets.isEmpty()) {
            throw new Error("not all snippets written");
        }
    }

    /**
     * Walk one instruction inserting instrumentation at specified instructions
     */
    private void insertAtInstruction() throws IOException {
        pos = c.localPosition();
        int opcode = c.readU1();
        if (opcode == opc_wide) {
            // no support for instrumenting wide instructions
            int wopcode = c.readU1();
            int lvIndex = c.readU2();
            switch (wopcode) {
            case opc_aload: case opc_astore:
            case opc_fload: case opc_fstore:
            case opc_iload: case opc_istore:
            case opc_lload: case opc_lstore:
            case opc_dload: case opc_dstore:
            case opc_ret:
                break;

            case opc_iinc:
                c.readS2();
                break;
            default:
                throw new Error("Invalid wide opcode: " + wopcode);
            }
        } else {
            Injector inj;

            inj = before[opcode];
            if (inj != null) {
                inject(pos, inj.bytecodes(className, methodName, pos));
            }

            switch (opcode) {
            case opc_tableswitch:{
                int header = (pos+1+3) & (~3);        // 4byte boundry
                c.skip(header - (pos+1));             // skip old padding

                c.readU4();
                int low = c.readU4();
                int high = c.readU4();
                c.skip((high+1-low) * 4);
                break;
            }

            case opc_lookupswitch:{
                int header = (pos+1+3) & (~3);        // 4byte boundry
                c.skip(header - (pos+1));             // skip padding

                c.readU4();
                int npairs = c.readU4();
                c.skip(npairs * 8);
                break;
            }

            default: {
                int instrLen = opcLengths[opcode];
                c.skip(instrLen-1);
            }
            }
            inj = after[opcode];
            if (inj != null) {
                pos = c.localPosition();
                inject(pos, inj.bytecodes(className, methodName, pos));
            }
        }
    }

    /**
     * Walk one instruction adjusting for insertions
     */
    private boolean adjustInstruction() throws IOException {
        pos = c.localPosition();
        newPos = map[pos];
        int opcode = c.readU1();
        if (Inject.verbose) {
            traceln();
            traceFixedWidthInt(pos, 4);
            traceFixedWidthInt(newPos, 4);
            trace(" ");
        }
        if (opcode == opc_wide) {
            int wopcode = c.readU1();
            int lvIndex = c.readU2();
            if (Inject.verbose) {
                trace(opcNames[wopcode] + "_w ");
            }
            switch (wopcode) {
            case opc_aload: case opc_astore:
            case opc_fload: case opc_fstore:
            case opc_iload: case opc_istore:
            case opc_lload: case opc_lstore:
            case opc_dload: case opc_dstore:
            case opc_ret:
                trace(lvIndex);
                break;

            case opc_iinc:
                int constVal = c.readS2();
                if (Inject.verbose) {
                    trace(lvIndex + " " + constVal);
                }
                break;
            default:
                throw new Error("Invalid wide opcode: " + wopcode);
            }
        } else {
            if (Inject.verbose) {
                trace(opcNames[opcode]);
            }
            switch (opcode) {

            case opc_tableswitch:{
                int widened = widening[pos];
                int header = (pos+1+3) & (~3);        // 4byte boundry
                int newHeader = (newPos+1+3) & (~3);  // 4byte boundry

                c.skip(header - (pos+1));             // skip old padding

                Span defaultSkip = new Span(c.readU4());
                int low = c.readU4();
                int high = c.readU4();
                if (Inject.verbose) {
                    trace(" " + low + " to " + high);
                    trace(": default= [was] " + defaultSkip.target);
                    trace(" [now] " + defaultSkip.newTarget);
                    for (int i = low; i <= high; ++i) {
                        Span jump = new Span(c.readU4());
                        traceln("");
                        trace('\t');
                        traceFixedWidthInt(i, 5);
                        trace(": " + jump.newTarget);
                    }
                } else {
                    c.skip((high+1-low) * 4);
                }
                int newPadding = newHeader - newPos;
                int oldPadding = header - pos;
                int deltaPadding = newPadding - oldPadding;
                if (widened != deltaPadding) {
                    widen(pos, deltaPadding);
                    return false;       // cause restart
                }
                break;
            }

            case opc_lookupswitch:{
                int widened = widening[pos];
                int header = (pos+1+3) & (~3);        // 4byte boundry
                int newHeader = (newPos+1+3) & (~3);  // 4byte boundry

                c.skip(header - (pos+1));             // skip old padding

                Span defaultSkip = new Span(c.readU4());
                int npairs = c.readU4();
                if (Inject.verbose) {
                    trace(" npairs: " + npairs);
                    trace(": default= [was] " + defaultSkip.target);
                    trace(" [now] " + defaultSkip.newTarget);
                    for (int i = 0; i< npairs; ++i) {
                        int match = c.readU4();
                        Span jump = new Span(c.readU4());
                        traceln("");
                        trace('\t');
                        traceFixedWidthInt(match, 5);
                        trace(": " + jump.newTarget);
                    }
                } else {
                    c.skip(npairs * 8);
                }
                int newPadding = newHeader - newPos;
                int oldPadding = header - pos;
                int deltaPadding = newPadding - oldPadding;
                if (widened != deltaPadding) {
                    widen(pos, deltaPadding);
                    return false;       // cause restart
                }
                break;
            }

            case opc_jsr: case opc_goto:
            case opc_ifeq: case opc_ifge: case opc_ifgt:
            case opc_ifle: case opc_iflt: case opc_ifne:
            case opc_if_icmpeq: case opc_if_icmpne: case opc_if_icmpge:
            case opc_if_icmpgt: case opc_if_icmple: case opc_if_icmplt:
            case opc_if_acmpeq: case opc_if_acmpne:
            case opc_ifnull: case opc_ifnonnull: {
                int widened = widening[pos];
                Span jump = new Span(c.readS2());
                if (widened == 0) {  // not yet widened
                    int newDelta = jump.newDelta;
                    if ((newDelta < -32768) || (newDelta > 32767)) {
                        switch (opcode) {
                        case opc_jsr: case opc_goto:
                            widen(pos, 2); // will convert to wide
                            break;
                        default:
                            widen(pos, 5); // will inject goto_w
                            break;
                        }
                        return false;       // cause restart
                    }
                }
                if (Inject.verbose) {
                    trace(" [was] " + jump.target + " ==> " +
                          " [now] " + jump.newTarget);
                }
                break;
            }

            case opc_jsr_w:
            case opc_goto_w: {
                Span jump = new Span(c.readU4());
                if (Inject.verbose) {
                    trace(" [was] " + jump.target +
                          " [now] " + jump.newTarget);
                }
                break;
            }

            default: {
                int instrLen = opcLengths[opcode];
                c.skip(instrLen-1);
                break;
            }
            }
        }
        return true;     // successful return
    }


    /**
     * Walk one instruction writing the transformed instruction.
     */
    private void writeInstruction() throws IOException {
        pos = c.localPosition();
        newPos = map[pos];
        byte[] newCode = snippets.remove(new Integer(pos));
        if (newCode != null) {
            traceln();
            traceFixedWidthInt(pos, 4);
            trace(" ... -- Inserting new code");
            c.writeBytes(newCode);
        }
        int opcode = c.readU1();
        if (Inject.verbose) {
            traceln();
            traceFixedWidthInt(pos, 4);
            traceFixedWidthInt(newPos, 4);
            trace(" ");
        }
        if (opcode == opc_wide) {
            int wopcode = c.readU1();
            int lvIndex = c.readU2();
            if (Inject.verbose) {
                trace(opcNames[wopcode] + "_w ");
            }
            c.writeU1(opcode);
            c.writeU1(wopcode);
            c.writeU2(lvIndex);
            switch (wopcode) {
            case opc_aload: case opc_astore:
            case opc_fload: case opc_fstore:
            case opc_iload: case opc_istore:
            case opc_lload: case opc_lstore:
            case opc_dload: case opc_dstore:
            case opc_ret:
                trace(lvIndex);
                break;

            case opc_iinc:
                int constVal = c.readS2();
                c.writeU2(constVal);  // ??? U vs S
                if (Inject.verbose) {
                    trace(lvIndex + " " + constVal);
                }
                break;
            default:
                throw new Error("Invalid wide opcode: " + wopcode);
            }
        } else {
            if (Inject.verbose) {
                trace(opcNames[opcode]);
            }
            switch (opcode) {

            case opc_tableswitch:{
                int header = (pos+1+3) & (~3);   // 4byte boundry
                int newHeader = (newPos+1+3) & (~3); // 4byte boundry

                c.skip(header - (pos+1));             // skip old padding

                Span defaultSkip = new Span(c.readU4());
                int low = c.readU4();
                int high = c.readU4();

                c.writeU1(opcode);                   // copy instruction
                for (int i = newPos+1; i < newHeader; ++i) {
                    c.writeU1(0);                    // write new padding
                }
                c.writeU4(defaultSkip.newDelta);
                c.writeU4(low);
                c.writeU4(high);

                if (Inject.verbose) {
                    trace(" " + low + " to " + high);
                    trace(": default= [was] " + defaultSkip.target);
                    trace(" [now] " + defaultSkip.newTarget);
                }
                for (int i = low; i <= high; ++i) {
                    Span jump = new Span(c.readU4());
                    c.writeU4(jump.newDelta);
                    if (Inject.verbose) {
                        traceln("");
                        trace('\t');
                        traceFixedWidthInt(i, 5);
                        trace(": " + jump.newTarget);
                    }
                }
                break;
            }

            case opc_lookupswitch:{
                int header = (pos+1+3) & (~3);   // 4byte boundry
                int newHeader = (newPos+1+3) & (~3); // 4byte boundry

                c.skip(header - (pos+1));             // skip old padding

                Span defaultSkip = new Span(c.readU4());
                int npairs = c.readU4();
                if (Inject.verbose) {
                    trace(" npairs: " + npairs);
                    trace(": default= [was] " + defaultSkip.target);
                    trace(" [now] " + defaultSkip.newTarget);
                }
                c.writeU1(opcode);                   // copy instruction
                for (int i = newPos+1; i < newHeader; ++i) {
                    c.writeU1(0);                    // write new padding
                }
                c.writeU4(defaultSkip.newDelta);
                c.writeU4(npairs);
                for (int i = 0; i< npairs; ++i) {
                    int match = c.readU4();
                    Span jump = new Span(c.readU4());
                    c.writeU4(match);
                    c.writeU4(jump.newDelta);
                    if (Inject.verbose) {
                        traceln("");
                        trace('\t');
                        traceFixedWidthInt(match, 5);
                        trace(": " + jump.newTarget);
                    }
                }
                break;
            }

            case opc_jsr: case opc_goto:
            case opc_ifeq: case opc_ifge: case opc_ifgt:
            case opc_ifle: case opc_iflt: case opc_ifne:
            case opc_if_icmpeq: case opc_if_icmpne: case opc_if_icmpge:
            case opc_if_icmpgt: case opc_if_icmple: case opc_if_icmplt:
            case opc_if_acmpeq: case opc_if_acmpne:
            case opc_ifnull: case opc_ifnonnull: {
                int widened = widening[pos];
                Span jump = new Span(c.readS2());
                int newOpcode = opcode;   // default to unchanged
                if (widened == 0) {        // not widened
                    c.writeU1(opcode);    // rewrite instruction
                    c.writeU2(jump.newDelta);
                } else if (widened == 2) { // wide form
                    switch (opcode) {
                    case opc_jsr:
                        newOpcode = opc_jsr_w;
                        break;
                    case opc_goto:
                        newOpcode = opc_jsr_w;
                        break;
                    default:
                        throw new Error("unexpected opcode: " +
                                   opcode);
                    }
                    c.writeU1(newOpcode);      // write wide instruction
                    c.writeU4(jump.newDelta);  // write new and wide delta
                } else if (widened == 5) {      // insert goto_w
                    switch (opcode) {
                    case opc_ifeq:
                        newOpcode = opc_ifne;
                        break;
                    case opc_ifge:
                        newOpcode = opc_iflt;
                        break;
                    case opc_ifgt:
                        newOpcode = opc_ifle;
                        break;
                    case opc_ifle:
                        newOpcode = opc_ifgt;
                        break;
                    case opc_iflt:
                        newOpcode = opc_ifge;
                        break;
                    case opc_ifne:
                        newOpcode = opc_ifeq;
                        break;
                    case opc_if_icmpeq:
                        newOpcode = opc_if_icmpne;
                        break;
                    case opc_if_icmpne:
                        newOpcode = opc_if_icmpeq;
                        break;
                    case opc_if_icmpge:
                        newOpcode = opc_if_icmplt;
                        break;
                    case opc_if_icmpgt:
                        newOpcode = opc_if_icmple;
                        break;
                    case opc_if_icmple:
                        newOpcode = opc_if_icmpgt;
                        break;
                    case opc_if_icmplt:
                        newOpcode = opc_if_icmpge;
                        break;
                    case opc_if_acmpeq:
                        newOpcode = opc_if_acmpne;
                        break;
                    case opc_if_acmpne:
                        newOpcode = opc_if_acmpeq;
                        break;
                    case opc_ifnull:
                        newOpcode = opc_ifnonnull;
                        break;
                    case opc_ifnonnull:
                        newOpcode = opc_ifnull;
                        break;
                    default:
                        throw new Error("unexpected opcode: " +
                                   opcode);
                    }
                    c.writeU1(newOpcode); // write inverse branch
                    c.writeU2(3 + 5);     // beyond if and goto_w
                    c.writeU1(opc_goto_w);// add a goto_w
                    c.writeU4(jump.newDelta);  // write new and wide delta
                } else {
                    throw new Error("unexpected widening");
                }

                if (Inject.verbose) {
                    trace(" [was] " + jump.target + " ==> " +
                          opcNames[newOpcode] +
                          " [now] " + jump.newTarget);
                }
                break;
            }

            case opc_jsr_w:
            case opc_goto_w: {
                Span jump = new Span(c.readU4());
                c.writeU1(opcode);        // instruction itself
                c.writeU4(jump.newDelta);
                if (Inject.verbose) {
                    trace(" [was] " + jump.target +
                          " [now] " + jump.newTarget);
                }
                break;
            }

            default: {
                int instrLen = opcLengths[opcode];
                c.writeU1(opcode);        // instruction itself
                c.copy(instrLen-1);
            }
            }
        }
    }

    /**
     * Copy the exception table for this method code
     */
    void copyExceptionTable() throws IOException {
        int tableLength = c.copyU2();   // exception table len
        if (tableLength > 0) {
            traceln();
            traceln("Exception table:");
            traceln(" from:old/new  to:old/new target:old/new type");
            for (int tcnt = tableLength; tcnt > 0; --tcnt) {
                int startPC = c.readU2();
                int newStartPC = map[startPC];
                c.writeU2(newStartPC);
                int endPC = c.readU2();
                int newEndPC = map[endPC];
                c.writeU2(newEndPC);
                int handlerPC = c.readU2();
                int newHandlerPC = map[handlerPC];
                c.writeU2(newHandlerPC);
                int catchType = c.copyU2();
                if (Inject.verbose) {
                    traceFixedWidthInt(startPC, 6);
                    traceFixedWidthInt(newStartPC, 6);
                    traceFixedWidthInt(endPC, 6);
                    traceFixedWidthInt(newEndPC, 6);
                    traceFixedWidthInt(handlerPC, 6);
                    traceFixedWidthInt(newHandlerPC, 6);
                    trace("    ");
                    if (catchType == 0)
                        traceln("any");
                    else {
                        traceln("" + catchType);
                    }
                }
            }
        }
    }

    /**
     * Copy the line number table for this method code
     */
    void copyLineNumberAttr() throws IOException {
        // name index already read
        c.copy(4);                      // attr len
        int tableLength = c.copyU2();   // line table len
        if (tableLength > 0) {
            if (Inject.verbose) {
                traceln();
                traceln("Line numbers for method " + methodName);
            }
            for (int tcnt = tableLength; tcnt > 0; --tcnt) {
                int startPC = c.readU2();
                int newStartPC = map[startPC];
                c.writeU2(newStartPC);
                int lineNumber = c.copyU2();
                if (Inject.verbose) {
                    traceln("   line " + lineNumber +
                            ": [was] " + startPC +
                            " [now] " + newStartPC);
                }
            }
        }
    }

    /**
     * Copy the local variable table for this method code
     */
    void copyLocalVarAttr() throws IOException {
        // name index already read
        c.copy(4);                      // attr len
        int tableLength = c.copyU2();   // local var table len
        if (tableLength > 0) {
            if (Inject.verbose) {
                traceln();
                traceln("Local variables for method " + methodName);
            }
            for (int tcnt = tableLength; tcnt > 0; --tcnt) {
                int startPC = c.readU2();
                int newStartPC = map[startPC];
                c.writeU2(newStartPC);
                int length = c.readU2();
                int endPC = startPC + length;
                int newEndPC = map[endPC];
                int newLength = newEndPC - newStartPC;
                c.writeU2(newLength);
                int nameIndex = c.copyU2();
                int descriptorIndex = c.copyU2();
                int index = c.copyU2();
                if (Inject.verbose) {
                    trace("   ");
                    trace(descriptorIndex);
                    trace(" ");
                    trace(nameIndex);
                    traceln("  pc= [was] " + startPC + " [now] " + newStartPC +
                            ", length= [was] " + length + " [now] " + newLength +
                            ", slot=" + index);
                }
            }
        }
    }
}
