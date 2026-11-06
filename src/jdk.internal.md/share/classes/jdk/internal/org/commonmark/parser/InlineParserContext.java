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

package jdk.internal.org.commonmark.parser;

import jdk.internal.org.commonmark.node.LinkReferenceDefinition;
import jdk.internal.org.commonmark.parser.beta.LinkProcessor;
import jdk.internal.org.commonmark.parser.beta.InlineContentParserFactory;
import jdk.internal.org.commonmark.parser.delimiter.DelimiterProcessor;

import java.util.List;
import java.util.Set;

/**
 * Context for inline parsing.
 */
public interface InlineParserContext {

    /**
     * @return custom inline content parsers that have been configured with
     * {@link Parser.Builder#customInlineContentParserFactory(InlineContentParserFactory)}
     */
    List<InlineContentParserFactory> getCustomInlineContentParserFactories();

    /**
     * @return custom delimiter processors that have been configured with
     * {@link Parser.Builder#customDelimiterProcessor(DelimiterProcessor)}
     */
    List<DelimiterProcessor> getCustomDelimiterProcessors();

    /**
     * @return custom link processors that have been configured with {@link Parser.Builder#linkProcessor}.
     */
    List<LinkProcessor> getCustomLinkProcessors();

    /**
     * @return custom link markers that have been configured with {@link Parser.Builder#linkMarker}.
     */
    Set<Character> getCustomLinkMarkers();

    /**
     * Look up a {@link LinkReferenceDefinition} for a given label.
     * <p>
     * Note that the passed in label does not need to be normalized; implementations are responsible for doing the
     * normalization before lookup.
     *
     * @param label the link label to look up
     * @return the definition if one exists, {@code null} otherwise
     * @deprecated use {@link #getDefinition} with {@link LinkReferenceDefinition} instead
     */
    @Deprecated
    LinkReferenceDefinition getLinkReferenceDefinition(String label);

    /**
     * Look up a definition of a type for a given label.
     * <p>
     * Note that the passed in label does not need to be normalized; implementations are responsible for doing the
     * normalization before lookup.
     *
     * @return the definition if one exists, null otherwise
     */
    <D> D getDefinition(Class<D> type, String label);
}
