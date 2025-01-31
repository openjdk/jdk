/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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


import javax.lang.model.element.Element;
import javax.tools.Diagnostic;


import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import jdk.javadoc.doclet.StandardDoclet;
import jdk.javadoc.doclet.Taglet;

import static com.sun.source.doctree.DocTree.Kind.UNKNOWN_INLINE_TAG;

/**
 * An inline tag to insert a note formatted as preview note.
 * The tag can be used as follows:
 *
 * <pre>
 * {&commat;previewNote jep-number [Preview note heading]}
 * Preview note content
 * {&commat;previewNote}
 * </pre>
 *
 */
public class PreviewNote implements Taglet {

    static final String TAG_NAME = "previewNote";
    Reporter reporter = null;

    @Override
    public void init(DocletEnvironment env, Doclet doclet) {
        if (doclet instanceof StandardDoclet stdoclet) {
            reporter = stdoclet.getReporter();
        }
    }

    /**
     * Returns the set of locations in which the tag may be used.
     */
    @Override
    public Set<Location> getAllowedLocations() {
        return EnumSet.allOf(Taglet.Location.class);
    }

    @Override
    public boolean isInlineTag() {
        return true;
    }

    @Override
    public String getName() {
        return TAG_NAME;
    }

    @Override
    public String toString(List<? extends DocTree> tags, Element elem) {

        for (DocTree tag : tags) {
            if (tag.getKind() == UNKNOWN_INLINE_TAG) {
                UnknownInlineTagTree inlineTag = (UnknownInlineTagTree) tag;
                String[] content = inlineTag.getContent().toString().trim().split("\\s+", 2);
                if (!content[0].isBlank()) {
                    StringBuilder sb = new StringBuilder("""
                       <div class="preview-block" style="margin-top:10px; display:block; max-width:max-content;">
                       """);
                    if (content.length == 2) {
                        sb.append("""
                                <div class="preview-label">
                                """)
                          .append(content[1])
                          .append("""
                                </div>
                                """);
                    }
                    sb.append("""
                            <div class="preview-comment">
                            """);
                    return sb.toString();
                } else {
                    return """
                             </div>
                             </div>
                            """;
                }
            }
        }

        if (reporter == null) {
            throw new IllegalArgumentException("@" + TAG_NAME + " taglet content must be begin or end");
        }
        reporter.print(Diagnostic.Kind.ERROR, "@" + TAG_NAME + " taglet content must be begin or end");
        return "";
    }
}
