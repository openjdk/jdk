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
        Type.MethodType m = (Type.MethodType) getType(constant_pool);
        return m.argTypes.size();
    }

    @Override
    public String getParameterTypes(ConstantPool constant_pool) throws ConstantPoolException {
        Type.MethodType m = (Type.MethodType) getType(constant_pool);
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        String sep = "";
        for (Type argType: m.argTypes) {
            sb.append(sep);
            sb.append(argType);
            sep = ", ";
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String getReturnType(ConstantPool constant_pool) throws ConstantPoolException {
        Type.MethodType m = (Type.MethodType) getType(constant_pool);
        return m.returnType.toString();
    }

    @Override
    public String getFieldType(ConstantPool constant_pool) throws ConstantPoolException {
        return getType(constant_pool).toString();
    }

    private Type parse(String sig) {
        this.sig = sig;
        sigp = 0;

        List<Type> typeArgTypes = null;
        if (sig.charAt(sigp) == '<')
            typeArgTypes = parseTypeArgTypes();

        if (sig.charAt(sigp) == '(') {
            List<Type> argTypes = parseTypeSignatures(')');
            Type returnType = parseTypeSignature();
            List<Type> throwsTypes = null;
            while (sigp < sig.length() && sig.charAt(sigp) == '^') {
                sigp++;
                if (throwsTypes == null)
                    throwsTypes = new ArrayList<Type>();
                throwsTypes.add(parseTypeSignature());
            }
            return new Type.MethodType(typeArgTypes, argTypes, returnType, throwsTypes);
        } else {
            Type t = parseTypeSignature();
            if (typeArgTypes == null && sigp == sig.length())
                return t;
            Type superclass = t;
            List<Type> superinterfaces = new ArrayList<Type>();
            while (sigp < sig.length())
                superinterfaces.add(parseTypeSignature());
            return new Type.ClassSigType(typeArgTypes, superclass, superinterfaces);

        }
    }

    private Type parseTypeSignature() {
        switch (sig.charAt(sigp)) {
            case 'B':
                sigp++;
                return new Type.SimpleType("byte");

            case 'C':
                sigp++;
                return new Type.SimpleType("char");

            case 'D':
                sigp++;
                return new Type.SimpleType("double");

            case 'F':
                sigp++;
                return new Type.SimpleType("float");

            case 'I':
                sigp++;
                return new Type.SimpleType("int");

            case 'J':
                sigp++;
                return new Type.SimpleType("long");

            case 'L':
                return parseClassTypeSignature();

            case 'S':
                sigp++;
                return new Type.SimpleType("short");

            case 'T':
                return parseTypeVariableSignature();

            case 'V':
                sigp++;
                return new Type.SimpleType("void");

            case 'Z':
                sigp++;
                return new Type.SimpleType("boolean");

            case '[':
                sigp++;
                return new Type.ArrayType(parseTypeSignature());

            case '*':
                sigp++;
                return new Type.WildcardType();

            case '+':
                sigp++;
                return new Type.WildcardType("extends", parseTypeSignature());

            case '-':
                sigp++;
                return new Type.WildcardType("super", parseTypeSignature());

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
        Type t = null;
        char sigch;
        while (true) {
            switch  (sigch = sig.charAt(sigp)) {
                case '/':
                    sigp++;
                    sb.append(".");
                    break;

                case '.':
                    sigp++;
                    if (t == null)
                        t = new Type.SimpleType(sb.toString());
                    return new Type.InnerClassType(t, parseClassTypeSignatureRest());

                case ';':
                    sigp++;
                    if (t == null)
                        t = new Type.SimpleType(sb.toString());
                    return t;

                case '<':
                    List<Type> argTypes = parseTypeSignatures('>');
                    t = new Type.ClassType(sb.toString(), argTypes);
                    break;

                default:
                    sigp++;
                    sb.append(sigch);
                    break;
            }
        }
    }

    private List<Type> parseTypeArgTypes() {
        assert sig.charAt(sigp) == '<';
        sigp++;
        List<Type> types = null;
        types = new ArrayList<Type>();
        while (sig.charAt(sigp) != '>')
            types.add(parseTypeArgType());
        sigp++;
        return types;
    }

    private Type parseTypeArgType() {
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
        return new Type.TypeArgType(name, classBound, interfaceBounds);
    }

    private Type parseTypeVariableSignature() {
        sigp++;
        int sep = sig.indexOf(';', sigp);
        Type t = new Type.SimpleType(sig.substring(sigp, sep));
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
