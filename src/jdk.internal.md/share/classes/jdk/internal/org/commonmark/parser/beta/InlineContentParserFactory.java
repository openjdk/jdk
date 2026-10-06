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
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.parser.beta;

import java.util.Set;

/**
 * A factory for extending inline content parsing.
 * <p>
 * See {@link org.commonmark.parser.Parser.Builder#customInlineContentParserFactory} for how to register it.
 */
public interface InlineContentParserFactory {

    /**
     * An inline content parser needs to have a special "trigger" character which activates it. When this character is
     * encountered during inline parsing, {@link InlineContentParser#tryParse} is called with the current parser state.
     * It can also register for more than one trigger character.
     */
    Set<Character> getTriggerCharacters();

    /**
     * Create an {@link InlineContentParser} that will do the parsing. Create is called once per text snippet of inline
     * content inside block structures, and then called each time a trigger character is encountered.
     */
    InlineContentParser create();
}
