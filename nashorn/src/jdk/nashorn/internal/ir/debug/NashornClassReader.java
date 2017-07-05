/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.ir.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.internal.org.objectweb.asm.Attribute;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.Label;
import jdk.nashorn.internal.ir.debug.NashornTextifier.NashornLabel;

/**
 * Subclass of the ASM classs reader that retains more info, such
 * as bytecode offsets
 */
public class NashornClassReader extends ClassReader {

    private final Map<String, List<Label>> labelMap = new HashMap<>();

    /**
     * Constructor
     * @param bytecode bytecode for class
     */
    public NashornClassReader(final byte[] bytecode) {
        super(bytecode);
        parse(bytecode);
    }

    List<Label> getExtraLabels(final String className, final String methodName, final String methodDesc) {
        final String key = fullyQualifiedName(className, methodName, methodDesc);
        return labelMap.get(key);
    }

    private static int readByte(final byte[] bytecode, final int index) {
        return (byte)(bytecode[index] & 0xff);
    }

    private static int readShort(final byte[] bytecode, final int index) {
        return (short)((bytecode[index] & 0xff) << 8) | (bytecode[index + 1] & 0xff);
    }

    private static int readInt(final byte[] bytecode, final int index) {
        return ((bytecode[index] & 0xff) << 24) | ((bytecode[index + 1] & 0xff) << 16) | ((bytecode[index + 2] & 0xff) << 8) | (bytecode[index + 3] & 0xff);
    }

    private static long readLong(final byte[] bytecode, final int index) {
        final int hi = readInt(bytecode, index);
        final int lo = readInt(bytecode, index + 4);
        return ((long)hi << 32) | lo;
    }

    private static String readUTF(final int index, final int utfLen, final byte[] bytecode) {
        final int endIndex = index + utfLen;
        final char buf[] = new char[utfLen * 2];
        int strLen = 0;
        int c;
        int st = 0;
        char cc = 0;
        int i = index;

        while (i < endIndex) {
            c = bytecode[i++];
            switch (st) {
            case 0:
                c = c & 0xFF;
                if (c < 0x80) { // 0xxxxxxx
                    buf[strLen++] = (char) c;
                } else if (c < 0xE0 && c > 0xBF) { // 110x xxxx 10xx xxxx
                    cc = (char) (c & 0x1F);
                    st = 1;
                } else { // 1110 xxxx 10xx xxxx 10xx xxxx
                    cc = (char) (c & 0x0F);
                    st = 2;
                }
                break;

            case 1: // byte 2 of 2-byte char or byte 3 of 3-byte char
                buf[strLen++] = (char) ((cc << 6) | (c & 0x3F));
                st = 0;
                break;

            case 2: // byte 2 of 3-byte char
                cc = (char) ((cc << 6) | (c & 0x3F));
                st = 1;
                break;

            default:
                break;
            }
        }
        return new String(buf, 0, strLen);
    }

    private String parse(final byte[] bytecode) {
        String thisClassName;

        int u = 0;

        final int magic = readInt(bytecode, u);
        u += 4; //magic
        assert magic == 0xcafebabe : Integer.toHexString(magic);
        readShort(bytecode, u); //minor
        u += 2;
        readShort(bytecode, u); //major
        u += 2; //minor

        final int cpc = readShort(bytecode, u);
        u += 2;
        final ArrayList<Constant> cp = new ArrayList<>(cpc);
        cp.add(null);

        for (int i = 1; i < cpc; i++) {
            //constant pool entries
            final int tag = readByte(bytecode, u);
            u += 1;
            switch (tag) {
            case 7: //class
                cp.add(new IndexInfo(cp, tag, readShort(bytecode, u)));
                u += 2;
                break;
            case 9:  //fieldref
            case 10: //methodref
            case 11: //interfacemethodref
                cp.add(new IndexInfo2(cp, tag, readShort(bytecode, u), readShort(bytecode, u + 2)));
                u += 4;
               break;
            case 8: //string
                cp.add(new IndexInfo(cp, tag, readShort(bytecode, u))); //string index
                u += 2;
                break;
            case 3:  //int
                cp.add(new DirectInfo<>(cp, tag, readInt(bytecode, u)));
                u += 4;
                break;
            case 4:  //float
                cp.add(new DirectInfo<>(cp, tag, Float.intBitsToFloat(readInt(bytecode, u))));
                u += 4;
                break;
            case 5:  //long
                cp.add(new DirectInfo<>(cp, tag, readLong(bytecode, u)));
                cp.add(null);
                i++;
                u += 8;
                break;
            case 6:  //double
                cp.add(new DirectInfo<>(cp, tag, Double.longBitsToDouble(readLong(bytecode, u))));
                cp.add(null);
                i++;
                u += 8;
                break;
            case 12: //name and type
                cp.add(new IndexInfo2(cp, tag, readShort(bytecode, u), readShort(bytecode, u + 2)));
                u += 4;
                break;
            case 1:  //utf8
                final int len = readShort(bytecode, u);
                u += 2;
                cp.add(new DirectInfo<>(cp, tag, readUTF(u, len, bytecode)));
                u += len;
                break;
            case 16: //methodtype
                cp.add(new IndexInfo(cp, tag, readShort(bytecode, u)));
                u += 2;
                break;
            case 18: //indy
                cp.add(new IndexInfo2(cp, tag, readShort(bytecode, u), readShort(bytecode, u + 2)) {
                    @Override
                    public String toString() {
                        return "#" + index + ' ' + cp.get(index2).toString();
                    }

                });
                u += 4;
                break;
            case 15: //methodhandle
                final int kind = readByte(bytecode, u);
                assert kind >= 1 && kind <= 9 : kind;
                cp.add(new IndexInfo2(cp, tag, kind, readShort(bytecode, u + 1)) {
                    @Override
                    public String toString() {
                        return "#" + index + ' ' + cp.get(index2).toString();
                    }
                });

                u += 3;
                break;
            default:
                assert false : tag;
                break;
            }
        }

        readShort(bytecode, u); //access flags
        u += 2; //access
        final int cls = readShort(bytecode, u);
        u += 2; //this_class
        thisClassName = cp.get(cls).toString();
        u += 2; //super

        final int ifc = readShort(bytecode, u);
        u += 2;
        u += ifc * 2;

        final int fc = readShort(bytecode, u);
        u += 2; //fields

        for (int i = 0 ; i < fc ; i++) {
            u += 2; //access
            readShort(bytecode, u); //fieldname
            u += 2; //name
            u += 2; //descriptor
            final int ac = readShort(bytecode, u);
            u += 2;
            //field attributes
            for (int j = 0; j < ac; j++) {
                u += 2; //attribute name
                final int len = readInt(bytecode, u);
                u += 4;
                u += len;
            }
        }

        final int mc = readShort(bytecode, u);
        u += 2;
        for (int i = 0 ; i < mc ; i++) {
            readShort(bytecode, u);
            u += 2; //access

            final int methodNameIndex = readShort(bytecode, u);
            u += 2;
            final String methodName = cp.get(methodNameIndex).toString();

            final int methodDescIndex = readShort(bytecode, u);
            u += 2;
            final String methodDesc = cp.get(methodDescIndex).toString();

            final int ac = readShort(bytecode, u);
            u += 2;

            //method attributes
            for (int j = 0; j < ac; j++) {
                final int nameIndex = readShort(bytecode, u);
                u += 2;
                final String attrName = cp.get(nameIndex).toString();

                final int attrLen = readInt(bytecode, u);
                u += 4;

                if ("Code".equals(attrName)) {
                    readShort(bytecode, u);
                    u += 2; //max stack
                    readShort(bytecode, u);
                    u += 2; //max locals
                    final int len = readInt(bytecode, u);
                    u += 4;
                    parseCode(bytecode, u, len, fullyQualifiedName(thisClassName, methodName, methodDesc));
                    u += len;
                    final int elen = readShort(bytecode, u); //exception table length
                    u += 2;
                    u += elen * 8;

                    //method attributes
                    final int ac2 = readShort(bytecode, u);
                    u += 2;
                    for (int k = 0; k < ac2; k++) {
                        u += 2; //name;
                        final int aclen = readInt(bytecode, u);
                        u += 4; //length
                        u += aclen; //bytes;
                    }
                } else {
                    u += attrLen;
                }
            }
        }

        final int ac = readShort(bytecode, u);
        u += 2;
        //other attributes
        for (int i = 0 ; i < ac ; i++) {
            readShort(bytecode, u); //name index
            u += 2;
            final int len = readInt(bytecode, u);
            u += 4;
            u += len;
            //attribute
        }

        return thisClassName;
    }

    private static String fullyQualifiedName(final String className, final String methodName, final String methodDesc) {
        return className + '.' + methodName + methodDesc;
    }

    private void parseCode(final byte[] bytecode, final int index, final int len, final String desc) {
        final List<Label> labels = new ArrayList<>();
        labelMap.put(desc, labels);

        boolean wide = false;

        for (int i = index; i < index + len;) {
            final int opcode = bytecode[i];
            labels.add(new NashornLabel(opcode, i - index));

            switch (opcode & 0xff) {
            case 0xc4: //wide
                wide = true;
                i += 1;
                break;
            case 0xa9: //ret
                i += wide ? 4 : 2;
                break;
            case 0xab: //lookupswitch
                i += 1;
                while (((i - index) & 3) != 0) {
                    i++;
                }
                readInt(bytecode, i);
                i += 4; //defaultbyte
                final int npairs = readInt(bytecode, i);
                i += 4;
                i += 8 * npairs;
                break;
            case 0xaa: //tableswitch
                i += 1;
                while (((i - index) & 3) != 0) {
                    i++;
                }
                readInt(bytecode, i); //default
                i += 4;
                final int lo = readInt(bytecode, i);
                i += 4;
                final int hi = readInt(bytecode, i);
                i += 4;
                i += 4 * (hi - lo + 1);
                break;
            case 0xc5: //multianewarray
                i += 4;
                break;
            case 0x19: //aload (wide)
            case 0x18: //dload
            case 0x17: //fload
            case 0x15: //iload
            case 0x16: //lload
            case 0x3a: //astore wide
            case 0x39: //dstore
            case 0x38: //fstore
            case 0x36: //istore
            case 0x37: //lstore
                i += wide ? 3 : 2;
                break;
            case 0x10: //bipush
            case 0x12: //ldc
            case 0xbc: //anewarrayu
                i += 2;
                break;
            case 0xb4: //getfield
            case 0xb2: //getstatic
            case 0xbd: //anewarray
            case 0xc0: //checkcast
            case 0xa5: //ifacmp_eq
            case 0xa6: //ifacmp_ne
            case 0x9f: //all ifs and ifcmps
            case 0xa0:
            case 0xa1:
            case 0xa2:
            case 0xa3:
            case 0xa4:
            case 0x99:
            case 0x9a:
            case 0x9b:
            case 0x9c:
            case 0x9d:
            case 0x9e:
            case 0xc7:
            case 0xc6:
            case 0xc1: //instanceof
            case 0xa7: //goto
            case 0xb7: //special
            case 0xb8: //static
            case 0xb6: //virtual
            case 0xa8: //jsr
            case 0x13: //ldc_w
            case 0x14: //ldc2_w
            case 0xbb: //new
            case 0xb5: //putfield
            case 0xb3: //putstatic
            case 0x11: //sipush
                i += 3;
                break;
            case 0x84: //iinc (wide)
                i += wide ? 5 : 3;
                break;
            case 0xba: //indy
            case 0xb9: //interface
            case 0xc8:
            case 0xc9:  //jsr_w
                i += 5; //goto_w
                break;
            default:
                i++;
                break;
            }

            if (wide) {
                wide = false;
            }
        }
    }

    @Override
    public void accept(final ClassVisitor classVisitor, final Attribute[] attrs, final int flags) {
        super.accept(classVisitor, attrs, flags);
    }

    @Override
    protected Label readLabel(final int offset, final Label[] labels) {
        final Label label = super.readLabel(offset, labels);
        label.info = offset;
        return label;
    }

    private abstract static class Constant {
        protected ArrayList<Constant> cp;
        protected int tag;
        protected Constant(final ArrayList<Constant> cp, final int tag) {
            this.cp = cp;
            this.tag = tag;
        }

        @SuppressWarnings("unused")
        final String getType() {
            String str = type[tag];
            while (str.length() < 16) {
                str += " ";
            }
            return str;
        }
    }

    private static class IndexInfo extends Constant {
        protected final int index;

        IndexInfo(final ArrayList<Constant> cp, final int tag, final int index) {
            super(cp, tag);
            this.index = index;
        }

        @Override
        public String toString() {
            return cp.get(index).toString();
        }
    }

    private static class IndexInfo2 extends IndexInfo {
        protected final int index2;

        IndexInfo2(final ArrayList<Constant> cp, final int tag, final int index, final int index2) {
            super(cp, tag, index);
            this.index2 = index2;
        }

        @Override
        public String toString() {
            return super.toString() + ' ' + cp.get(index2).toString();
        }
    }

    private static class DirectInfo<T> extends Constant {
        protected final T info;

        DirectInfo(final ArrayList<Constant> cp, final int tag, final T info) {
            super(cp, tag);
            this.info = info;
        }

        @Override
        public String toString() {
            return info.toString();// + " [class=" + info.getClass().getSimpleName() + ']';
        }
    }

    private static String type[] = {
        //0
        "<error>",
        //1
        "UTF8",
        //2
        "<error>",
        //3
        "Integer",
        //4
        "Float",
        //5
        "Long",
        //6
        "Double",
        //7
        "Class",
        //8
        "String",
        //9
        "Fieldref",
        //10
        "Methodref",
        //11
        "InterfaceMethodRef",
        //12
        "NameAndType",
        //13
        "<error>",
        //14
        "<error>",
        //15
        "MethodHandle",
        //16
        "MethodType",
        //17
        "<error>",
        //18
        "Invokedynamic"
    };

}
