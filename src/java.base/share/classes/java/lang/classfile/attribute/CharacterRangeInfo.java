/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.instruction.CharacterRange;

import jdk.internal.classfile.impl.UnboundAttribute;
import jdk.internal.classfile.impl.Util;

/**
 * Models a single character range entry in the {@link
 * CharacterRangeTableAttribute}.
 * <p>
 * Each character range entry associates a range of indices in the code array
 * with a range of character positions in the source file.  A character position
 * in the source file is represented by a line number and a column number, and
 * its value is encoded as {@code lineNumber << 10 + columnNumber}.  Note that
 * column numbers are not the same as byte indices in a column as multibyte
 * characters may be present in the source file.
 *
 * Each character range entry includes a
 * flag which indicates what kind of range is described: statement, assignment,
 * method call, etc.
 *
 * @see CharacterRangeTableAttribute#characterRangeTable()
 * @see CharacterRange
 * @since 24
 */
public sealed interface CharacterRangeInfo
        permits UnboundAttribute.UnboundCharacterRangeInfo {

    /**
     * {@return the start of indices in the code array, inclusive}
     *
     * @see CharacterRange#startScope()
     */
    int startPc();

    /**
     * {@return the end of indices in the code array, exclusive}
     *
     * @see CharacterRange#endScope()
     */
    int endPc();

    /**
     * {@return the encoded start of character positions in the source file,
     * inclusive}
     */
    int characterRangeStart();

    /**
     * {@return the encoded end of character positions in the source file,
     * exclusive}
     */
    int characterRangeEnd();

    /**
     * {@return the flags of this character range entry}
     * <p>
     * The value of the flags item describes the kind of range. Multiple flags
     * may be set within flags.
     * <ul>
     * <li>{@link CharacterRange#FLAG_STATEMENT} Range is a Statement
     * (except ExpressionStatement), StatementExpression (JLS {@jls 14.8}), as
     * well as each {@code VariableDeclaratorId = VariableInitializer} of
     * LocalVariableDeclarationStatement (JLS {@jls 14.4}) or FieldDeclaration
     * (JLS {@jls 8.3}) in the grammar.
     * <li>{@link CharacterRange#FLAG_BLOCK} Range is a Block in the grammar.
     * <li>{@link CharacterRange#FLAG_ASSIGNMENT} Range is an assignment
     * expression - {@code Expression1 AssignmentOperator Expression1} in the
     * grammar as well as increment and decrement expressions (both prefix and
     * postfix).
     * <li>{@link CharacterRange#FLAG_FLOW_CONTROLLER} An expression
     * whose value will affect control flow. {@code Flowcon} in the following:
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
     * <li>{@link CharacterRange#FLAG_FLOW_TARGET} Statement or
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
     * <li>{@link CharacterRange#FLAG_INVOKE} Method invocation. For
     * example: Identifier Arguments.
     * <li>{@link CharacterRange#FLAG_CREATE} New object creation. For
     * example: new Creator.
     * <li>{@link CharacterRange#FLAG_BRANCH_TRUE} A condition encoded
     * in the branch instruction immediately contained in the code range for
     * this item is not inverted towards the corresponding branch condition in
     * the source code. I.e. actual jump occurs if and only if the source
     * code branch condition evaluates to true. Entries of this type are
     * produced only for conditions that are listed in the description of
     * CRT_FLOW_CONTROLLER flag. The source range for the entry contains flow
     * controlling expression. start_pc field for an entry of this type must
     * point to a branch instruction: if_acmp&lt;cond&gt;, if_icmp&lt;cond&gt;,
     * if&lt;cond&gt;, ifnonull, ifnull or goto. CRT_BRANCH_TRUE and
     * CRT_BRANCH_FALSE are special kinds of entries that can be used to
     * determine what branch of a condition was chosen during the runtime.
     * <li>{@link CharacterRange#FLAG_BRANCH_FALSE} A condition encoded
     * in the branch instruction immediately contained in the code range for
     * this item is inverted towards the corresponding branch condition in the
     * source code. I.e. actual jump occurs if and only if the source code
     * branch condition evaluates to false. Entries of this type are produced
     * only for conditions that are listed in the description of
     * CRT_FLOW_CONTROLLER flag. The source range for the entry contains flow
     * controlling expression. start_pc field for an entry of this type must
     * point to a branch instruction: if_acmp&lt;cond&gt;, if_icmp&lt;cond&gt;,
     * if&lt;cond&gt;, ifnonull, ifnull or goto.
     * </ul>
     * <p>
     * All bits of the flags item not assigned above are reserved for future use.
     * They should be set to zero in generated class files and should be ignored
     * by Java virtual machine implementations.
     *
     * @see CharacterRange#flags()
     */
    int flags();

    /**
     * {@return a character range entry}
     *
     * @apiNote
     * The created entry cannot be written to a {@link CodeBuilder}.  Use
     * {@link CodeBuilder#characterRange CodeBuilder::characterRange} instead.
     *
     * @param startPc the start of indices in the code array, inclusive
     * @param endPc the end of indices in the code array, exclusive
     * @param characterRangeStart the encoded start of character positions in
     *        the source file, inclusive
     * @param characterRangeEnd the encoded end of character positions in the
     *        source file, exclusive
     * @param flags the flags of this entry
     * @throws IllegalArgumentException if {@code startPc}, {@code endPc}, or
     *         {@code flags} is not {@link java.lang.classfile##u2 u2}
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
