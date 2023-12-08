/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.classfile.attribute;

import jdk.internal.classfile.impl.UnboundAttribute;
import jdk.internal.javac.PreviewFeature;

/**
 * Models a single character range in the {@link CharacterRangeTableAttribute}.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface CharacterRangeInfo
        permits UnboundAttribute.UnboundCharacterRangeInfo {

    /**
     * {@return the start of the character range region (inclusive)}  This is
     * the index into the code array at which the code for this character range
     * begins.
     */
    int startPc();

    /**
     * {@return the end of the character range region (exclusive)}  This is the
     * index into the code array after which the code for this character range
     * ends.
     */
    int endPc();

    /**
     * {@return the encoded start of the character range region (inclusive)}
     * The value is constructed from the line_number/column_number pair as given
     * by {@code line_number << 10 + column_number}, where the source file is
     * viewed as an array of (possibly multi-byte) characters.
     */
    int characterRangeStart();

    /**
     * {@return the encoded end of the character range region (exclusive)}.
     * The value is constructed from the line_number/column_number pair as given
     * by {@code line_number << 10 + column_number}, where the source file is
     * viewed as an array of (possibly multi-byte) characters.
     */
    int characterRangeEnd();

    /**
     * The value of the flags item describes the kind of range. Multiple flags
     * may be set within flags.
     * <ul>
     * <li>{@link java.lang.classfile.ClassFile#CRT_STATEMENT} Range is a Statement
     * (except ExpressionStatement), StatementExpression {@jls 14.8}, as well as each
     * VariableDeclaratorId = VariableInitializer of
     * LocalVariableDeclarationStatement {@jls 14.4} or FieldDeclaration {@jls 8.3} in the
     * grammar.
     * <li>{@link java.lang.classfile.ClassFile#CRT_BLOCK} Range is a Block in the
     * grammar.
     * <li>{@link java.lang.classfile.ClassFile#CRT_ASSIGNMENT} Range is an assignment
     * expression - Expression1 AssignmentOperator Expression1 in the grammar as
     * well as increment and decrement expressions (both prefix and postfix).
     * <li>{@link java.lang.classfile.ClassFile#CRT_FLOW_CONTROLLER} An expression
     * whose value will effect control flow. {@code Flowcon} in the following:
     * <pre>
     * if ( Flowcon ) Statement [else Statement]
     * for ( ForInitOpt ; [Flowcon] ; ForUpdateOpt ) Statement
     * while ( Flowcon ) Statement
     * do Statement while ( Flowcon ) ;
     * switch ( Flowcon ) { SwitchBlockStatementGroups }
     * Flowcon || Expression3
     * Flowcon &amp;&amp; Expression3
     * Flowcon ? Expression : Expression1
     * </pre>
     * <li>{@link java.lang.classfile.ClassFile#CRT_FLOW_TARGET} Statement or
     * expression effected by a CRT_FLOW_CONTROLLER. {@code Flowtarg} in the following:
     * <pre>
     * if ( Flowcon ) Flowtarg [else Flowtarg]
     * for ( ForInitOpt ; [Flowcon] ; ForUpdateOpt ) Flowtarg
     * while ( Flowcon ) Flowtarg
     * do Flowtarg while ( Flowcon ) ;
     * Flowcon || Flowtarg
     * Flowcon &amp;&amp; Flowtarg
     * Flowcon ? Flowtarg : Flowtarg
     * </pre>
     * <li>{@link java.lang.classfile.ClassFile#CRT_INVOKE} Method invocation. For
     * example: Identifier Arguments.
     * <li>{@link java.lang.classfile.ClassFile#CRT_CREATE} New object creation. For
     * example: new Creator.
     * <li>{@link java.lang.classfile.ClassFile#CRT_BRANCH_TRUE} A condition encoded
     * in the branch instruction immediately contained in the code range for
     * this item is not inverted towards the corresponding branch condition in
     * the source code. I.e. actual jump occurs if and only if the the source
     * code branch condition evaluates to true. Entries of this type are
     * produced only for conditions that are listed in the description of
     * CRT_FLOW_CONTROLLER flag. The source range for the entry contains flow
     * controlling expression. start_pc field for an entry of this type must
     * point to a branch instruction: if_acmp&lt;cond&gt;, if_icmp&lt;cond&gt;,
     * if&lt;cond&gt;, ifnonull, ifnull or goto. CRT_BRANCH_TRUE and
     * CRT_BRANCH_FALSE are special kinds of entries that can be used to
     * determine what branch of a condition was chosen during the runtime.
     * <li>{@link java.lang.classfile.ClassFile#CRT_BRANCH_FALSE} A condition encoded
     * in the branch instruction immediately contained in the code range for
     * this item is inverted towards the corresponding branch condition in the
     * source code. I.e. actual jump occurs if and only if the the source code
     * branch condition evaluates to false. Entries of this type are produced
     * only for conditions that are listed in the description of
     * CRT_FLOW_CONTROLLER flag. The source range for the entry contains flow
     * controlling expression. start_pc field for an entry of this type must
     * point to a branch instruction: if_acmp&lt;cond&gt;, if_icmp&lt;cond&gt;,
     * if&lt;cond&gt;, ifnonull, ifnull or goto.
     * </ul>
     * <p>
     * All bits of the flags item not assigned above are reserved for future use. They should be set to zero in generated class files and should be ignored by Java virtual machine implementations.
     *
     * @return the flags
     */
    int flags();

    /**
     * {@return a character range description}
     * @param startPc the start of the bytecode range, inclusive
     * @param endPc the end of the bytecode range, exclusive
     * @param characterRangeStart the start of the character range, inclusive,
     *                            encoded as {@code line_number << 10 + column_number}
     * @param characterRangeEnd the end of the character range, exclusive,
     *                          encoded as {@code line_number << 10 + column_number}
     * @param flags the range flags
     */
    static CharacterRangeInfo of(int startPc,
                                 int endPc,
                                 int characterRangeStart,
                                 int characterRangeEnd,
                                 int flags) {
        return new UnboundAttribute.UnboundCharacterRangeInfo(startPc, endPc,
                                                              characterRangeStart, characterRangeEnd,
                                                              flags);
    }
}
