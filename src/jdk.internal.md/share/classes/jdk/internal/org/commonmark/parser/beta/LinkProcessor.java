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

import jdk.internal.org.commonmark.parser.InlineParserContext;

/**
 * An interface to decide how links/images are handled.
 * <p>
 * Implementations need to be registered with a parser via {@link org.commonmark.parser.Parser.Builder#linkProcessor}.
 * Then, when inline parsing is run, each parsed link/image is passed to the processor. This includes links like these:
 * <p>
 * <pre><code>
 * [text](destination)
 * [text]
 * [text][]
 * [text][label]
 * </code></pre>
 * And images:
 * <pre><code>
 * ![text](destination)
 * ![text]
 * ![text][]
 * ![text][label]
 * </code></pre>
 * See {@link LinkInfo} for accessing various parts of the parsed link/image.
 * <p>
 * The processor can then inspect the link/image and decide what to do with it by returning the appropriate
 * {@link LinkResult}. If it returns {@link LinkResult#none()}, the next registered processor is tried. If none of them
 * apply, the link is handled as it normally would.
 */
public interface LinkProcessor {

    /**
     * @param linkInfo information about the parsed link/image
     * @param scanner  the scanner at the current position after the parsed link/image
     * @param context  context for inline parsing
     * @return what to do with the link/image, e.g. do nothing (try the next processor), wrap the text in a node, or
     * replace the link/image with a node
     */
    LinkResult process(LinkInfo linkInfo, Scanner scanner, InlineParserContext context);
}
