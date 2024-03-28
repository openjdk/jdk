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

package jdk.internal.org.commonmark.renderer.html;

import jdk.internal.org.commonmark.node.Image;
import jdk.internal.org.commonmark.node.Link;
import jdk.internal.org.commonmark.node.Node;

import java.util.Map;

public interface HtmlNodeRendererContext {

    /**
     * @param url to be encoded
     * @return an encoded URL (depending on the configuration)
     */
    String encodeUrl(String url);

    /**
     * Let extensions modify the HTML tag attributes.
     *
     * @param node the node for which the attributes are applied
     * @param tagName the HTML tag name that these attributes are for (e.g. {@code h1}, {@code pre}, {@code code}).
     * @param attributes the attributes that were calculated by the renderer
     * @return the extended attributes with added/updated/removed entries
     */
    Map<String, String> extendAttributes(Node node, String tagName, Map<String, String> attributes);

    /**
     * @return the HTML writer to use
     */
    HtmlWriter getWriter();

    /**
     * @return HTML that should be rendered for a soft line break
     */
    String getSoftbreak();

    /**
     * Render the specified node and its children using the configured renderers. This should be used to render child
     * nodes; be careful not to pass the node that is being rendered, that would result in an endless loop.
     *
     * @param node the node to render
     */
    void render(Node node);

    /**
     * @return whether HTML blocks and tags should be escaped or not
     */
    boolean shouldEscapeHtml();

    /**
     * @return true if the {@link UrlSanitizer} should be used.
     * @since 0.14.0
     */
    boolean shouldSanitizeUrls();

    /**
     * @return Sanitizer to use for securing {@link Link} href and {@link Image} src if {@link #shouldSanitizeUrls()} is true.
     * @since 0.14.0
     */
    UrlSanitizer urlSanitizer();
}
