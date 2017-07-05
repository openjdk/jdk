/*
 * Copyright (c) 2002, 2011, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.jdi;

import com.sun.jdi.*;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.oops.Instance;
import sun.jvm.hotspot.oops.Array;
import sun.jvm.hotspot.oops.InstanceKlass;
import sun.jvm.hotspot.oops.Symbol;
import sun.jvm.hotspot.oops.FieldIdentifier;

import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Comparator;

public class FieldImpl extends TypeComponentImpl implements Field {
    private JNITypeParser signatureParser;
    private sun.jvm.hotspot.oops.Field saField;

    FieldImpl( VirtualMachine vm, ReferenceTypeImpl declaringType,
               sun.jvm.hotspot.oops.Field saField) {
        super(vm, declaringType);
        this.saField = saField;
        getParser();
    }

    private void getParser() {
        if (signatureParser == null) {
            Symbol sig1 = saField.getSignature();
            signature = sig1.asString();
            signatureParser = new JNITypeParser(signature);
        }
    }

    sun.jvm.hotspot.oops.Field ref() {
        return saField;
    }

    // get the value of static field
    ValueImpl getValue() {
        return getValue(saField.getFieldHolder().getJavaMirror());
    }

    // get the value of this Field from a specific Oop
    ValueImpl getValue(Oop target) {
        ValueImpl valueImpl;
        sun.jvm.hotspot.oops.Field saField = (sun.jvm.hotspot.oops.Field) ref();
        sun.jvm.hotspot.oops.FieldType ft = saField.getFieldType();
        if (ft.isArray()) {
            sun.jvm.hotspot.oops.OopField of = (sun.jvm.hotspot.oops.OopField)saField;
            valueImpl = (ArrayReferenceImpl) vm.arrayMirror((Array)of.getValue(target));
        } else if (ft.isObject()) {
            sun.jvm.hotspot.oops.OopField of = (sun.jvm.hotspot.oops.OopField)saField;
            valueImpl = (ObjectReferenceImpl) vm.objectMirror(of.getValue(target));
        } else if (ft.isByte()) {
            sun.jvm.hotspot.oops.ByteField bf = (sun.jvm.hotspot.oops.ByteField)saField;
            valueImpl = (ByteValueImpl) vm.mirrorOf(bf.getValue(target));
        } else if (ft.isChar()) {
            sun.jvm.hotspot.oops.CharField cf = (sun.jvm.hotspot.oops.CharField)saField;
            valueImpl = (CharValueImpl) vm.mirrorOf(cf.getValue(target));
        } else if (ft.isDouble()) {
            sun.jvm.hotspot.oops.DoubleField df = (sun.jvm.hotspot.oops.DoubleField)saField;
            valueImpl = (DoubleValueImpl) vm.mirrorOf(df.getValue(target));
        } else if (ft.isFloat()) {
            sun.jvm.hotspot.oops.FloatField ff = (sun.jvm.hotspot.oops.FloatField)saField;
            valueImpl = (FloatValueImpl) vm.mirrorOf(ff.getValue(target));
        } else if (ft.isInt()) {
            sun.jvm.hotspot.oops.IntField iif = (sun.jvm.hotspot.oops.IntField)saField;
            valueImpl = (IntegerValueImpl) vm.mirrorOf(iif.getValue(target));
        } else if (ft.isLong()) {
            sun.jvm.hotspot.oops.LongField lf = (sun.jvm.hotspot.oops.LongField)saField;
            valueImpl = (LongValueImpl) vm.mirrorOf(lf.getValue(target));
        } else if (ft.isShort()) {
            sun.jvm.hotspot.oops.ShortField sf = (sun.jvm.hotspot.oops.ShortField)saField;
            valueImpl = (ShortValueImpl) vm.mirrorOf(sf.getValue(target));
        } else if (ft.isBoolean()) {
            sun.jvm.hotspot.oops.BooleanField bf = (sun.jvm.hotspot.oops.BooleanField)saField;
            valueImpl = (BooleanValueImpl) vm.mirrorOf(bf.getValue(target));
        } else {
            throw new RuntimeException("Should not reach here");
        }
        return valueImpl;
    }

    public boolean equals(Object obj) {
        if ((obj != null) && (obj instanceof FieldImpl)) {
            FieldImpl other = (FieldImpl)obj;
            return (declaringType().equals(other.declaringType())) &&
                (ref().equals(other.ref())) &&
                super.equals(obj);
        } else {
            return false;
        }
    }

    public boolean isTransient() {
        return saField.isTransient();
    }

    public boolean isVolatile() {
        return saField.isVolatile();
    }

    public boolean isEnumConstant() {
        return saField.isEnumConstant();
    }

    public Type type() throws ClassNotLoadedException {
        // So, we do it just like JDI does by searching the enclosing type.
        return findType(signature());
    }

    public String typeName() { //fixme jjh: jpda version creates redundant JNITypeParsers
        getParser();
        return signatureParser.typeName();
    }

    public String genericSignature() {
        Symbol genSig = saField.getGenericSignature();
        return (genSig != null)? genSig.asString() : null;
    }

    // From interface Comparable
    public int compareTo(Field field) {
        ReferenceTypeImpl declaringType = (ReferenceTypeImpl)declaringType();
        int rc = declaringType.compareTo(field.declaringType());
        if (rc == 0) {
            rc = declaringType.indexOf(this) -
                declaringType.indexOf(field);
        }
        return rc;
    }

    // from interface Mirror
    public String toString() {
        StringBuffer buf = new StringBuffer();

        buf.append(declaringType().name());
        buf.append('.');
        buf.append(name());
        return buf.toString();
    }

    public String name() {
        FieldIdentifier myName =  saField.getID();
        return myName.getName();
    }

    // From interface Accessible
    public int modifiers() {
        return saField.getAccessFlagsObj().getStandardFlags();
    }

    public boolean isPackagePrivate() {
        return saField.isPackagePrivate();
    }

    public boolean isPrivate() {
        return saField.isPrivate();
    }

    public boolean isProtected() {
        return saField.isProtected();
    }

    public boolean isPublic() {
        return saField.isPublic();
    }

    public boolean isStatic() {
        return saField.isStatic();
    }

    public boolean isFinal() {
        return saField.isFinal();
    }

    public boolean isSynthetic() {
        return saField.isSynthetic();
    }

    public int hashCode() {
        return saField.hashCode();
    }


    private Type findType(String signature) throws ClassNotLoadedException {
        ReferenceTypeImpl enclosing = (ReferenceTypeImpl)declaringType();
        return enclosing.findType(signature);
    }

}
