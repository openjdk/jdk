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

import jdk.internal.org.objectweb.asm.ModuleVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/**
 * A {@link ModuleVisitor} that prints the fields it visits with a
 * {@link Printer}.
 *
 * @author Remi Forax
 */
public final class TraceModuleVisitor extends ModuleVisitor {

    public final Printer p;

    public TraceModuleVisitor(final Printer p) {
        this(null, p);
    }

    public TraceModuleVisitor(final ModuleVisitor mv, final Printer p) {
        super(Opcodes.ASM6, mv);
        this.p = p;
    }

    @Override
    public void visitMainClass(String mainClass) {
        p.visitMainClass(mainClass);
        super.visitMainClass(mainClass);
    }

    @Override
    public void visitPackage(String packaze) {
        p.visitPackage(packaze);
        super.visitPackage(packaze);
    }

    @Override
    public void visitRequire(String module, int access, String version) {
        p.visitRequire(module, access, version);
        super.visitRequire(module, access, version);
    }

    @Override
    public void visitExport(String packaze, int access, String... modules) {
        p.visitExport(packaze, access, modules);
        super.visitExport(packaze, access, modules);
    }

    @Override
    public void visitOpen(String packaze, int access, String... modules) {
        p.visitOpen(packaze, access, modules);
        super.visitOpen(packaze, access, modules);
    }

    @Override
    public void visitUse(String use) {
        p.visitUse(use);
        super.visitUse(use);
    }

    @Override
    public void visitProvide(String service, String... providers) {
        p.visitProvide(service, providers);
        super.visitProvide(service, providers);
    }

    @Override
    public void visitEnd() {
        p.visitModuleEnd();
        super.visitEnd();
    }
}
