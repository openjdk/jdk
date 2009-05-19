/*
 * Copyright 2007-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.classfile;

import java.io.IOException;

/**
 * See JVMS3, section 4.5.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ConstantPool {

    public class InvalidIndex extends ConstantPoolException {
        private static final long serialVersionUID = -4350294289300939730L;
        InvalidIndex(int index) {
            super(index);
        }

        @Override
        public String getMessage() {
            // i18n
            return "invalid index #" + index;
        }
    }

    public class UnexpectedEntry extends ConstantPoolException {
        private static final long serialVersionUID = 6986335935377933211L;
        UnexpectedEntry(int index, int expected_tag, int found_tag) {
            super(index);
            this.expected_tag = expected_tag;
            this.found_tag = found_tag;
        }

        @Override
        public String getMessage() {
            // i18n?
            return "unexpected entry at #" + index + " -- expected tag " + expected_tag + ", found " + found_tag;
        }

        public final int expected_tag;
        public final int found_tag;
    }

    public class InvalidEntry extends ConstantPoolException {
        private static final long serialVersionUID = 1000087545585204447L;
        InvalidEntry(int index, int tag) {
            super(index);
            this.tag = tag;
        }

        @Override
        public String getMessage() {
            // i18n?
            return "unexpected tag at #" + index + ": " + tag;
        }

        public final int tag;
    }

    public class EntryNotFound extends ConstantPoolException {
        private static final long serialVersionUID = 2885537606468581850L;
        EntryNotFound(Object value) {
            super(-1);
            this.value = value;
        }

        @Override
        public String getMessage() {
            // i18n?
            return "value not found: " + value;
        }

        public final Object value;
    }

    public static final int CONSTANT_Utf8 = 1;
    public static final int CONSTANT_Integer = 3;
    public static final int CONSTANT_Float = 4;
    public static final int CONSTANT_Long = 5;
    public static final int CONSTANT_Double = 6;
    public static final int CONSTANT_Class = 7;
    public static final int CONSTANT_String = 8;
    public static final int CONSTANT_Fieldref = 9;
    public static final int CONSTANT_Methodref = 10;
    public static final int CONSTANT_InterfaceMethodref = 11;
    public static final int CONSTANT_NameAndType = 12;

    ConstantPool(ClassReader cr) throws IOException, InvalidEntry {
        int count = cr.readUnsignedShort();
        pool = new CPInfo[count];
        for (int i = 1; i < count; i++) {
            int tag = cr.readUnsignedByte();
            switch (tag) {
            case CONSTANT_Class:
                pool[i] = new CONSTANT_Class_info(this, cr);
                break;

            case CONSTANT_Double:
                pool[i] = new CONSTANT_Double_info(cr);
                i++;
                break;

            case CONSTANT_Fieldref:
                pool[i] = new CONSTANT_Fieldref_info(this, cr);
                break;

            case CONSTANT_Float:
                pool[i] = new CONSTANT_Float_info(cr);
                break;

            case CONSTANT_Integer:
                pool[i] = new CONSTANT_Integer_info(cr);
                break;

            case CONSTANT_InterfaceMethodref:
                pool[i] = new CONSTANT_InterfaceMethodref_info(this, cr);
                break;

            case CONSTANT_Long:
                pool[i] = new CONSTANT_Long_info(cr);
                i++;
                break;

            case CONSTANT_Methodref:
                pool[i] = new CONSTANT_Methodref_info(this, cr);
                break;

            case CONSTANT_NameAndType:
                pool[i] = new CONSTANT_NameAndType_info(this, cr);
                break;

            case CONSTANT_String:
                pool[i] = new CONSTANT_String_info(this, cr);
                break;

            case CONSTANT_Utf8:
                pool[i] = new CONSTANT_Utf8_info(cr);
                break;

            default:
                throw new InvalidEntry(i, tag);
            }
        }
    }

    public ConstantPool(CPInfo[] pool) {
        this.pool = pool;
    }

    public int size() {
        return pool.length;
    }

    public CPInfo get(int index) throws InvalidIndex {
        if (index <= 0 || index >= pool.length)
            throw new InvalidIndex(index);
        CPInfo info = pool[index];
        if (info == null) {
            // this occurs for indices referencing the "second half" of an
            // 8 byte constant, such as CONSTANT_Double or CONSTANT_Long
            throw new InvalidIndex(index);
        }
        return pool[index];
    }

    private CPInfo get(int index, int expected_type) throws InvalidIndex, UnexpectedEntry {
        CPInfo info = get(index);
        if (info.getTag() != expected_type)
            throw new UnexpectedEntry(index, expected_type, info.getTag());
        return info;
    }

    public CONSTANT_Utf8_info getUTF8Info(int index) throws InvalidIndex, UnexpectedEntry {
        return ((CONSTANT_Utf8_info) get(index, CONSTANT_Utf8));
    }

    public CONSTANT_Class_info getClassInfo(int index) throws InvalidIndex, UnexpectedEntry {
        return ((CONSTANT_Class_info) get(index, CONSTANT_Class));
    }

    public CONSTANT_NameAndType_info getNameAndTypeInfo(int index) throws InvalidIndex, UnexpectedEntry {
        return ((CONSTANT_NameAndType_info) get(index, CONSTANT_NameAndType));
    }

    public String getUTF8Value(int index) throws InvalidIndex, UnexpectedEntry {
        return getUTF8Info(index).value;
    }

    public int getUTF8Index(String value) throws EntryNotFound {
        for (int i = 1; i < pool.length; i++) {
            CPInfo info = pool[i];
            if (info instanceof CONSTANT_Utf8_info &&
                    ((CONSTANT_Utf8_info) info).value.equals(value))
                return i;
        }
        throw new EntryNotFound(value);
    }

    private CPInfo[] pool;

    public interface Visitor<R,P> {
        R visitClass(CONSTANT_Class_info info, P p);
        R visitDouble(CONSTANT_Double_info info, P p);
        R visitFieldref(CONSTANT_Fieldref_info info, P p);
        R visitFloat(CONSTANT_Float_info info, P p);
        R visitInteger(CONSTANT_Integer_info info, P p);
        R visitInterfaceMethodref(CONSTANT_InterfaceMethodref_info info, P p);
        R visitLong(CONSTANT_Long_info info, P p);
        R visitNameAndType(CONSTANT_NameAndType_info info, P p);
        R visitMethodref(CONSTANT_Methodref_info info, P p);
        R visitString(CONSTANT_String_info info, P p);
        R visitUtf8(CONSTANT_Utf8_info info, P p);
    }

    public static abstract class CPInfo {
        CPInfo() {
            this.cp = null;
        }

        CPInfo(ConstantPool cp) {
            this.cp = cp;
        }

        public abstract int getTag();

        public abstract <R,D> R accept(Visitor<R,D> visitor, D data);

        protected final ConstantPool cp;
    }

    public static abstract class CPRefInfo extends CPInfo {
        protected CPRefInfo(ConstantPool cp, ClassReader cr, int tag) throws IOException {
            super(cp);
            this.tag = tag;
            class_index = cr.readUnsignedShort();
            name_and_type_index = cr.readUnsignedShort();
        }

        protected CPRefInfo(ConstantPool cp, int tag, int class_index, int name_and_type_index) {
            super(cp);
            this.tag = tag;
            this.class_index = class_index;
            this.name_and_type_index = name_and_type_index;
        }

        public int getTag() {
            return tag;
        }

        public CONSTANT_Class_info getClassInfo() throws ConstantPoolException {
            return cp.getClassInfo(class_index);
        }

        public String getClassName() throws ConstantPoolException {
            return cp.getClassInfo(class_index).getName();
        }

        public CONSTANT_NameAndType_info getNameAndTypeInfo() throws ConstantPoolException {
            return cp.getNameAndTypeInfo(name_and_type_index);
        }

        public final int tag;
        public final int class_index;
        public final int name_and_type_index;
    }

    public static class CONSTANT_Class_info extends CPInfo {
        CONSTANT_Class_info(ConstantPool cp, ClassReader cr) throws IOException {
            super(cp);
            name_index = cr.readUnsignedShort();
        }

        public CONSTANT_Class_info(ConstantPool cp, int name_index) {
            super(cp);
            this.name_index = name_index;
        }

        public int getTag() {
            return CONSTANT_Class;
        }

        public String getName() throws ConstantPoolException {
            return cp.getUTF8Value(name_index);
        }

        public String getBaseName() throws ConstantPoolException {
            String name = getName();
            int index = name.indexOf("[L") + 1;
            return name.substring(index);
        }

        public int getDimensionCount() throws ConstantPoolException {
            String name = getName();
            int count = 0;
            while (name.charAt(count) == '[')
                count++;
            return count;
        }

        @Override
        public String toString() {
            return "CONSTANT_Class_info[name_index: " + name_index + "]";
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitClass(this, data);
        }

        public final int name_index;
    }

    public static class CONSTANT_Double_info extends CPInfo {
        CONSTANT_Double_info(ClassReader cr) throws IOException {
            value = cr.readDouble();
        }

        public CONSTANT_Double_info(double value) {
            this.value = value;
        }

        public int getTag() {
            return CONSTANT_Double;
        }

        @Override
        public String toString() {
            return "CONSTANT_Double_info[value: " + value + "]";
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitDouble(this, data);
        }

        public final double value;
    }

    public static class CONSTANT_Fieldref_info extends CPRefInfo {
        CONSTANT_Fieldref_info(ConstantPool cp, ClassReader cr) throws IOException {
            super(cp, cr, CONSTANT_Fieldref);
        }

        public CONSTANT_Fieldref_info(ConstantPool cp, int class_index, int name_and_type_index) {
            super(cp, CONSTANT_Fieldref, class_index, name_and_type_index);
        }

        @Override
        public String toString() {
            return "CONSTANT_Fieldref_info[class_index: " + class_index + ", name_and_type_index: " + name_and_type_index + "]";
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitFieldref(this, data);
        }
    }

    public static class CONSTANT_Float_info extends CPInfo {
        CONSTANT_Float_info(ClassReader cr) throws IOException {
            value = cr.readFloat();
        }

        public CONSTANT_Float_info(float value) {
            this.value = value;
        }

        public int getTag() {
            return CONSTANT_Float;
        }

        @Override
        public String toString() {
            return "CONSTANT_Float_info[value: " + value + "]";
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitFloat(this, data);
        }

        public final float value;
    }

    public static class CONSTANT_Integer_info extends CPInfo {
        CONSTANT_Integer_info(ClassReader cr) throws IOException {
            value = cr.readInt();
        }

        public CONSTANT_Integer_info(int value) {
            this.value = value;
        }

        public int getTag() {
            return CONSTANT_Integer;
        }

        @Override
        public String toString() {
            return "CONSTANT_Integer_info[value: " + value + "]";
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitInteger(this, data);
        }

        public final int value;
    }

    public static class CONSTANT_InterfaceMethodref_info extends CPRefInfo {
        CONSTANT_InterfaceMethodref_info(ConstantPool cp, ClassReader cr) throws IOException {
            super(cp, cr, CONSTANT_InterfaceMethodref);
        }

        public CONSTANT_InterfaceMethodref_info(ConstantPool cp, int class_index, int name_and_type_index) {
            super(cp, CONSTANT_InterfaceMethodref, class_index, name_and_type_index);
        }

        @Override
        public String toString() {
            return "CONSTANT_InterfaceMethodref_info[class_index: " + class_index + ", name_and_type_index: " + name_and_type_index + "]";
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitInterfaceMethodref(this, data);
        }
    }

    public static class CONSTANT_Long_info extends CPInfo {
        CONSTANT_Long_info(ClassReader cr) throws IOException {
            value = cr.readLong();
        }

        public CONSTANT_Long_info(long value) {
            this.value = value;
        }

        public int getTag() {
            return CONSTANT_Long;
        }

        @Override
        public String toString() {
            return "CONSTANT_Long_info[value: " + value + "]";
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitLong(this, data);
        }

        public final long value;
    }

    public static class CONSTANT_Methodref_info extends CPRefInfo {
        CONSTANT_Methodref_info(ConstantPool cp, ClassReader cr) throws IOException {
            super(cp, cr, CONSTANT_Methodref);
        }

        public CONSTANT_Methodref_info(ConstantPool cp, int class_index, int name_and_type_index) {
            super(cp, CONSTANT_Methodref, class_index, name_and_type_index);
        }

        @Override
        public String toString() {
            return "CONSTANT_Methodref_info[class_index: " + class_index + ", name_and_type_index: " + name_and_type_index + "]";
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitMethodref(this, data);
        }
    }

    public static class CONSTANT_NameAndType_info extends CPInfo {
        CONSTANT_NameAndType_info(ConstantPool cp, ClassReader cr) throws IOException {
            super(cp);
            name_index = cr.readUnsignedShort();
            type_index = cr.readUnsignedShort();
        }

        public CONSTANT_NameAndType_info(ConstantPool cp, int name_index, int type_index) {
            super(cp);
            this.name_index = name_index;
            this.type_index = type_index;
        }

        public int getTag() {
            return CONSTANT_NameAndType;
        }

        public String getName() throws ConstantPoolException {
            return cp.getUTF8Value(name_index);
        }

        public String getType() throws ConstantPoolException {
            return cp.getUTF8Value(type_index);
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitNameAndType(this, data);
        }

        public final int name_index;
        public final int type_index;
    }

    public static class CONSTANT_String_info extends CPInfo {
        CONSTANT_String_info(ConstantPool cp, ClassReader cr) throws IOException {
            super(cp);
            string_index = cr.readUnsignedShort();
        }

        public CONSTANT_String_info(ConstantPool cp, int string_index) {
            super(cp);
            this.string_index = string_index;
        }

        public int getTag() {
            return CONSTANT_String;
        }

        public String getString() throws ConstantPoolException {
            return cp.getUTF8Value(string_index);
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitString(this, data);
        }

        public final int string_index;
    }

    public static class CONSTANT_Utf8_info extends CPInfo {
        CONSTANT_Utf8_info(ClassReader cr) throws IOException {
            value = cr.readUTF();
        }

        public CONSTANT_Utf8_info(String value) {
            this.value = value;
        }

        public int getTag() {
            return CONSTANT_Utf8;
        }

        @Override
        public String toString() {
            return "CONSTANT_Utf8_info[value: " + value + "]";
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitUtf8(this, data);
        }

        public final String value;
    }


}
