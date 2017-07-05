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
package jdk.internal.org.objectweb.asm.signature;

/**
 * A type signature parser to make a signature visitor visit an existing
 * signature.
 *
 * @author Thomas Hallgren
 * @author Eric Bruneton
 */
public class SignatureReader {

    /**
     * The signature to be read.
     */
    private final String signature;

    /**
     * Constructs a {@link SignatureReader} for the given signature.
     *
     * @param signature
     *            A <i>ClassSignature</i>, <i>MethodTypeSignature</i>, or
     *            <i>FieldTypeSignature</i>.
     */
    public SignatureReader(final String signature) {
        this.signature = signature;
    }

    /**
     * Makes the given visitor visit the signature of this
     * {@link SignatureReader}. This signature is the one specified in the
     * constructor (see {@link #SignatureReader(String) SignatureReader}). This
     * method is intended to be called on a {@link SignatureReader} that was
     * created using a <i>ClassSignature</i> (such as the <code>signature</code>
     * parameter of the {@link jdk.internal.org.objectweb.asm.ClassVisitor#visit
     * ClassVisitor.visit} method) or a <i>MethodTypeSignature</i> (such as the
     * <code>signature</code> parameter of the
     * {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitMethod
     * ClassVisitor.visitMethod} method).
     *
     * @param v
     *            the visitor that must visit this signature.
     */
    public void accept(final SignatureVisitor v) {
        String signature = this.signature;
        int len = signature.length();
        int pos;
        char c;

        if (signature.charAt(0) == '<') {
            pos = 2;
            do {
                int end = signature.indexOf(':', pos);
                v.visitFormalTypeParameter(signature.substring(pos - 1, end));
                pos = end + 1;

                c = signature.charAt(pos);
                if (c == 'L' || c == '[' || c == 'T') {
                    pos = parseType(signature, pos, v.visitClassBound());
                }

                while ((c = signature.charAt(pos++)) == ':') {
                    pos = parseType(signature, pos, v.visitInterfaceBound());
                }
            } while (c != '>');
        } else {
            pos = 0;
        }

        if (signature.charAt(pos) == '(') {
            pos++;
            while (signature.charAt(pos) != ')') {
                pos = parseType(signature, pos, v.visitParameterType());
            }
            pos = parseType(signature, pos + 1, v.visitReturnType());
            while (pos < len) {
                pos = parseType(signature, pos + 1, v.visitExceptionType());
            }
        } else {
            pos = parseType(signature, pos, v.visitSuperclass());
            while (pos < len) {
                pos = parseType(signature, pos, v.visitInterface());
            }
        }
    }

    /**
     * Makes the given visitor visit the signature of this
     * {@link SignatureReader}. This signature is the one specified in the
     * constructor (see {@link #SignatureReader(String) SignatureReader}). This
     * method is intended to be called on a {@link SignatureReader} that was
     * created using a <i>FieldTypeSignature</i>, such as the
     * <code>signature</code> parameter of the
     * {@link jdk.internal.org.objectweb.asm.ClassVisitor#visitField ClassVisitor.visitField}
     * or {@link jdk.internal.org.objectweb.asm.MethodVisitor#visitLocalVariable
     * MethodVisitor.visitLocalVariable} methods.
     *
     * @param v
     *            the visitor that must visit this signature.
     */
    public void acceptType(final SignatureVisitor v) {
        parseType(this.signature, 0, v);
    }

    /**
     * Parses a field type signature and makes the given visitor visit it.
     *
     * @param signature
     *            a string containing the signature that must be parsed.
     * @param pos
     *            index of the first character of the signature to parsed.
     * @param v
     *            the visitor that must visit this signature.
     * @return the index of the first character after the parsed signature.
     */
    private static int parseType(final String signature, int pos,
            final SignatureVisitor v) {
        char c;
        int start, end;
        boolean visited, inner;
        String name;

        switch (c = signature.charAt(pos++)) {
        case 'Z':
        case 'C':
        case 'B':
        case 'S':
        case 'I':
        case 'F':
        case 'J':
        case 'D':
        case 'V':
            v.visitBaseType(c);
            return pos;

        case '[':
            return parseType(signature, pos, v.visitArrayType());

        case 'T':
            end = signature.indexOf(';', pos);
            v.visitTypeVariable(signature.substring(pos, end));
            return end + 1;

        default: // case 'L':
            start = pos;
            visited = false;
            inner = false;
            for (;;) {
                switch (c = signature.charAt(pos++)) {
                case '.':
                case ';':
                    if (!visited) {
                        name = signature.substring(start, pos - 1);
                        if (inner) {
                            v.visitInnerClassType(name);
                        } else {
                            v.visitClassType(name);
                        }
                    }
                    if (c == ';') {
                        v.visitEnd();
                        return pos;
                    }
                    start = pos;
                    visited = false;
                    inner = true;
                    break;

                case '<':
                    name = signature.substring(start, pos - 1);
                    if (inner) {
                        v.visitInnerClassType(name);
                    } else {
                        v.visitClassType(name);
                    }
                    visited = true;
                    top: for (;;) {
                        switch (c = signature.charAt(pos)) {
                        case '>':
                            break top;
                        case '*':
                            ++pos;
                            v.visitTypeArgument();
                            break;
                        case '+':
                        case '-':
                            pos = parseType(signature, pos + 1,
                                    v.visitTypeArgument(c));
                            break;
                        default:
                            pos = parseType(signature, pos,
                                    v.visitTypeArgument('='));
                            break;
                        }
                    }
                }
            }
        }
    }
}
