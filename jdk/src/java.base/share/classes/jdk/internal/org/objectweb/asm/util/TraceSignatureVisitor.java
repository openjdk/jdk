/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package jdk.internal.org.objectweb.asm.util;

import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.signature.SignatureVisitor;

/**
 * A {@link SignatureVisitor} that prints a disassembled view of the signature
 * it visits.
 *
 * @author Eugene Kuleshov
 * @author Eric Bruneton
 */
public final class TraceSignatureVisitor extends SignatureVisitor {

    private final StringBuilder declaration;

    private boolean isInterface;

    private boolean seenFormalParameter;

    private boolean seenInterfaceBound;

    private boolean seenParameter;

    private boolean seenInterface;

    private StringBuilder returnType;

    private StringBuilder exceptions;

    /**
     * Stack used to keep track of class types that have arguments. Each element
     * of this stack is a boolean encoded in one bit. The top of the stack is
     * the lowest order bit. Pushing false = *2, pushing true = *2+1, popping =
     * /2.
     */
    private int argumentStack;

    /**
     * Stack used to keep track of array class types. Each element of this stack
     * is a boolean encoded in one bit. The top of the stack is the lowest order
     * bit. Pushing false = *2, pushing true = *2+1, popping = /2.
     */
    private int arrayStack;

    private String separator = "";

    public TraceSignatureVisitor(final int access) {
        super(Opcodes.ASM5);
        isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
        this.declaration = new StringBuilder();
    }

    private TraceSignatureVisitor(final StringBuilder buf) {
        super(Opcodes.ASM5);
        this.declaration = buf;
    }

    @Override
    public void visitFormalTypeParameter(final String name) {
        declaration.append(seenFormalParameter ? ", " : "<").append(name);
        seenFormalParameter = true;
        seenInterfaceBound = false;
    }

    @Override
    public SignatureVisitor visitClassBound() {
        separator = " extends ";
        startType();
        return this;
    }

    @Override
    public SignatureVisitor visitInterfaceBound() {
        separator = seenInterfaceBound ? ", " : " extends ";
        seenInterfaceBound = true;
        startType();
        return this;
    }

    @Override
    public SignatureVisitor visitSuperclass() {
        endFormals();
        separator = " extends ";
        startType();
        return this;
    }

    @Override
    public SignatureVisitor visitInterface() {
        separator = seenInterface ? ", " : isInterface ? " extends "
                : " implements ";
        seenInterface = true;
        startType();
        return this;
    }

    @Override
    public SignatureVisitor visitParameterType() {
        endFormals();
        if (seenParameter) {
            declaration.append(", ");
        } else {
            seenParameter = true;
            declaration.append('(');
        }
        startType();
        return this;
    }

    @Override
    public SignatureVisitor visitReturnType() {
        endFormals();
        if (seenParameter) {
            seenParameter = false;
        } else {
            declaration.append('(');
        }
        declaration.append(')');
        returnType = new StringBuilder();
        return new TraceSignatureVisitor(returnType);
    }

    @Override
    public SignatureVisitor visitExceptionType() {
        if (exceptions == null) {
            exceptions = new StringBuilder();
        } else {
            exceptions.append(", ");
        }
        // startType();
        return new TraceSignatureVisitor(exceptions);
    }

    @Override
    public void visitBaseType(final char descriptor) {
        switch (descriptor) {
        case 'V':
            declaration.append("void");
            break;
        case 'B':
            declaration.append("byte");
            break;
        case 'J':
            declaration.append("long");
            break;
        case 'Z':
            declaration.append("boolean");
            break;
        case 'I':
            declaration.append("int");
            break;
        case 'S':
            declaration.append("short");
            break;
        case 'C':
            declaration.append("char");
            break;
        case 'F':
            declaration.append("float");
            break;
        // case 'D':
        default:
            declaration.append("double");
            break;
        }
        endType();
    }

    @Override
    public void visitTypeVariable(final String name) {
        declaration.append(name);
        endType();
    }

    @Override
    public SignatureVisitor visitArrayType() {
        startType();
        arrayStack |= 1;
        return this;
    }

    @Override
    public void visitClassType(final String name) {
        if ("java/lang/Object".equals(name)) {
            // Map<java.lang.Object,java.util.List>
            // or
            // abstract public V get(Object key); (seen in Dictionary.class)
            // should have Object
            // but java.lang.String extends java.lang.Object is unnecessary
            boolean needObjectClass = argumentStack % 2 != 0 || seenParameter;
            if (needObjectClass) {
                declaration.append(separator).append(name.replace('/', '.'));
            }
        } else {
            declaration.append(separator).append(name.replace('/', '.'));
        }
        separator = "";
        argumentStack *= 2;
    }

    @Override
    public void visitInnerClassType(final String name) {
        if (argumentStack % 2 != 0) {
            declaration.append('>');
        }
        argumentStack /= 2;
        declaration.append('.');
        declaration.append(separator).append(name.replace('/', '.'));
        separator = "";
        argumentStack *= 2;
    }

    @Override
    public void visitTypeArgument() {
        if (argumentStack % 2 == 0) {
            ++argumentStack;
            declaration.append('<');
        } else {
            declaration.append(", ");
        }
        declaration.append('?');
    }

    @Override
    public SignatureVisitor visitTypeArgument(final char tag) {
        if (argumentStack % 2 == 0) {
            ++argumentStack;
            declaration.append('<');
        } else {
            declaration.append(", ");
        }

        if (tag == EXTENDS) {
            declaration.append("? extends ");
        } else if (tag == SUPER) {
            declaration.append("? super ");
        }

        startType();
        return this;
    }

    @Override
    public void visitEnd() {
        if (argumentStack % 2 != 0) {
            declaration.append('>');
        }
        argumentStack /= 2;
        endType();
    }

    public String getDeclaration() {
        return declaration.toString();
    }

    public String getReturnType() {
        return returnType == null ? null : returnType.toString();
    }

    public String getExceptions() {
        return exceptions == null ? null : exceptions.toString();
    }

    // -----------------------------------------------

    private void endFormals() {
        if (seenFormalParameter) {
            declaration.append('>');
            seenFormalParameter = false;
        }
    }

    private void startType() {
        arrayStack *= 2;
    }

    private void endType() {
        if (arrayStack % 2 == 0) {
            arrayStack /= 2;
        } else {
            while (arrayStack % 2 != 0) {
                arrayStack /= 2;
                declaration.append("[]");
            }
        }
    }
}
