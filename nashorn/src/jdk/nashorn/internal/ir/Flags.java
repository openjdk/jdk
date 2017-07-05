/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.ir;

/**
 * Interface implemented by all nodes that have flags in
 * a lexical context. This is needed as we sometimes have to save
 * the setting of flags in the lexical context until a block
 * is completely finished and its final version (after multiple
 * copy on writes) is placed in the lexical context
 *
 * @param <T> lexical context node that can have flags set during code generation
 */
public interface Flags<T extends LexicalContextNode> {

    /**
     * Get all flags of a LexicalContextNode
     * @return flags
     */
    public int getFlags();

    /**
     * Check if a flag is set in a lexical context node
     * @param flag flag to check
     * @return flags
     */
    public boolean getFlag(int flag);

    /**
     * Clear a flag of a LexicalContextNode
     * @param lc lexical context
     * @param flag flag to clear
     * @return the new LexicalContext node if flags were changed, same otherwise
     */
    public T clearFlag(final LexicalContext lc, int flag);

    /**
     * Set a flag of a LexicalContextNode
     * @param lc lexical context
     * @param flag flag to set
     * @return the new LexicalContext node if flags were changed, same otherwise
     */
    public T setFlag(final LexicalContext lc, int flag);

    /**
     * Set all flags of a LexicalContextNode, overwriting previous flags
     * @param lc lexical context
     * @param flags new flags value
     * @return the new LexicalContext node if flags were changed, same otherwise
     */
    public T setFlags(final LexicalContext lc, int flags);
}
