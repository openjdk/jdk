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

import jdk.internal.org.commonmark.node.Node;

import java.util.Map;

/**
 * Extension point for adding/changing attributes on HTML tags for a node.
 */
public interface AttributeProvider {

    /**
     * Set the attributes for a HTML tag of the specified node by modifying the provided map.
     * <p>
     * This allows to change or even remove default attributes. With great power comes great responsibility.
     * <p>
     * The attribute key and values will be escaped (preserving character entities), so don't escape them here,
     * otherwise they will be double-escaped.
     * <p>
     * This method may be called multiple times for the same node, if the node is rendered using multiple nested
     * tags (e.g. code blocks).
     *
     * @param node the node to set attributes for
     * @param tagName the HTML tag name that these attributes are for (e.g. {@code h1}, {@code pre}, {@code code}).
     * @param attributes the attributes, with any default attributes already set in the map
     */
    void setAttributes(Node node, String tagName, Map<String, String> attributes);

}
