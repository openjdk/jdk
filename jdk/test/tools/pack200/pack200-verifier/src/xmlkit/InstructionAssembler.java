/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
package xmlkit; // -*- mode: java; indent-tabs-mode: nil -*-

import xmlkit.XMLKit.Element;
import java.util.HashMap;
/*
 * @author jrose
 */
abstract class InstructionAssembler extends InstructionSyntax {

    InstructionAssembler() {
    }

    public static String assemble(Element instructions, String pcAttrName,
            ClassSyntax.GetCPIndex getCPI) {
        int insCount = instructions.size();
        Element[] insElems = new Element[insCount];
        int[] elemToIndexMap;
        int[] insLocs;
        byte[] ops = new byte[insCount];
        int[] operands = new int[insCount];
        boolean[] isWide = new boolean[insCount];
        int[] branches;
        int[] branchInsLocs;
        HashMap<String, String> labels = new HashMap<String, String>();

        final int WIDE = 0xc4;
        final int GOTO = 0xa7;
        final int GOTO_W = 0xc8;
        final int GOTO_LEN = 3;
        final int GOTO_W_LEN = 5;
        assert ("wide".equals(bcNames[WIDE]));
        assert ("goto".equals(bcNames[GOTO]));
        assert ("goto_w".equals(bcNames[GOTO_W]));
        assert (bcFormats[GOTO].length() == GOTO_LEN);
        assert (bcFormats[GOTO_W].length() == GOTO_W_LEN);

        // Unpack instructions into temp. arrays, and find branches and labels.
        {
            elemToIndexMap = (pcAttrName != null) ? new int[insCount] : null;
            int[] buffer = operands;
            int id = 0;
            int branchCount = 0;
            for (int i = 0; i < insCount; i++) {
                Element ins = (Element) instructions.get(i);
                if (elemToIndexMap != null) {
                    elemToIndexMap[i] = (ins.getAttr(pcAttrName) != null ? id : -1);
                }
                String lab = ins.getAttr("pc");
                if (lab != null) {
                    labels.put(lab, String.valueOf(id));
                }
                int op = opCode(ins.getName());
                if (op < 0) {
                    assert (ins.getAttr(pcAttrName) != null
                            || ins.getName().equals("label"));
                    continue;  // delete PC holder element
                }
                if (op == WIDE) { //0xc4
                    isWide[id] = true;  // force wide format
                    continue;
                }
                if (bcFormats[op].indexOf('o') >= 0) {
                    buffer[branchCount++] = id;
                }
                if (bcFormats[op] == bcWideFormats[op]) {
                    isWide[id] = false;
                }
                insElems[id] = ins;
                ops[id] = (byte) op;
                id++;
            }
            insCount = id;  // maybe we deleted some wide prefixes, etc.
            branches = new int[branchCount + 1];
            System.arraycopy(buffer, 0, branches, 0, branchCount);
            branches[branchCount] = -1;  // sentinel
        }

        // Compute instruction sizes.  These sizes are final,
        // except for branch instructions, which may need lengthening.
        // Some instructions (ldc, bipush, iload, iinc) are automagically widened.
        insLocs = new int[insCount + 1];
        int loc = 0;
        for (int bn = 0, id = 0; id < insCount; id++) {
            insLocs[id] = loc;
            Element ins = insElems[id];
            int op = ops[id] & 0xFF;
            String format = opFormat(op, isWide[id]);
            // Make sure operands fit within the given format.
            for (int j = 1, jlimit = format.length(); j < jlimit; j++) {
                char fc = format.charAt(j);
                int x = 0;
                switch (fc) {
                    case 'l':
                        x = (int) ins.getAttrLong("loc");
                        assert (x >= 0);
                        if (x > 0xFF && !isWide[id]) {
                            isWide[id] = true;
                            format = opFormat(op, isWide[id]);
                        }
                        assert (x <= 0xFFFF);
                        break;
                    case 'k':
                        char fc2 = format.charAt(Math.min(j + 1, format.length() - 1));
                        x = getCPIndex(ins, fc2, getCPI);
                        if (x > 0xFF && j == jlimit - 1) {
                            assert (op == 0x12); //ldc
                            ops[id] = (byte) (op = 0x13); //ldc_w
                            format = opFormat(op);
                        }
                        assert (x <= 0xFFFF);
                        j++;  // skip type-of-constant marker
                        break;
                    case 'x':
                        x = (int) ins.getAttrLong("num");
                        assert (x >= 0 && x <= ((j == jlimit - 1) ? 0xFF : 0xFFFF));
                        break;
                    case 's':
                        x = (int) ins.getAttrLong("num");
                        if (x != (byte) x && j == jlimit - 1) {
                            switch (op) {
                                case 0x10: //bipush
                                    ops[id] = (byte) (op = 0x11); //sipush
                                    break;
                                case 0x84: //iinc
                                    isWide[id] = true;
                                    format = opFormat(op, isWide[id]);
                                    break;
                                default:
                                    assert (false);  // cannot lengthen
                            }
                        }
                        // unsign the value now, to make later steps clearer
                        if (j == jlimit - 1) {
                            assert (x == (byte) x);
                            x = x & 0xFF;
                        } else {
                            assert (x == (short) x);
                            x = x & 0xFFFF;
                        }
                        break;
                    case 'o':
                        assert (branches[bn] == id);
                        bn++;
                        // make local copies of the branches, and fix up labels
                        insElems[id] = ins = new Element(ins);
                        String newLab = labels.get(ins.getAttr("lab"));
                        assert (newLab != null);
                        ins.setAttr("lab", newLab);
                        int prevCas = 0;
                        int k = 0;
                        for (Element cas : ins.elements()) {
                            assert (cas.getName().equals("Case"));
                            ins.set(k++, cas = new Element(cas));
                            newLab = labels.get(cas.getAttr("lab"));
                            assert (newLab != null);
                            cas.setAttr("lab", newLab);
                            int thisCas = (int) cas.getAttrLong("num");
                            assert (op == 0xab
                                    || op == 0xaa && (k == 0 || thisCas == prevCas + 1));
                            prevCas = thisCas;
                        }
                        break;
                    case 't':
                        // switch table is represented as Switch.Case sub-elements
                        break;
                    default:
                        assert (false);
                }
                operands[id] = x;  // record operand (last if there are 2)
                // skip redundant chars
                while (j + 1 < jlimit && format.charAt(j + 1) == fc) {
                    ++j;
                }
            }

            switch (op) {
                case 0xaa: //tableswitch
                    loc = switchBase(loc);
                    loc += 4 * (3 + ins.size());
                    break;
                case 0xab: //lookupswitch
                    loc = switchBase(loc);
                    loc += 4 * (2 + 2 * ins.size());
                    break;
                default:
                    if (isWide[id]) {
                        loc++;  // 'wide' opcode prefix
                    }
                    loc += format.length();
                    break;
            }
        }
        insLocs[insCount] = loc;

        // compute branch offsets, and see if any branches need expansion
        for (int maxTries = 9, tries = 0;; ++tries) {
            boolean overflowing = false;
            boolean[] branchExpansions = null;
            for (int bn = 0; bn < branches.length - 1; bn++) {
                int id = branches[bn];
                Element ins = insElems[id];
                int insSize = insLocs[id + 1] - insLocs[id];
                int origin = insLocs[id];
                int target = insLocs[(int) ins.getAttrLong("lab")];
                int offset = target - origin;
                operands[id] = offset;
                //System.out.println("branch id="+id+" len="+insSize+" to="+target+" offset="+offset);
                assert (insSize == GOTO_LEN || insSize == GOTO_W_LEN || ins.getName().indexOf("switch") > 0);
                boolean thisOverflow = (insSize == GOTO_LEN && (offset != (short) offset));
                if (thisOverflow && !overflowing) {
                    overflowing = true;
                    branchExpansions = new boolean[branches.length];
                }
                if (thisOverflow || tries == maxTries - 1) {
                    // lengthen the branch
                    assert (!(thisOverflow && isWide[id]));
                    isWide[id] = true;
                    branchExpansions[bn] = true;
                }
            }
            if (!overflowing) {
                break;  // done, usually on first try
            }
            assert (tries <= maxTries);

            // Walk over all instructions, expanding branches and updating locations.
            int fixup = 0;
            for (int bn = 0, id = 0; id < insCount; id++) {
                insLocs[id] += fixup;
                if (branches[bn] == id) {
                    int op = ops[id] & 0xFF;
                    int wop;
                    boolean invert;
                    if (branchExpansions[bn]) {
                        switch (op) {
                            case GOTO: //0xa7
                                wop = GOTO_W; //0xc8
                                invert = false;
                                break;
                            case 0xa8: //jsr
                                wop = 0xc9; //jsr_w
                                invert = false;
                                break;
                            default:
                                wop = invertBranchOp(op);
                                invert = true;
                                break;
                        }
                        assert (op != wop);
                        ops[id] = (byte) wop;
                        isWide[id] = invert;
                        if (invert) {
                            fixup += GOTO_W_LEN;  //branch around a wide goto
                        } else {
                            fixup += (GOTO_W_LEN - GOTO_LEN);
                        }
                        // done expanding:  ops and isWide reflect the decision
                    }
                    bn++;
                }
            }
            insLocs[insCount] += fixup;
        }
        // we know the layout now

        // notify the caller of offsets, if requested
        if (elemToIndexMap != null) {
            for (int i = 0; i < elemToIndexMap.length; i++) {
                int id = elemToIndexMap[i];
                if (id >= 0) {
                    Element ins = (Element) instructions.get(i);
                    ins.setAttr(pcAttrName, "" + insLocs[id]);
                }
            }
            elemToIndexMap = null;  // release the pointer
        }

        // output the bytes
        StringBuffer sbuf = new StringBuffer(insLocs[insCount]);
        for (int bn = 0, id = 0; id < insCount; id++) {
            //System.out.println("output id="+id+" loc="+insLocs[id]+" len="+(insLocs[id+1]-insLocs[id])+" #sbuf="+sbuf.length());
            assert (sbuf.length() == insLocs[id]);
            Element ins;
            int pc = insLocs[id];
            int nextpc = insLocs[id + 1];
            int op = ops[id] & 0xFF;
            int opnd = operands[id];
            String format;
            if (branches[bn] == id) {
                bn++;
                sbuf.append((char) op);
                if (isWide[id]) {
                    // emit <ifop lab=1f> <goto_w target> <label pc=1f>
                    int target = pc + opnd;
                    putInt(sbuf, nextpc - pc, -2);
                    assert (sbuf.length() == pc + GOTO_LEN);
                    sbuf.append((char) GOTO_W);
                    putInt(sbuf, target - (pc + GOTO_LEN), 4);
                } else if (op == 0xaa || //tableswitch
                        op == 0xab) {  //lookupswitch
                    ins = insElems[id];
                    for (int pad = switchBase(pc) - (pc + 1); pad > 0; pad--) {
                        sbuf.append((char) 0);
                    }
                    assert (pc + opnd == insLocs[(int) ins.getAttrLong("lab")]);
                    putInt(sbuf, opnd, 4); // default label
                    if (op == 0xaa) {  //tableswitch
                        Element cas0 = (Element) ins.get(0);
                        int lowCase = (int) cas0.getAttrLong("num");
                        Element casN = (Element) ins.get(ins.size() - 1);
                        int highCase = (int) casN.getAttrLong("num");
                        assert (highCase - lowCase + 1 == ins.size());
                        putInt(sbuf, lowCase, 4);
                        putInt(sbuf, highCase, 4);
                        int caseForAssert = lowCase;
                        for (Element cas : ins.elements()) {
                            int target = insLocs[(int) cas.getAttrLong("lab")];
                            assert (cas.getAttrLong("num") == caseForAssert++);
                            putInt(sbuf, target - pc, 4);
                        }
                    } else {  //lookupswitch
                        int caseCount = ins.size();
                        putInt(sbuf, caseCount, 4);
                        for (Element cas : ins.elements()) {
                            int target = insLocs[(int) cas.getAttrLong("lab")];
                            putInt(sbuf, (int) cas.getAttrLong("num"), 4);
                            putInt(sbuf, target - pc, 4);
                        }
                    }
                    assert (nextpc == sbuf.length());
                } else {
                    putInt(sbuf, opnd, -(nextpc - (pc + 1)));
                }
            } else if (nextpc == pc + 1) {
                // a single-byte instruction
                sbuf.append((char) op);
            } else {
                // picky stuff
                boolean wide = isWide[id];
                if (wide) {
                    sbuf.append((char) WIDE);
                    pc++;
                }
                sbuf.append((char) op);
                int opnd1;
                int opnd2 = opnd;
                switch (op) {
                    case 0x84:  //iinc
                        ins = insElems[id];
                        opnd1 = (int) ins.getAttrLong("loc");
                        if (isWide[id]) {
                            putInt(sbuf, opnd1, 2);
                            putInt(sbuf, opnd2, 2);
                        } else {
                            putInt(sbuf, opnd1, 1);
                            putInt(sbuf, opnd2, 1);
                        }
                        break;
                    case 0xc5: //multianewarray
                        ins = insElems[id];
                        opnd1 = getCPIndex(ins, 'c', getCPI);
                        putInt(sbuf, opnd1, 2);
                        putInt(sbuf, opnd2, 1);
                        break;
                    case 0xb9: //invokeinterface
                        ins = insElems[id];
                        opnd1 = getCPIndex(ins, 'n', getCPI);
                        putInt(sbuf, opnd1, 2);
                        opnd2 = (int) ins.getAttrLong("num");
                        if (opnd2 == 0) {
                            opnd2 = ClassSyntax.computeInterfaceNum(ins.getAttr("val"));
                        }
                        putInt(sbuf, opnd2, 2);
                        break;
                    default:
                        // put the single operand and be done
                        putInt(sbuf, opnd, nextpc - (pc + 1));
                        break;
                }
            }
        }
        assert (sbuf.length() == insLocs[insCount]);

        return sbuf.toString();
    }

    static int getCPIndex(Element ins, char ctype,
            ClassSyntax.GetCPIndex getCPI) {
        int x = (int) ins.getAttrLong("ref");
        if (x == 0 && getCPI != null) {
            String val = ins.getAttr("val");
            if (val == null || val.equals("")) {
                val = ins.getText().toString();
            }
            byte tag;
            switch (ctype) {
                case 'k':
                    tag = (byte) ins.getAttrLong("tag");
                    break;
                case 'c':
                    tag = ClassSyntax.CONSTANT_Class;
                    break;
                case 'f':
                    tag = ClassSyntax.CONSTANT_Fieldref;
                    break;
                case 'm':
                    tag = ClassSyntax.CONSTANT_Methodref;
                    break;
                case 'n':
                    tag = ClassSyntax.CONSTANT_InterfaceMethodref;
                    break;
                default:
                    throw new Error("bad ctype " + ctype + " in " + ins);
            }
            x = getCPI.getCPIndex(tag, val);
            //System.out.println("getCPIndex "+ins+" => "+tag+"/"+val+" => "+x);
        } else {
            assert (x > 0);
        }
        return x;
    }

    static void putInt(StringBuffer sbuf, int x, int len) {
        //System.out.println("putInt x="+x+" len="+len);
        boolean isSigned = false;
        if (len < 0) {
            len = -len;
            isSigned = true;
        }
        assert (len == 1 || len == 2 || len == 4);
        int insig = ((4 - len) * 8);  // how many insignificant bits?
        int sx = x << insig;
        ;
        assert (x == (isSigned ? (sx >> insig) : (sx >>> insig)));
        for (int i = 0; i < len; i++) {
            sbuf.append((char) (sx >>> 24));
            sx <<= 8;
        }
    }
}
