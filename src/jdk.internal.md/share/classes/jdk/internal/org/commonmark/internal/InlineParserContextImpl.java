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

package jdk.internal.org.commonmark.internal;

import jdk.internal.org.commonmark.node.LinkReferenceDefinition;
import jdk.internal.org.commonmark.parser.InlineParserContext;
import jdk.internal.org.commonmark.parser.beta.LinkProcessor;
import jdk.internal.org.commonmark.parser.beta.InlineContentParserFactory;
import jdk.internal.org.commonmark.parser.delimiter.DelimiterProcessor;

import java.util.List;
import java.util.Set;

public class InlineParserContextImpl implements InlineParserContext {

    private final List<InlineContentParserFactory> inlineContentParserFactories;
    private final List<DelimiterProcessor> delimiterProcessors;
    private final List<LinkProcessor> linkProcessors;
    private final Set<Character> linkMarkers;
    private final Definitions definitions;

    public InlineParserContextImpl(List<InlineContentParserFactory> inlineContentParserFactories,
                                   List<DelimiterProcessor> delimiterProcessors,
                                   List<LinkProcessor> linkProcessors,
                                   Set<Character> linkMarkers,
                                   Definitions definitions) {
        this.inlineContentParserFactories = inlineContentParserFactories;
        this.delimiterProcessors = delimiterProcessors;
        this.linkProcessors = linkProcessors;
        this.linkMarkers = linkMarkers;
        this.definitions = definitions;
    }

    @Override
    public List<InlineContentParserFactory> getCustomInlineContentParserFactories() {
        return inlineContentParserFactories;
    }

    @Override
    public List<DelimiterProcessor> getCustomDelimiterProcessors() {
        return delimiterProcessors;
    }

    @Override
    public List<LinkProcessor> getCustomLinkProcessors() {
        return linkProcessors;
    }

    @Override
    public Set<Character> getCustomLinkMarkers() {
        return linkMarkers;
    }

    @Override
    @Deprecated
    public LinkReferenceDefinition getLinkReferenceDefinition(String label) {
        return definitions.getDefinition(LinkReferenceDefinition.class, label);
    }

    @Override
    public <D> D getDefinition(Class<D> type, String label) {
        return definitions.getDefinition(type, label);
    }
}
