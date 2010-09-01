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
import java.util.Map;
/*
 * @author jrose
 */
public abstract class InstructionSyntax {

    InstructionSyntax() {
    }
    static final String[] bcNames;
    static final String[] bcFormats;
    static final String[] bcWideFormats;
    static final HashMap<String, Integer> bcCodes;
    static final HashMap<String, Element> abbrevs;
    static final HashMap<Element, String> rabbrevs;

    static {
        TokenList tl = new TokenList(
                " nop aconst_null iconst_m1 iconst_0 iconst_1 iconst_2 iconst_3"
                + " iconst_4 iconst_5 lconst_0 lconst_1 fconst_0 fconst_1 fconst_2"
                + " dconst_0 dconst_1 bipush/s sipush/ss ldc/k ldc_w/kk ldc2_w/kk"
                + " iload/wl lload/wl fload/wl dload/wl aload/wl iload_0 iload_1"
                + " iload_2 iload_3 lload_0 lload_1 lload_2 lload_3 fload_0 fload_1"
                + " fload_2 fload_3 dload_0 dload_1 dload_2 dload_3 aload_0 aload_1"
                + " aload_2 aload_3 iaload laload faload daload aaload baload caload"
                + " saload istore/wl lstore/wl fstore/wl dstore/wl astore/wl"
                + " istore_0 istore_1 istore_2 istore_3 lstore_0 lstore_1 lstore_2"
                + " lstore_3 fstore_0 fstore_1 fstore_2 fstore_3 dstore_0 dstore_1"
                + " dstore_2 dstore_3 astore_0 astore_1 astore_2 astore_3 iastore"
                + " lastore fastore dastore aastore bastore castore sastore pop pop2"
                + " dup dup_x1 dup_x2 dup2 dup2_x1 dup2_x2 swap iadd ladd fadd dadd"
                + " isub lsub fsub dsub imul lmul fmul dmul idiv ldiv fdiv ddiv irem"
                + " lrem frem drem ineg lneg fneg dneg ishl lshl ishr lshr iushr"
                + " lushr iand land ior lor ixor lxor iinc/wls i2l i2f i2d l2i l2f"
                + " l2d f2i f2l f2d d2i d2l d2f i2b i2c i2s lcmp fcmpl fcmpg dcmpl"
                + " dcmpg ifeq/oo ifne/oo iflt/oo ifge/oo ifgt/oo ifle/oo"
                + " if_icmpeq/oo if_icmpne/oo if_icmplt/oo if_icmpge/oo if_icmpgt/oo"
                + " if_icmple/oo if_acmpeq/oo if_acmpne/oo goto/oo jsr/oo ret/wl"
                + " tableswitch/oooot lookupswitch/oooot ireturn lreturn freturn dreturn areturn"
                + " return getstatic/kf putstatic/kf getfield/kf putfield/kf"
                + " invokevirtual/km invokespecial/km invokestatic/km"
                + " invokeinterface/knxx xxxunusedxxx new/kc newarray/x anewarray/kc"
                + " arraylength athrow checkcast/kc instanceof/kc monitorenter"
                + " monitorexit wide multianewarray/kcx ifnull/oo ifnonnull/oo"
                + " goto_w/oooo jsr_w/oooo");
        assert (tl.size() == 202);  // this many instructions!
        HashMap<String, Integer> map = new HashMap<String, Integer>(tl.size());
        String[] names = tl.toArray(new String[tl.size()]);
        String[] formats = new String[names.length];
        String[] wideFormats = new String[names.length];
        StringBuilder sbuf = new StringBuilder();
        sbuf.append('i');  // all op formats begin with "i"
        int i = 0;
        for (String ins : names) {
            assert (ins == ins.trim());  // no whitespace
            int sfx = ins.indexOf('/');
            String format = "i";
            String wideFormat = null;
            if (sfx >= 0) {
                format = ins.substring(sfx + 1);
                ins = ins.substring(0, sfx);
                if (format.charAt(0) == 'w') {
                    format = format.substring(1);
                    sbuf.setLength(1);
                    for (int j = 0; j < format.length(); j++) {
                        // double everything except the initial 'i'
                        sbuf.append(format.charAt(j));
                        sbuf.append(format.charAt(j));
                    }
                    wideFormat = sbuf.toString().intern();
                }
                sbuf.setLength(1);
                sbuf.append(format);
                format = sbuf.toString().intern();
            }
            ins = ins.intern();
            names[i] = ins;
            formats[i] = format;
            wideFormats[i] = (wideFormat != null) ? wideFormat : format;
            //System.out.println(ins+" "+format+" "+wideFormat);
            map.put(ins, i++);
        }
        //map = Collections.unmodifiableMap(map);

        HashMap<String, Element> abb = new HashMap<String, Element>(tl.size() / 2);
        abb.put("iconst_m1", new Element("bipush", "num", "-1"));
        for (String ins : names) {
            int sfx = ins.indexOf('_');
            if (sfx >= 0 && Character.isDigit(ins.charAt(sfx + 1))) {
                String pfx = ins.substring(0, sfx).intern();
                String num = ins.substring(sfx + 1);
                String att = pfx.endsWith("const") ? "num" : "loc";
                Element exp = new Element(pfx, att, num).deepFreeze();
                abb.put(ins, exp);
            }
        }
        //abb = Collections.unmodifiableMap(abb);
        HashMap<Element, String> rabb = new HashMap<Element, String>(tl.size() / 2);
        for (Map.Entry<String, Element> e : abb.entrySet()) {
            rabb.put(e.getValue(), e.getKey());
        }
        //rabb = Collections.unmodifiableMap(rabb);


        bcNames = names;
        bcFormats = formats;
        bcWideFormats = wideFormats;
        bcCodes = map;
        abbrevs = abb;
        rabbrevs = rabb;
    }

    public static String opName(int op) {
        if (op >= 0 && op < bcNames.length) {
            return bcNames[op];
        }
        return "unknown#" + op;
    }

    public static String opFormat(int op) {
        return opFormat(op, false);
    }

    public static String opFormat(int op, boolean isWide) {
        if (op >= 0 && op < bcFormats.length) {
            return (isWide ? bcWideFormats[op] : bcFormats[op]);
        }
        return "?";
    }

    public static int opCode(String opName) {
        Integer op = (Integer) bcCodes.get(opName);
        if (op != null) {
            return op.intValue();
        }
        return -1;
    }

    public static Element expandAbbrev(String opName) {
        return abbrevs.get(opName);
    }

    public static String findAbbrev(Element op) {
        return rabbrevs.get(op);
    }

    public static int invertBranchOp(int op) {
        assert (opFormat(op).indexOf('o') >= 0);
        final int IFMIN = 0x99;
        final int IFMAX = 0xa6;
        final int IFMIN2 = 0xc6;
        final int IFMAX2 = 0xc7;
        assert (bcNames[IFMIN] == "ifeq");
        assert (bcNames[IFMAX] == "if_acmpne");
        assert (bcNames[IFMIN2] == "ifnonnull");
        assert (bcNames[IFMAX2] == "ifnull");
        int rop;
        if (op >= IFMIN && op <= IFMAX) {
            rop = IFMIN + ((op - IFMIN) ^ 1);
        } else if (op >= IFMIN2 && op <= IFMAX2) {
            rop = IFMIN2 + ((op - IFMIN2) ^ 1);
        } else {
            assert (false);
            rop = op;
        }
        assert (opFormat(rop).indexOf('o') >= 0);
        return rop;
    }

    public static Element parse(String bytes) {
        Element e = new Element("Instructions", bytes.length());
        boolean willBeWide;
        boolean isWide = false;
        Element[] tempMap = new Element[bytes.length()];
        for (int pc = 0, nextpc; pc < bytes.length(); pc = nextpc) {
            int op = bytes.charAt(pc);
            Element i = new Element(opName(op));

            nextpc = pc + 1;
            int locarg = 0;
            int cparg = 0;
            int intarg = 0;
            int labelarg = 0;

            willBeWide = false;
            switch (op) {
                case 0xc4: //wide
                    willBeWide = true;
                    break;
                case 0x10: //bipush
                    intarg = nextpc++;
                    intarg *= -1;  //mark signed
                    break;
                case 0x11: //sipush
                    intarg = nextpc;
                    nextpc += 2;
                    intarg *= -1;  //mark signed
                    break;
                case 0x12: //ldc
                    cparg = nextpc++;
                    break;
                case 0x13: //ldc_w
                case 0x14: //ldc2_w
                case 0xb2: //getstatic
                case 0xb3: //putstatic
                case 0xb4: //getfield
                case 0xb5: //putfield
                case 0xb6: //invokevirtual
                case 0xb7: //invokespecial
                case 0xb8: //invokestatic
                case 0xbb: //new
                case 0xbd: //anewarray
                case 0xc0: //checkcast
                case 0xc1: //instanceof
                    cparg = nextpc;
                    nextpc += 2;
                    break;
                case 0xb9: //invokeinterface
                    cparg = nextpc;
                    nextpc += 2;
                    intarg = nextpc;
                    nextpc += 2;
                    break;
                case 0xc5: //multianewarray
                    cparg = nextpc;
                    nextpc += 2;
                    intarg = nextpc++;
                    break;
                case 0x15: //iload
                case 0x16: //lload
                case 0x17: //fload
                case 0x18: //dload
                case 0x19: //aload
                case 0x36: //istore
                case 0x37: //lstore
                case 0x38: //fstore
                case 0x39: //dstore
                case 0x3a: //astore
                case 0xa9: //ret
                    locarg = nextpc++;
                    if (isWide) {
                        nextpc++;
                    }
                    break;
                case 0x84: //iinc
                    locarg = nextpc++;
                    if (isWide) {
                        nextpc++;
                    }
                    intarg = nextpc++;
                    if (isWide) {
                        nextpc++;
                    }
                    intarg *= -1;  //mark signed
                    break;
                case 0x99: //ifeq
                case 0x9a: //ifne
                case 0x9b: //iflt
                case 0x9c: //ifge
                case 0x9d: //ifgt
                case 0x9e: //ifle
                case 0x9f: //if_icmpeq
                case 0xa0: //if_icmpne
                case 0xa1: //if_icmplt
                case 0xa2: //if_icmpge
                case 0xa3: //if_icmpgt
                case 0xa4: //if_icmple
                case 0xa5: //if_acmpeq
                case 0xa6: //if_acmpne
                case 0xa7: //goto
                case 0xa8: //jsr
                    labelarg = nextpc;
                    nextpc += 2;
                    break;
                case 0xbc: //newarray
                    intarg = nextpc++;
                    break;
                case 0xc6: //ifnull
                case 0xc7: //ifnonnull
                    labelarg = nextpc;
                    nextpc += 2;
                    break;
                case 0xc8: //goto_w
                case 0xc9: //jsr_w
                    labelarg = nextpc;
                    nextpc += 4;
                    break;

                // save the best for last:
                case 0xaa: //tableswitch
                    nextpc = parseSwitch(bytes, pc, true, i);
                    break;
                case 0xab: //lookupswitch
                    nextpc = parseSwitch(bytes, pc, false, i);
                    break;
            }

            String format = null;
            assert ((format = opFormat(op, isWide)) != null);
            //System.out.println("pc="+pc+" len="+(nextpc - pc)+" w="+isWide+" op="+op+" name="+opName(op)+" format="+format);
            assert ((nextpc - pc) == format.length() || format.indexOf('t') >= 0);

            // Parse out instruction fields.
            if (locarg != 0) {
                int len = nextpc - locarg;
                if (intarg != 0) {
                    len /= 2;  // split
                }
                i.setAttr("loc", "" + getInt(bytes, locarg, len));
                assert ('l' == format.charAt(locarg - pc + 0));
                assert ('l' == format.charAt(locarg - pc + len - 1));
            }
            if (cparg != 0) {
                int len = nextpc - cparg;
                if (len > 2) {
                    len = 2;
                }
                i.setAttr("ref", "" + getInt(bytes, cparg, len));
                assert ('k' == format.charAt(cparg - pc + 0));
            }
            if (intarg != 0) {
                boolean isSigned = (intarg < 0);
                if (isSigned) {
                    intarg *= -1;
                }
                int len = nextpc - intarg;
                i.setAttr("num", "" + getInt(bytes, intarg, isSigned ? -len : len));
                assert ((isSigned ? 's' : 'x') == format.charAt(intarg - pc + 0));
                assert ((isSigned ? 's' : 'x') == format.charAt(intarg - pc + len - 1));
            }
            if (labelarg != 0) {
                int len = nextpc - labelarg;
                int offset = getInt(bytes, labelarg, -len);
                int target = pc + offset;
                i.setAttr("lab", "" + target);
                assert ('o' == format.charAt(labelarg - pc + 0));
                assert ('o' == format.charAt(labelarg - pc + len - 1));
            }

            e.add(i);
            tempMap[pc] = i;
            isWide = willBeWide;
        }

        // Mark targets of branches.
        for (Element i : e.elements()) {
            for (int j = -1; j < i.size(); j++) {
                Element c = (j < 0) ? i : (Element) i.get(j);
                Number targetNum = c.getAttrNumber("lab");
                if (targetNum != null) {
                    int target = targetNum.intValue();
                    Element ti = null;
                    if (target >= 0 && target < tempMap.length) {
                        ti = tempMap[target];
                    }
                    if (ti != null) {
                        ti.setAttr("pc", "" + target);
                    } else {
                        c.setAttr("lab.error", "");
                    }
                }
            }
        }

        // Shrink to fit:
        for (Element i : e.elements()) {
            i.trimToSize();
        }
        e.trimToSize();

        /*
        String assem = assemble(e);
        if (!assem.equals(bytes)) {
        System.out.println("Bytes: "+bytes);
        System.out.println("Insns: "+e);
        System.out.println("Assem: "+parse(assem));
        }
         */

        return e;
    }

    static int switchBase(int pc) {
        int apc = pc + 1;
        apc += (-apc) & 3;
        return apc;
    }

    static int parseSwitch(String s, int pc, boolean isTable, Element i) {
        int apc = switchBase(pc);
        int defLabel = pc + getInt(s, apc + 4 * 0, 4);
        i.setAttr("lab", "" + defLabel);
        if (isTable) {
            int lowCase = getInt(s, apc + 4 * 1, 4);
            int highCase = getInt(s, apc + 4 * 2, 4);
            int caseCount = highCase - lowCase + 1;
            for (int n = 0; n < caseCount; n++) {
                Element c = new Element("Case", 4);
                int caseVal = lowCase + n;
                int caseLab = getInt(s, apc + 4 * (3 + n), 4) + pc;
                c.setAttr("num", "" + caseVal);
                c.setAttr("lab", "" + caseLab);
                assert (c.getExtraCapacity() == 0);
                i.add(c);
            }
            return apc + 4 * (3 + caseCount);
        } else {
            int caseCount = getInt(s, apc + 4 * 1, 4);
            for (int n = 0; n < caseCount; n++) {
                Element c = new Element("Case", 4);
                int caseVal = getInt(s, apc + 4 * (2 + (2 * n) + 0), 4);
                int caseLab = getInt(s, apc + 4 * (2 + (2 * n) + 1), 4) + pc;
                c.setAttr("num", "" + caseVal);
                c.setAttr("lab", "" + caseLab);
                assert (c.getExtraCapacity() == 0);
                i.add(c);
            }
            return apc + 4 * (2 + 2 * caseCount);
        }
    }

    static int getInt(String s, int pc, int len) {
        //System.out.println("getInt s["+s.length()+"] pc="+pc+" len="+len);
        int result = s.charAt(pc);
        if (len < 0) {
            len = -len;
            result = (byte) result;
        }
        if (!(len == 1 || len == 2 || len == 4)) {
            System.out.println("len=" + len);
        }
        assert (len == 1 || len == 2 || len == 4);
        for (int i = 1; i < len; i++) {
            result <<= 8;
            result += s.charAt(pc + i) & 0xFF;
        }
        return result;
    }

    public static String assemble(Element instructions) {
        return InstructionAssembler.assemble(instructions, null, null);
    }

    public static String assemble(Element instructions, String pcAttrName) {
        return InstructionAssembler.assemble(instructions, pcAttrName, null);
    }

    public static String assemble(Element instructions, ClassSyntax.GetCPIndex getCPI) {
        return InstructionAssembler.assemble(instructions, null, getCPI);
    }

    public static String assemble(Element instructions, String pcAttrName,
            ClassSyntax.GetCPIndex getCPI) {
        return InstructionAssembler.assemble(instructions, pcAttrName, getCPI);
    }
}
