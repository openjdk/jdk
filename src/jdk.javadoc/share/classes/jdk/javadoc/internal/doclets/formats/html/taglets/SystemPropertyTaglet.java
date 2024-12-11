/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html.taglets;

import java.util.EnumSet;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.SystemPropertyTree;

import jdk.javadoc.doclet.Taglet;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.HtmlTree;

/**
 * A taglet that represents the {@code @systemProperty} tag.
 */
public class SystemPropertyTaglet extends BaseTaglet {

    SystemPropertyTaglet(HtmlConfiguration config) {
        super(config, DocTree.Kind.SYSTEM_PROPERTY, true, EnumSet.allOf(Taglet.Location.class));
    }

    @Override
    public Content getInlineTagOutput(Element element, DocTree tag, TagletWriter tagletWriter) {
        this.tagletWriter = tagletWriter;
        return systemPropertyTagOutput(element, (SystemPropertyTree) tag);
    }

    /**
     * Returns the output for a {@code {@systemProperty...}} tag.
     *
     * @param element the element that owns the doc comment
     * @param tag     the system property tag
     *
     * @return the output
     */
    private Content systemPropertyTagOutput(Element element, SystemPropertyTree tag) {
        String tagText = tag.getPropertyName().toString();
        return HtmlTree.CODE(tagletWriter.createAnchorAndSearchIndex(element, tagText,
                resources.getText("doclet.System_Property"), tag));
    }
}
