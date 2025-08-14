/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.classfile.instruction;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Label;
import java.lang.classfile.PseudoInstruction;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.util.Optional;

import jdk.internal.classfile.impl.AbstractPseudoInstruction;

/**
 * A pseudo-instruction modeling an entry in the {@code exception_table} array
 * of a {@link CodeAttribute Code} attribute.  Catch (JVMS {@jvms 3.12}) and
 * finally (JVMS {@jvms 3.14}) blocks in Java source code compile to exception
 * table entries.  Delivered as a {@link CodeElement} when traversing the
 * contents of a {@link CodeModel}.
 * <p>
 * An exception table entry is composite:
 * {@snippet lang=text :
 * // @link substring="ExceptionCatch" target="#of(Label, Label, Label, Optional)" :
 * ExceptionCatch(
 *     Label handler, // @link substring="handler" target="#handler"
 *     Label tryStart, // @link substring="tryStart" target="#tryStart"
 *     Label tryEnd, // @link substring="tryEnd" target="#tryEnd"
 *     Optional<ClassEntry> catchType // @link substring="catchType" target="#catchType"
 * )
 * }
 *
 * @see CodeBuilder#exceptionCatch CodeBuilder::exceptionCatch
 * @see CodeAttribute#exceptionHandlers()
 * @jvms 4.7.3 The {@code Code} Attribute
 * @since 24
 */
public sealed interface ExceptionCatch extends PseudoInstruction
        permits AbstractPseudoInstruction.ExceptionCatchImpl {
    /**
     * {@return the handler for the exception}
     */
    Label handler();

    /**
     * {@return the beginning of the instruction range for the guarded instructions}
     */
    Label tryStart();

    /**
     * {@return the end of the instruction range for the guarded instructions}
     */
    Label tryEnd();

    /**
     * {@return the type of the exception to catch, or empty if this handler
     * catches everything}
     */
    Optional<ClassEntry> catchType();

    /**
     * {@return an exception table pseudo-instruction}
     * @param handler the handler for the exception
     * @param tryStart the beginning of the instruction range for the guarded instructions
     * @param tryEnd the end of the instruction range for the guarded instructions
     * @param catchTypeEntry the type of exception to catch, or empty if this
     *                       handler is unconditional
     */
    static ExceptionCatch of(Label handler, Label tryStart, Label tryEnd,
                             Optional<ClassEntry> catchTypeEntry) {
        return new AbstractPseudoInstruction.ExceptionCatchImpl(handler, tryStart, tryEnd, catchTypeEntry.orElse(null));
    }

    /**
     * {@return an exception table pseudo-instruction to catch everything}
     * @param handler the handler for the exception
     * @param tryStart the beginning of the instruction range for the guarded instructions
     * @param tryEnd the end of the instruction range for the guarded instructions
     */
    static ExceptionCatch of(Label handler, Label tryStart, Label tryEnd) {
        return new AbstractPseudoInstruction.ExceptionCatchImpl(handler, tryStart, tryEnd, (ClassEntry) null);
    }
}
