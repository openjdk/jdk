/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.ArrayList;
import java.util.List;
import com.sun.tools.classfile.Type.*;

/**
 * See JVMS3 4.4.4.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Signature extends Descriptor {

    public Signature(int index) {
        super(index);
    }

    public Type getType(ConstantPool constant_pool) throws ConstantPoolException {
        if (type == null)
            type = parse(getValue(constant_pool));
        return type;
    }

    @Override
    public int getParameterCount(ConstantPool constant_pool) throws ConstantPoolException {
        MethodType m = (MethodType) getType(constant_pool);
        return m.paramTypes.size();
    }

    @Override
    public String getParameterTypes(ConstantPool constant_pool) throws ConstantPoolException {
        MethodType m = (MethodType) getType(constant_pool);
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        String sep = "";
        for (Type paramType: m.paramTypes) {
            sb.append(sep);
            sb.append(paramType);
            sep = ", ";
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String getReturnType(ConstantPool constant_pool) throws ConstantPoolException {
        MethodType m = (MethodType) getType(constant_pool);
        return m.returnType.toString();
    }

    @Override
    public String getFieldType(ConstantPool constant_pool) throws ConstantPoolException {
        return getType(constant_pool).toString();
    }

    private Type parse(String sig) {
        this.sig = sig;
        sigp = 0;

        List<TypeParamType> typeParamTypes = null;
        if (sig.charAt(sigp) == '<')
            typeParamTypes = parseTypeParamTypes();

        if (sig.charAt(sigp) == '(') {
            List<Type> paramTypes = parseTypeSignatures(')');
            Type returnType = parseTypeSignature();
            List<Type> throwsTypes = null;
            while (sigp < sig.length() && sig.charAt(sigp) == '^') {
                sigp++;
                if (throwsTypes == null)
                    throwsTypes = new ArrayList<Type>();
                throwsTypes.add(parseTypeSignature());
            }
            return new MethodType(typeParamTypes, paramTypes, returnType, throwsTypes);
        } else {
            Type t = parseTypeSignature();
            if (typeParamTypes == null && sigp == sig.length())
                return t;
            Type superclass = t;
            List<Type> superinterfaces = null;
            while (sigp < sig.length()) {
                if (superinterfaces == null)
                    superinterfaces = new ArrayList<Type>();
                superinterfaces.add(parseTypeSignature());
            }
            return new ClassSigType(typeParamTypes, superclass, superinterfaces);

        }
    }

    private Type parseTypeSignature() {
        switch (sig.charAt(sigp)) {
            case 'B':
                sigp++;
                return new SimpleType("byte");

            case 'C':
                sigp++;
                return new SimpleType("char");

            case 'D':
                sigp++;
                return new SimpleType("double");

            case 'F':
                sigp++;
                return new SimpleType("float");

            case 'I':
                sigp++;
                return new SimpleType("int");

            case 'J':
                sigp++;
                return new SimpleType("long");

            case 'L':
                return parseClassTypeSignature();

            case 'S':
                sigp++;
                return new SimpleType("short");

            case 'T':
                return parseTypeVariableSignature();

            case 'V':
                sigp++;
                return new SimpleType("void");

            case 'Z':
                sigp++;
                return new SimpleType("boolean");

            case '[':
                sigp++;
                return new ArrayType(parseTypeSignature());

            case '*':
                sigp++;
                return new WildcardType();

            case '+':
                sigp++;
                return new WildcardType(WildcardType.Kind.EXTENDS, parseTypeSignature());

            case '-':
                sigp++;
                return new WildcardType(WildcardType.Kind.SUPER, parseTypeSignature());

            default:
                throw new IllegalStateException(debugInfo());
        }
    }

    private List<Type> parseTypeSignatures(char term) {
        sigp++;
        List<Type> types = new ArrayList<Type>();
        while (sig.charAt(sigp) != term)
            types.add(parseTypeSignature());
        sigp++;
        return types;
    }

    private Type parseClassTypeSignature() {
        assert sig.charAt(sigp) == 'L';
        sigp++;
        return parseClassTypeSignatureRest();
    }

    private Type parseClassTypeSignatureRest() {
        StringBuilder sb = new StringBuilder();
        List<Type> argTypes = null;
        ClassType t = null;
        char sigch ;

        do {
            switch  (sigch = sig.charAt(sigp)) {
                case '<':
                    argTypes = parseTypeSignatures('>');
                    break;

                case '.':
                case ';':
                    sigp++;
                    t = new ClassType(t, sb.toString(), argTypes);
                    sb.setLength(0);
                    argTypes = null;
                    break;

                default:
                    sigp++;
                    sb.append(sigch);
                    break;
            }
        } while (sigch != ';');

        return t;
    }

    private List<TypeParamType> parseTypeParamTypes() {
        assert sig.charAt(sigp) == '<';
        sigp++;
        List<TypeParamType> types = new ArrayList<TypeParamType>();
        while (sig.charAt(sigp) != '>')
            types.add(parseTypeParamType());
        sigp++;
        return types;
    }

    private TypeParamType parseTypeParamType() {
        int sep = sig.indexOf(":", sigp);
        String name = sig.substring(sigp, sep);
        Type classBound = null;
        List<Type> interfaceBounds = null;
        sigp = sep + 1;
        if (sig.charAt(sigp) != ':')
            classBound = parseTypeSignature();
        while (sig.charAt(sigp) == ':') {
            sigp++;
            if (interfaceBounds == null)
                interfaceBounds = new ArrayList<Type>();
            interfaceBounds.add(parseTypeSignature());
        }
        return new TypeParamType(name, classBound, interfaceBounds);
    }

    private Type parseTypeVariableSignature() {
        sigp++;
        int sep = sig.indexOf(';', sigp);
        Type t = new SimpleType(sig.substring(sigp, sep));
        sigp = sep + 1;
        return t;
    }

    private String debugInfo() {
        return sig.substring(0, sigp) + "!" + sig.charAt(sigp) + "!" + sig.substring(sigp+1);
    }

    private String sig;
    private int sigp;

    private Type type;
}
