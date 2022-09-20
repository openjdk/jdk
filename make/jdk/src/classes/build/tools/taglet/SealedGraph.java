/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.ModuleElement;
import com.sun.source.doctree.DocTree;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Taglet;
import static jdk.javadoc.doclet.Taglet.Location.*;

/**
 * A block tag to optionally insert a reference to a sealed class hierarchy graph.
 */
public class SealedGraph implements Taglet {
    private static final String sealedGraphDotPath =
        System.getProperty("sealedGraphDotPath");

    private DocletEnvironment docletEnvironment;

    /** Returns the set of locations in which a taglet may be used. */
    @Override
    public Set<Location> getAllowedLocations() {
        return EnumSet.of(TYPE);
    }

    @Override
    public boolean isInlineTag() {
        return false;
    }

    @Override
    public String getName() {
        return "sealedGraph";
    }

    @Override
    public void init(DocletEnvironment env, Doclet doclet) {
        docletEnvironment = env;
    }

    @Override
    public String toString(List<? extends DocTree> tags, Element element) {
        if (sealedGraphDotPath == null || sealedGraphDotPath.isEmpty()) {
            return "";
        }
        if (docletEnvironment == null || !(element instanceof TypeElement typeElement)) {
            return "";
        }

        ModuleElement module = docletEnvironment.getElementUtils().getModuleOf(element);
        File dotFile = new File(sealedGraphDotPath,
                module.getQualifiedName() + "_" + typeElement.getQualifiedName() + ".dot");

        try (PrintWriter out = new PrintWriter(dotFile)) {
            out.println("""
                digraph G {
                labelloc="b";
                label="The Public Sealed Hierarchy of java.lang.foreign.MemoryLayout";
                rankdir="BT";
                GroupLayout -> MemoryLayout;
                StructLayout -> GroupLayout;
                UnionLayout -> GroupLayout;
                PaddingLayout -> MemoryLayout;
                SequenceLayout -> MemoryLayout;
                ValueLayout -> MemoryLayout;
                OfAddress -> ValueLayout;
                OfBoolean -> ValueLayout;
                OfByte -> ValueLayout;
                OfChar -> ValueLayout;
                OfDouble -> ValueLayout;
                OfFloat -> ValueLayout;
                OfInt -> ValueLayout;
                OfLong -> ValueLayout;
                OfShort -> ValueLayout;
                }
            """);
        } catch (FileNotFoundException e) {
            // FIXME: Please handle better than just ignoring
        }

        String simpleTypeName = element.getSimpleName().toString();
        String imageFile = simpleTypeName + "-sealed-graph.svg";
        int thumbnailHeight = -1;
        return "<dt>Sealed Class Hierarchy Graph:</dt>"
            + "<dd>"
            + "<a class=\"sealed-graph\" href=\"" + imageFile + "\">"
            + getImage(simpleTypeName, imageFile, thumbnailHeight)
            + "</a>"
            + "</dd>";
    }

    private static final String VERTICAL_ALIGN = "vertical-align:top";

    private String getImage(String moduleName, String file, int height) {
        return String.format("<img style=\"%s\" alt=\"Sealed class hierarchy graph for %s\" src=\"%s\"%s>",
                             VERTICAL_ALIGN,
                             moduleName,
                             file,
                             (height <= 0 ? "" : " height=\"" + height + "\""));
    }
}
