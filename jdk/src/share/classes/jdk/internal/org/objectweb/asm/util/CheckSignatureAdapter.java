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
 * A {@link SignatureVisitor} that checks that its methods are properly used.
 *
 * @author Eric Bruneton
 */
public class CheckSignatureAdapter extends SignatureVisitor {

    /**
     * Type to be used to check class signatures. See
     * {@link #CheckSignatureAdapter(int, SignatureVisitor)
     * CheckSignatureAdapter}.
     */
    public static final int CLASS_SIGNATURE = 0;

    /**
     * Type to be used to check method signatures. See
     * {@link #CheckSignatureAdapter(int, SignatureVisitor)
     * CheckSignatureAdapter}.
     */
    public static final int METHOD_SIGNATURE = 1;

    /**
     * Type to be used to check type signatures.See
     * {@link #CheckSignatureAdapter(int, SignatureVisitor)
     * CheckSignatureAdapter}.
     */
    public static final int TYPE_SIGNATURE = 2;

    private static final int EMPTY = 1;

    private static final int FORMAL = 2;

    private static final int BOUND = 4;

    private static final int SUPER = 8;

    private static final int PARAM = 16;

    private static final int RETURN = 32;

    private static final int SIMPLE_TYPE = 64;

    private static final int CLASS_TYPE = 128;

    private static final int END = 256;

    /**
     * Type of the signature to be checked.
     */
    private final int type;

    /**
     * State of the automaton used to check the order of method calls.
     */
    private int state;

    /**
     * <tt>true</tt> if the checked type signature can be 'V'.
     */
    private boolean canBeVoid;

    /**
     * The visitor to which this adapter must delegate calls. May be
     * <tt>null</tt>.
     */
    private final SignatureVisitor sv;

    /**
     * Creates a new {@link CheckSignatureAdapter} object. <i>Subclasses must
     * not use this constructor</i>. Instead, they must use the
     * {@link #CheckSignatureAdapter(int, int, SignatureVisitor)} version.
     *
     * @param type
     *            the type of signature to be checked. See
     *            {@link #CLASS_SIGNATURE}, {@link #METHOD_SIGNATURE} and
     *            {@link #TYPE_SIGNATURE}.
     * @param sv
     *            the visitor to which this adapter must delegate calls. May be
     *            <tt>null</tt>.
     */
    public CheckSignatureAdapter(final int type, final SignatureVisitor sv) {
        this(Opcodes.ASM5, type, sv);
    }

    /**
     * Creates a new {@link CheckSignatureAdapter} object.
     *
     * @param api
     *            the ASM API version implemented by this visitor. Must be one
     *            of {@link Opcodes#ASM4} or {@link Opcodes#ASM5}.
     * @param type
     *            the type of signature to be checked. See
     *            {@link #CLASS_SIGNATURE}, {@link #METHOD_SIGNATURE} and
     *            {@link #TYPE_SIGNATURE}.
     * @param sv
     *            the visitor to which this adapter must delegate calls. May be
     *            <tt>null</tt>.
     */
    protected CheckSignatureAdapter(final int api, final int type,
            final SignatureVisitor sv) {
        super(api);
        this.type = type;
        this.state = EMPTY;
        this.sv = sv;
    }

    // class and method signatures

    @Override
    public void visitFormalTypeParameter(final String name) {
        if (type == TYPE_SIGNATURE
                || (state != EMPTY && state != FORMAL && state != BOUND)) {
            throw new IllegalStateException();
        }
        CheckMethodAdapter.checkIdentifier(name, "formal type parameter");
        state = FORMAL;
        if (sv != null) {
            sv.visitFormalTypeParameter(name);
        }
    }

    @Override
    public SignatureVisitor visitClassBound() {
        if (state != FORMAL) {
            throw new IllegalStateException();
        }
        state = BOUND;
        SignatureVisitor v = sv == null ? null : sv.visitClassBound();
        return new CheckSignatureAdapter(TYPE_SIGNATURE, v);
    }

    @Override
    public SignatureVisitor visitInterfaceBound() {
        if (state != FORMAL && state != BOUND) {
            throw new IllegalArgumentException();
        }
        SignatureVisitor v = sv == null ? null : sv.visitInterfaceBound();
        return new CheckSignatureAdapter(TYPE_SIGNATURE, v);
    }

    // class signatures

    @Override
    public SignatureVisitor visitSuperclass() {
        if (type != CLASS_SIGNATURE || (state & (EMPTY | FORMAL | BOUND)) == 0) {
            throw new IllegalArgumentException();
        }
        state = SUPER;
        SignatureVisitor v = sv == null ? null : sv.visitSuperclass();
        return new CheckSignatureAdapter(TYPE_SIGNATURE, v);
    }

    @Override
    public SignatureVisitor visitInterface() {
        if (state != SUPER) {
            throw new IllegalStateException();
        }
        SignatureVisitor v = sv == null ? null : sv.visitInterface();
        return new CheckSignatureAdapter(TYPE_SIGNATURE, v);
    }

    // method signatures

    @Override
    public SignatureVisitor visitParameterType() {
        if (type != METHOD_SIGNATURE
                || (state & (EMPTY | FORMAL | BOUND | PARAM)) == 0) {
            throw new IllegalArgumentException();
        }
        state = PARAM;
        SignatureVisitor v = sv == null ? null : sv.visitParameterType();
        return new CheckSignatureAdapter(TYPE_SIGNATURE, v);
    }

    @Override
    public SignatureVisitor visitReturnType() {
        if (type != METHOD_SIGNATURE
                || (state & (EMPTY | FORMAL | BOUND | PARAM)) == 0) {
            throw new IllegalArgumentException();
        }
        state = RETURN;
        SignatureVisitor v = sv == null ? null : sv.visitReturnType();
        CheckSignatureAdapter cv = new CheckSignatureAdapter(TYPE_SIGNATURE, v);
        cv.canBeVoid = true;
        return cv;
    }

    @Override
    public SignatureVisitor visitExceptionType() {
        if (state != RETURN) {
            throw new IllegalStateException();
        }
        SignatureVisitor v = sv == null ? null : sv.visitExceptionType();
        return new CheckSignatureAdapter(TYPE_SIGNATURE, v);
    }

    // type signatures

    @Override
    public void visitBaseType(final char descriptor) {
        if (type != TYPE_SIGNATURE || state != EMPTY) {
            throw new IllegalStateException();
        }
        if (descriptor == 'V') {
            if (!canBeVoid) {
                throw new IllegalArgumentException();
            }
        } else {
            if ("ZCBSIFJD".indexOf(descriptor) == -1) {
                throw new IllegalArgumentException();
            }
        }
        state = SIMPLE_TYPE;
        if (sv != null) {
            sv.visitBaseType(descriptor);
        }
    }

    @Override
    public void visitTypeVariable(final String name) {
        if (type != TYPE_SIGNATURE || state != EMPTY) {
            throw new IllegalStateException();
        }
        CheckMethodAdapter.checkIdentifier(name, "type variable");
        state = SIMPLE_TYPE;
        if (sv != null) {
            sv.visitTypeVariable(name);
        }
    }

    @Override
    public SignatureVisitor visitArrayType() {
        if (type != TYPE_SIGNATURE || state != EMPTY) {
            throw new IllegalStateException();
        }
        state = SIMPLE_TYPE;
        SignatureVisitor v = sv == null ? null : sv.visitArrayType();
        return new CheckSignatureAdapter(TYPE_SIGNATURE, v);
    }

    @Override
    public void visitClassType(final String name) {
        if (type != TYPE_SIGNATURE || state != EMPTY) {
            throw new IllegalStateException();
        }
        CheckMethodAdapter.checkInternalName(name, "class name");
        state = CLASS_TYPE;
        if (sv != null) {
            sv.visitClassType(name);
        }
    }

    @Override
    public void visitInnerClassType(final String name) {
        if (state != CLASS_TYPE) {
            throw new IllegalStateException();
        }
        CheckMethodAdapter.checkIdentifier(name, "inner class name");
        if (sv != null) {
            sv.visitInnerClassType(name);
        }
    }

    @Override
    public void visitTypeArgument() {
        if (state != CLASS_TYPE) {
            throw new IllegalStateException();
        }
        if (sv != null) {
            sv.visitTypeArgument();
        }
    }

    @Override
    public SignatureVisitor visitTypeArgument(final char wildcard) {
        if (state != CLASS_TYPE) {
            throw new IllegalStateException();
        }
        if ("+-=".indexOf(wildcard) == -1) {
            throw new IllegalArgumentException();
        }
        SignatureVisitor v = sv == null ? null : sv.visitTypeArgument(wildcard);
        return new CheckSignatureAdapter(TYPE_SIGNATURE, v);
    }

    @Override
    public void visitEnd() {
        if (state != CLASS_TYPE) {
            throw new IllegalStateException();
        }
        state = END;
        if (sv != null) {
            sv.visitEnd();
        }
    }
}
