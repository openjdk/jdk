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

import com.sun.source.doctree.DocTree;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Taglet;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.System.lineSeparator;
import static jdk.javadoc.doclet.Taglet.Location.TYPE;

/**
 * A block tag to optionally insert a reference to a sealed class hierarchy graph.
 */
public final class SealedGraph implements Taglet {
    private static final String sealedGraphDotPath =
            System.getProperty("sealedGraphDotPath");

    private DocletEnvironment docletEnvironment;

    /**
     * Returns the set of locations in which a taglet may be used.
     */
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

        Set<String> exports = module.getDirectives().stream()
                .filter(ModuleElement.ExportsDirective.class::isInstance)
                .map(ModuleElement.ExportsDirective.class::cast)
                // Only include packages that are globally exported (i.e. no "to" exports)
                .filter(me -> me.getTargetModules() == null)
                .map(ModuleElement.ExportsDirective::getPackage)
                .map(PackageElement::getQualifiedName)
                .map(Objects::toString)
                .collect(Collectors.toUnmodifiableSet());

        String dotContent = Renderer.graph(typeElement, exports);

        try (PrintWriter pw = new PrintWriter(dotFile)) {
            pw.println(dotContent);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        String simpleTypeName = element.getSimpleName().toString();
        String imageFile = simpleTypeName + "-sealed-graph.svg";
        int thumbnailHeight = 100;
        var hoverImage = "<span>"
                + getImage(simpleTypeName, imageFile, -1, true)
                + "</span>";

        return "<dt>Sealed Class Hierarchy Graph:</dt>"
                + "<dd>"
                + "<a class=\"sealed-graph\" href=\"" + imageFile + "\">"
                + getImage(simpleTypeName, imageFile, thumbnailHeight, false)
                // + hoverImage
                + "</a>"
                + "</dd>";
    }

    private static final String VERTICAL_ALIGN = "vertical-align:top";
    private static final String BORDER = "border: solid lightgray 1px;";

    private String getImage(String moduleName, String file, int height, boolean useBorder) {
        return String.format("<img style=\"%s\" alt=\"Sealed class hierarchy graph for %s\" src=\"%s\"%s>",
                useBorder ? BORDER + " " + VERTICAL_ALIGN : VERTICAL_ALIGN,
                moduleName,
                file,
                (height <= 0 ? "" : " height=\"" + height + "\""));
    }

    private static final class Renderer {

        private Renderer() {
        }

        // Generates a graph in DOT format
        static String graph(TypeElement rootClass, Set<String> exports) {
            final State state = new State(rootClass);
            traverse(state, rootClass, exports);
            return state.render();
        }

        static void traverse(State state, TypeElement node, Set<String> exports) {
            state.addNode(node);
            for (TypeElement subNode : permittedSubclasses(node, exports)) {
                if (isInPublicApi(node, exports) && isInPublicApi(subNode, exports)) {
                    state.addEdge(node, subNode);
                }
                traverse(state, subNode, exports);
            }
        }

        private static final class State {

            private static final String LABEL = "label";
            private static final String TOOLTIP = "tooltip";
            private static final String STYLE = "style";

            private final StringBuilder builder;

            private final Map<String, Map<String, String>> nodeStyleMap;

            public State(TypeElement rootNode) {
                nodeStyleMap = new LinkedHashMap<>();
                builder = new StringBuilder()
                        .append("digraph G {")
                        .append(lineSeparator())
/*                        .append("  labelloc=\"b\";")
                        .append(lineSeparator())
                        .append("  label=\"The Public Sealed Hierarchy of ")
                        .append(rootNode.getName())
                        .append("\";")
                        .append(lineSeparator())*/
                        .append("  rankdir=\"BT\";")
                        .append(lineSeparator());
            }

            public void addNode(TypeElement node) {
                var styles = nodeStyleMap.computeIfAbsent(id(node), n -> new LinkedHashMap<>());
                styles.put(LABEL, node.getSimpleName().toString());
                styles.put(TOOLTIP, node.getQualifiedName().toString());
                if (!(node.getModifiers().contains(Modifier.SEALED) || node.getModifiers().contains(Modifier.FINAL))) {
                    // This indicates that the hierarchy is not closed
                    styles.put(STYLE, "dashed");
                }
            }

            public void addEdge(TypeElement node, TypeElement subNode) {
                builder.append("  ")
                        .append(quotedId(subNode))
                        .append(" -> ")
                        .append(quotedId(node))
                        .append(";")
                        .append(lineSeparator());
            }

            public String render() {
                nodeStyleMap.forEach((nodeName, styles) -> {
                    builder.append("  ")
                            .append('"').append(nodeName).append("\" ")
                            .append(styles.entrySet().stream()
                                    .map(e -> e.getKey() + "=\"" + e.getValue() + "\"")
                                    .collect(Collectors.joining(" ", "[", "]")))
                            .append(System.lineSeparator());
                });
                builder.append("}");
                return builder.toString();
            }

            private String id(TypeElement node) {
                return node.getQualifiedName().toString();
            }

            private String quotedId(TypeElement node) {
                return "\"" + id(node) + "\"";
            }

            private String simpleName(String name) {
                int lastDot = name.lastIndexOf('.');
                return lastDot < 0
                        ? name
                        : name.substring(lastDot);
            }

        }

        private static List<TypeElement> permittedSubclasses(TypeElement node, Set<String> exports) {
            return node.getPermittedSubclasses().stream()
                    .filter(DeclaredType.class::isInstance)
                    .map(DeclaredType.class::cast)
                    .map(DeclaredType::asElement)
                    .filter(TypeElement.class::isInstance)
                    .map(TypeElement.class::cast)
                    .filter(te -> isInPublicApi(te, exports))
                    .toList();
        }


        private static boolean isInPublicApi(TypeElement typeElement, Set<String> exports) {
           return (exports.contains(packageName(typeElement.getQualifiedName().toString())) ||
                   exports.contains(packageName(typeElement.getSuperclass().toString()))) &&
                   typeElement.getModifiers().contains(Modifier.PUBLIC);
        }

        private static String packageName(String name) {
            int lastDot = name.lastIndexOf('.');
            return lastDot < 0
                    ? ""
                    : name.substring(0, lastDot);
        }
    }

}

