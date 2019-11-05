/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.lang.model.element.Element;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import jdk.javadoc.doclet.Taglet;
import static jdk.javadoc.doclet.Taglet.Location.*;

/**
 * An block tag to insert a standard warning about a preview API.
 */
public class Preview implements Taglet {

    /** Returns the set of locations in which a taglet may be used. */
    @Override
    public Set<Location> getAllowedLocations() {
        return EnumSet.of(MODULE, PACKAGE, TYPE, CONSTRUCTOR, METHOD, FIELD);
    }

    @Override
    public boolean isInlineTag() {
        return true;
    }

    @Override
    public String getName() {
        return "preview";
    }

    @Override
    public String toString(List<? extends DocTree> tags, Element elem) {
        UnknownInlineTagTree previewTag = (UnknownInlineTagTree) tags.get(0);
        List<? extends DocTree> previewContent = previewTag.getContent();
        String previewText = ((TextTree) previewContent.get(0)).getBody();
        String[] summaryAndDetails = previewText.split("\n\r?\n\r?");
        String summary = summaryAndDetails[0];
        String details = summaryAndDetails.length > 1 ? summaryAndDetails[1] : summaryAndDetails[0];
        StackTraceElement[] stackTrace = new Exception().getStackTrace();
        Predicate<StackTraceElement> isSummary =
                el -> el.getClassName().endsWith("HtmlDocletWriter") &&
                      el.getMethodName().equals("addSummaryComment");
        if (Arrays.stream(stackTrace).anyMatch(isSummary)) {
            return "<div style=\"display:inline-block; font-weight:bold\">" + summary + "</div><br>";
        }
        return "<div style=\"border: 1px solid red; border-radius: 5px; padding: 5px; display:inline-block; font-size: larger\">" + details + "</div><br>";
    }
}

