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
import sun.jvm.hotspot.oops.Symbol;
import sun.jvm.hotspot.oops.LocalVariableTableElement;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.lang.ref.SoftReference;
import java.util.Collections;

public abstract class MethodImpl extends TypeComponentImpl implements Method {
    private JNITypeParser signatureParser;
    protected sun.jvm.hotspot.oops.Method saMethod;

    abstract int argSlotCount() throws AbsentInformationException;
    abstract List allLineLocations(SDE.Stratum stratum,
                                   String sourceName)
                           throws AbsentInformationException;
    abstract List locationsOfLine(SDE.Stratum stratum,
                                  String sourceName,
                                  int lineNumber)
                           throws AbsentInformationException;

    static MethodImpl createMethodImpl(VirtualMachine vm, ReferenceTypeImpl declaringType,
                                       sun.jvm.hotspot.oops.Method saMethod) {
        // Someday might have to add concrete and non-concrete subclasses.
        if (saMethod.isNative() || saMethod.isAbstract()) {
            return new NonConcreteMethodImpl(vm, declaringType, saMethod);
        } else {
            return new ConcreteMethodImpl(vm, declaringType, saMethod);
        }
    }

    MethodImpl(VirtualMachine vm, ReferenceTypeImpl declaringType,
               sun.jvm.hotspot.oops.Method saMethod ) {
        super(vm, declaringType);
        this.saMethod = saMethod;
        getParser();
    }

    private JNITypeParser getParser() {
        if (signatureParser == null) {
            Symbol sig1 = saMethod.getSignature();
            signature = sig1.asString();
            signatureParser = new JNITypeParser(signature);
        }
        return signatureParser;
    }

    // Object ref() {
    sun.jvm.hotspot.oops.Method ref() {
        return saMethod;
    }

    public String genericSignature() {
        Symbol genSig = saMethod.getGenericSignature();
        return (genSig != null)? genSig.asString() : null;
    }

    public String returnTypeName() {
        return getParser().typeName();
    }

    public Type returnType() throws ClassNotLoadedException {
        return findType(getParser().signature());
    }

    private Type findType(String signature) throws ClassNotLoadedException {
        ReferenceTypeImpl enclosing = (ReferenceTypeImpl)declaringType();
        return enclosing.findType(signature);
    }

    public List argumentTypeNames() {
        return getParser().argumentTypeNames();
    }

    List argumentSignatures() {
        return getParser().argumentSignatures();
    }

    Type argumentType(int index) throws ClassNotLoadedException {
        ReferenceTypeImpl enclosing = (ReferenceTypeImpl)declaringType();
        String signature = (String)argumentSignatures().get(index);
        return enclosing.findType(signature);
    }

    public List argumentTypes() throws ClassNotLoadedException {
        int size = argumentSignatures().size();
        ArrayList types = new ArrayList(size);
        for (int i = 0; i < size; i++) {
            Type type = argumentType(i);
            types.add(type);
        }
        return types;
    }

    public boolean isAbstract() {
        return saMethod.isAbstract();
    }

    public boolean isBridge() {
        return saMethod.isBridge();
    }

    public boolean isSynchronized() {
        return saMethod.isSynchronized();
    }

    public boolean isNative() {
        return saMethod.isNative();
    }

    public boolean isVarArgs() {
        return saMethod.isVarArgs();
    }

    public boolean isConstructor() {
        return saMethod.isConstructor();
    }

    public boolean isStaticInitializer() {
        return saMethod.isStaticInitializer();
    }

    public boolean isObsolete() {
        return saMethod.isObsolete();
    }

    public final List allLineLocations()
                           throws AbsentInformationException {
        return allLineLocations(vm.getDefaultStratum(), null);
    }

    public List allLineLocations(String stratumID,
                                 String sourceName)
                           throws AbsentInformationException {
        return allLineLocations(declaringType.stratum(stratumID),
                                sourceName);
    }

    public final List locationsOfLine(int lineNumber)
                           throws AbsentInformationException {
        return locationsOfLine(vm.getDefaultStratum(),
                               null, lineNumber);
    }

    public List locationsOfLine(String stratumID,
                                String sourceName,
                                int lineNumber)
                           throws AbsentInformationException {
        return locationsOfLine(declaringType.stratum(stratumID),
                               sourceName, lineNumber);
    }

    LineInfo codeIndexToLineInfo(SDE.Stratum stratum,
                                 long codeIndex) {
        if (stratum.isJava()) {
            return new BaseLineInfo(-1, declaringType);
        } else {
            return new StratumLineInfo(stratum.id(), -1,
                                       null, null);
        }
    }

    public boolean equals(Object obj) {
        if ((obj != null) && (obj instanceof MethodImpl)) {
            MethodImpl other = (MethodImpl)obj;
            return (declaringType().equals(other.declaringType())) &&
                (ref().equals(other.ref())) &&
                super.equals(obj);
        } else {
            return false;
        }
    }

    // From interface Comparable
    public int compareTo(Method method) {
        ReferenceTypeImpl declaringType = (ReferenceTypeImpl)declaringType();
         int rc = declaringType.compareTo(method.declaringType());
         if (rc == 0) {
           rc = declaringType.indexOf(this) -
               declaringType.indexOf(method);
         }
         return rc;
    }

    // from interface Mirror
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(declaringType().name());
        sb.append(".");
        sb.append(name());
        sb.append("(");
        boolean first = true;
        for (Iterator it = argumentTypeNames().iterator(); it.hasNext();) {
            if (!first) {
                sb.append(", ");
            }
            sb.append((String)it.next());
            first = false;
        }
        sb.append(")");
        return sb.toString();
    }

    public String name() {
        Symbol myName = saMethod.getName();
        return myName.asString();
    }

    public int modifiers() {
        return saMethod.getAccessFlagsObj().getStandardFlags();
    }

    public boolean isPackagePrivate() {
        return saMethod.isPackagePrivate();
    }

    public boolean isPrivate() {
        return saMethod.isPrivate();
    }

    public boolean isProtected() {
        return saMethod.isProtected();
    }

    public boolean isPublic() {
        return saMethod.isPublic();
    }

    public boolean isStatic() {
        return saMethod.isStatic();
    }

    public boolean isSynthetic() {
        return saMethod.isSynthetic();
    }

    public boolean isFinal() {
        return saMethod.isFinal();
    }

    public int hashCode() {
        return saMethod.hashCode();
    }
}
