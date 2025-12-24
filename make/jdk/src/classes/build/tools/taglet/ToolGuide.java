/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.taglet;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.lang.reflect.Field;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import jdk.javadoc.doclet.Taglet;

import static com.sun.source.doctree.DocTree.Kind.*;
import static jdk.javadoc.doclet.Taglet.Location.*;

/**
 * A block tag to insert a link to tool guide in a nearby directory.
 * The tag can be used as follows:
 * <ul>
 * <li>&commat;toolGuide tool-name label
 * </ul>
 *
 * If the label is omitted, it defaults to the tool name.
 *
 * For example
 * <p>
 * &commat;toolGuide javac
 * <p>
 * will produce the following html, depending on the file containing
 * the tag.
 * <p>
 * {@code
 * <dt>Tool Guides:
 * <dd><a href="../../specs/man/javac.html">javac</a>
 * }
 */
public class ToolGuide implements Taglet {

    static final String TAG_NAME = "toolGuide";

    static final String BASE_URL = "specs/man";

    static final Pattern TAG_PATTERN = Pattern.compile("(?s)(?<name>[A-Za-z0-9]+)\\s*(?<label>.*)$");

    /**
     * Returns the set of locations in which the tag may be used.
     */
    @Override
    public Set<Location> getAllowedLocations() {
        return EnumSet.of(MODULE, PACKAGE, TYPE);
    }

    @Override
    public boolean isInlineTag() {
        return false;
    }

    @Override
    public String getName() {
        return TAG_NAME;
    }

    @Override
    public String toString(List<? extends DocTree> tags, Element elem) {

        if (tags.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<dt>Tool Guides:</dt>\n")
                .append("<dd>");

        boolean needComma = false;
        for (DocTree tag : tags) {

            if (tag.getKind() != UNKNOWN_BLOCK_TAG) {
                continue;
            }

            UnknownBlockTagTree blockTag = (UnknownBlockTagTree) tag;
            String tagText = blockTag.getContent().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining())
                    .trim();
            Matcher m = TAG_PATTERN.matcher(tagText);
            if (m.matches()) {
                String name = m.group("name");
                String label = m.group("label");
                if (label.isEmpty()) {
                    label = name;
                }
                String rootParent = currentPath().replaceAll("[^/]+", "..");

                String url = String.format("%s/%s/%s.html",
                        rootParent, BASE_URL, name);

                if (needComma) {
                    sb.append(",\n");
                } else {
                    needComma = true;
                }

                sb.append("<a href=\"")
                        .append(url)
                        .append("\">")
                        .append(label)
                        .append("</a>");
            }
        }

        sb.append("</dd>\n");

        return sb.toString();
    }

    private static ThreadLocal<String> CURRENT_PATH = null;

    private String currentPath() {
        if (CURRENT_PATH == null) {
            try {
                Field f = Class.forName("jdk.javadoc.internal.doclets.formats.html.HtmlDocletWriter")
                               .getField("CURRENT_PATH");
                @SuppressWarnings("unchecked")
                ThreadLocal<String> tl = (ThreadLocal<String>) f.get(null);
                CURRENT_PATH = tl;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Cannot determine current path", e);
            }
        }
        return CURRENT_PATH.get();
    }

}
