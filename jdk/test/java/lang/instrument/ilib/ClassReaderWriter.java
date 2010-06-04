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

package ilib;

class ClassReaderWriter implements RuntimeConstants {

    int codeAttributeIndex;
    int lineNumberAttributeIndex;
    int localVarAttributeIndex;

    private final byte[] orig;
    private final byte[] gen;
    private final int sectionLength;

    private static final int GROWTH_FACTOR = 2;
    private static final int SECTIONS = 2;
    private static final String codeAttributeName = "Code";
    private static final String lineNumberAttributeName = "LineNumberTable";
    private static final String localVarAttributeName = "LocalVariableTable";

    private int[] genSectionPos = new int[SECTIONS];

    private int inputPos = 0;
    private int genPos = 0;
    private int markPos = 0;
    private int currentSection = 0;

    private String[] constantPool;

    ClassReaderWriter(byte[] orig) {
        this.orig = orig;
        sectionLength = orig.length * GROWTH_FACTOR;
        gen = new byte[sectionLength * SECTIONS];
        for (int section = 0; section < SECTIONS; ++section) {
            genSectionPos[section] = section * sectionLength;
        }
    }

    int setSection(int section) {
        int prevSection = currentSection;
        genSectionPos[prevSection] = genPos;
        genPos = genSectionPos[section];
        currentSection = section;
        return prevSection;
    }

    byte[] result() {
        int section;
        int totalLength = 0;

        setSection(0); // save current section

        for (section = 0; section < SECTIONS; ++section) {
            int sectionStart = section * sectionLength;
            int sectionGenLength = genSectionPos[section] - sectionStart;
            totalLength += sectionGenLength;
        }

        byte[] newcf = new byte[totalLength];
        int written = 0;
        for (section = 0; section < SECTIONS; ++section) {
            int sectionStart = section * sectionLength;
            int sectionGenLength = genSectionPos[section] - sectionStart;
            System.arraycopy(gen, sectionStart, newcf, written, sectionGenLength);
            written += sectionGenLength;
        }

        return newcf;
    }

    int readU1() {
        return ((int)orig[inputPos++]) & 0xFF;
    }

    int readU2() {
        int res = readU1();
        return (res << 8) + readU1();
    }

    short readS2() {
        int res = readU1();
        return (short)((res << 8) + readU1());
    }

    int readU4() {
        int res = readU2();
        return (res << 16) + readU2();
    }

    void writeU1(int val) {
        gen[genPos++] = (byte)val;
    }

    void writeU2(int val) {
        writeU1(val >> 8);
        writeU1(val & 0xFF);
    }

    void writeU4(int val) {
        writeU2(val >> 16);
        writeU2(val & 0xFFFF);
    }

    int copyU1() {
        int value = readU1();
        writeU1(value);
        return value;
    }

    int copyU2() {
        int value = readU2();
        writeU2(value);
        return value;
    }

    int copyU4() {
        int value = readU4();
        writeU4(value);
        return value;
    }

    void copy(int count) {
        for (int i = 0; i < count; ++i) {
            gen[genPos++] = orig[inputPos++];
        }
    }

    void skip(int count) {
        inputPos += count;
    }

    byte[] readBytes(int count) {
        byte[] bytes = new byte[count];
        for (int i = 0; i < count; ++i) {
            bytes[i] = orig[inputPos++];
        }
        return bytes;
    }

    void writeBytes(byte[] bytes) {
        for (int i = 0; i < bytes.length; ++i) {
            gen[genPos++] = bytes[i];
        }
    }

    byte[] inputBytes() {
        return orig;
    }

    int inputPosition() {
        return inputPos;
    }

    void setInputPosition(int pos) {
        inputPos = pos;
    }

    void markLocalPositionStart() {
        markPos = inputPos;
    }

    int localPosition() {
        return inputPos - markPos;
    }

    void rewind() {
        setInputPosition(markPos);
    }

    int generatedPosition() {
        return genPos;
    }

    void randomAccessWriteU2(int pos, int val) {
        int savePos = genPos;
        genPos = pos;
        writeU2(val);
        genPos = savePos;
    }

    void randomAccessWriteU4(int pos, int val) {
        int savePos = genPos;
        genPos = pos;
        writeU4(val);
        genPos = savePos;
    }

    String constantPoolString(int index) {
        return constantPool[index];
    }

    void copyConstantPool(int constantPoolCount){
        // copy const pool
        constantPool = new String[constantPoolCount];
        // index zero not in class file
        for (int i = 1; i < constantPoolCount; ++i) {
            int tag = readU1();
            writeU1(tag);
            switch (tag) {
                case CONSTANT_CLASS:
                case CONSTANT_STRING:
                    copy(2);
                    break;
                case CONSTANT_FIELD:
                case CONSTANT_METHOD:
                case CONSTANT_INTERFACEMETHOD:
                case CONSTANT_INTEGER:
                case CONSTANT_FLOAT:
                case CONSTANT_NAMEANDTYPE:
                    copy(4);
                    break;
                case CONSTANT_LONG:
                case CONSTANT_DOUBLE:
                    copy(8);
                    ++i;  // these take two CP entries - duh!
                    break;
                case CONSTANT_UTF8:
                    int len = copyU2();
                    byte[] utf8 = readBytes(len);
                    String str = null; // null to shut the compiler up
                    try {
                        str = new String(utf8, "UTF-8");
                    } catch (Exception exc) {
                        throw new Error("CP exception: " + exc);
                    }
                    constantPool[i] = str;
                    if (str.equals(codeAttributeName)) {
                        codeAttributeIndex = i;
                    } else if (str.equals(lineNumberAttributeName)) {
                        lineNumberAttributeIndex = i;
                    } else if (str.equals(localVarAttributeName)) {
                        localVarAttributeIndex = i;
                    }
                    writeBytes(utf8);
                    break;
                default:
                    throw new Error(i + " unexpected CP tag: " + tag);
            }
        }
    }

}
