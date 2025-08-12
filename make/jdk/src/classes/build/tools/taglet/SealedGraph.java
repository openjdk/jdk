/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.System.lineSeparator;
import static java.nio.file.StandardOpenOption.*;
import static java.util.stream.Collectors.joining;
import static jdk.javadoc.doclet.Taglet.Location.TYPE;

/**
 * A block tag to optionally insert a reference to a sealed class hierarchy graph,
 * and generate the corresponding dot file.
 */
public final class SealedGraph implements Taglet {
    private static final String sealedDotOutputDir =
            System.getProperty("sealedDotOutputDir");

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
        if (sealedDotOutputDir == null || sealedDotOutputDir.isEmpty()) {
            return "";
        }
        if (docletEnvironment == null || !(element instanceof TypeElement typeElement)) {
            return "";
        }

        ModuleElement module = docletEnvironment.getElementUtils().getModuleOf(element);
        Path dotFile = Path.of(sealedDotOutputDir,
                module.getQualifiedName() + "_" + typeElement.getQualifiedName() + ".dot");

        Set<String> exports = module.getDirectives().stream()
                .filter(ModuleElement.ExportsDirective.class::isInstance)
                .map(ModuleElement.ExportsDirective.class::cast)
                // Only include packages that are globally exported (i.e. no "to" exports)
                .filter(ed -> ed.getTargetModules() == null)
                .map(ModuleElement.ExportsDirective::getPackage)
                .map(PackageElement::getQualifiedName)
                .map(Objects::toString)
                .collect(Collectors.toUnmodifiableSet());

        String dotContent = new Renderer().graph(typeElement, exports);

        try  {
            Files.writeString(dotFile, dotContent, WRITE, CREATE, TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String simpleTypeName = packagelessCanonicalName(typeElement).replace('.', '/');
        String imageFile = simpleTypeName + "-sealed-graph.svg";
        int thumbnailHeight = 100; // also appears in the stylesheet
        String hoverImage = "<span>"
            + getImage(simpleTypeName, imageFile, -1, true)
            + "</span>";

        return "<dt>Sealed Class Hierarchy Graph:</dt>"
                + "<dd>"
                + "<a class=\"sealed-graph\" href=\"" + imageFile + "\">"
                + getImage(simpleTypeName, imageFile, thumbnailHeight, false)
                + hoverImage
                + "</a>"
                + "</dd>";
    }

    private static final String VERTICAL_ALIGN = "vertical-align:top";
    private static final String BORDER = "border: solid lightgray 1px;";

    private String getImage(String typeName, String file, int height, boolean useBorder) {
        return String.format("<img style=\"%s\" alt=\"Sealed class hierarchy graph for %s\" src=\"%s\"%s>",
                useBorder ? BORDER + " " + VERTICAL_ALIGN : VERTICAL_ALIGN,
                typeName,
                file,
                (height <= 0 ? "" : " height=\"" + height + "\""));
    }

    private final class Renderer {

        // Generates a graph in DOT format
        String graph(TypeElement rootClass, Set<String> exports) {
            final State state = new State(rootClass);
            traverse(state, rootClass, exports);
            return state.render();
        }

        static void traverse(State state, TypeElement node, Set<String> exports) {
            state.addNode(node);
            if (!(node.getModifiers().contains(Modifier.SEALED) || node.getModifiers().contains(Modifier.FINAL))) {
                state.addNonSealedEdge(node);
            } else {
                for (TypeElement subNode : permittedSubclasses(node, exports)) {
                    if (isInPublicApi(node, exports) && isInPublicApi(subNode, exports)) {
                        state.addEdge(node, subNode);
                    }
                    traverse(state, subNode, exports);
                }
            }
        }

        private final class State {

            private static final String LABEL = "label";
            private static final String TOOLTIP = "tooltip";
            private static final String LINK = "href";

            private final TypeElement rootNode;

            private final StringBuilder builder;

            private final Map<String, Map<String, StyleItem>> nodeStyleMap;

            private int nonSealedHierarchyCount = 0;

            private sealed interface StyleItem {
                String valueString();

                record PlainString(String text) implements StyleItem {
                    @Override
                    public String valueString() {
                        return "\"" + text + "\"";
                    }
                }
                record HtmlString(String text) implements StyleItem {
                    @Override
                    public String valueString() {
                        return "<" + text + ">";
                    }
                }
            }

            public State(TypeElement rootNode) {
                this.rootNode = rootNode;
                nodeStyleMap = new LinkedHashMap<>();
                builder = new StringBuilder()
                        .append("digraph G {")
                        .append(lineSeparator())
                        .append("  nodesep=0.500000;")
                        .append(lineSeparator())
                        .append("  ranksep=0.600000;")
                        .append(lineSeparator())
                        .append("  pencolor=transparent;")
                        .append(lineSeparator())
                        .append("  node [shape=plaintext, fontcolor=\"#e76f00\", fontname=\"DejaVuSans\", fontsize=12, margin=\".2,.2\"];")
                        .append(lineSeparator())
                        .append("  edge [penwidth=2, color=\"#999999\", arrowhead=open, arrowsize=1];")
                        .append(lineSeparator())
                        .append("  rankdir=\"BT\";")
                        .append(lineSeparator());
            }

            public void addNode(TypeElement node) {
                var styles = nodeStyleMap.computeIfAbsent(id(node), n -> new LinkedHashMap<>());
                styles.put(LABEL, new StyleItem.PlainString(node.getSimpleName().toString()));
                styles.put(TOOLTIP, new StyleItem.PlainString(node.getQualifiedName().toString()));
                styles.put(LINK, new StyleItem.PlainString(relativeLink(node)));
            }

            // A permitted class must be in the same package or in the same module.
            // This implies the module is always the same.
            private String relativeLink(TypeElement node) {
                var util = SealedGraph.this.docletEnvironment.getElementUtils();
                var rootPackage = util.getPackageOf(rootNode);
                var nodePackage = util.getPackageOf(node);
                var backNavigator = rootPackage.getQualifiedName().toString().chars()
                        .filter(c -> c == '.')
                        .mapToObj(c -> "../")
                        .collect(joining()) +
                        "../";
                var forwardNavigator = nodePackage.getQualifiedName().toString()
                        .replace(".", "/");

                return backNavigator + forwardNavigator + "/" + packagelessCanonicalName(node) + ".html";
            }

            public void addEdge(TypeElement node, TypeElement subNode) {
                builder.append("  ")
                        .append(quotedId(subNode))
                        .append(" -> ")
                        .append(quotedId(node))
                        .append(";")
                        .append(lineSeparator());
            }

            public void addNonSealedEdge(TypeElement node) {
                // prepare open node
                var openNodeId = "open node #" + nonSealedHierarchyCount++;
                var styles = nodeStyleMap.computeIfAbsent(openNodeId, n -> new LinkedHashMap<>());
                styles.put(LABEL, new StyleItem.HtmlString("<I>&lt;any&gt;</I>"));
                styles.put(TOOLTIP, new StyleItem.PlainString("Non-sealed Hierarchy"));

                // add link to parent node
                builder.append("  ")
                        .append('"')
                        .append(openNodeId)
                        .append('"')
                        .append(" -> ")
                        .append(quotedId(node))
                        .append(" ")
                        .append("[style=\"dashed\"]")
                        .append(";")
                        .append(lineSeparator());
            }

            public String render() {
                nodeStyleMap.forEach((nodeName, styles) -> {
                    builder.append("  ")
                            .append('"').append(nodeName).append("\" ")
                            .append(styles.entrySet().stream()
                                    .map(e -> e.getKey() + "=" + e.getValue().valueString())
                                    .collect(joining(" ", "[", "]")))
                            .append(lineSeparator());
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
            var packageName = packageName(typeElement);
            return packageName.isPresent() && exports.contains(packageName.get()) &&
                    typeElement.getModifiers().contains(Modifier.PUBLIC);
        }

        private static Optional<String> packageName(TypeElement element) {
            return switch (element.getNestingKind()) {
                case TOP_LEVEL -> Optional.of(((PackageElement) element.getEnclosingElement()).getQualifiedName().toString());
                case ANONYMOUS, LOCAL -> Optional.empty();
                case MEMBER -> packageName((TypeElement) element.getEnclosingElement());
            };
        }
    }

    private static String packagelessCanonicalName(TypeElement element) {
        String result = element.getSimpleName().toString();
        while (element.getNestingKind() == NestingKind.MEMBER) {
            element = (TypeElement) element.getEnclosingElement();
            result = element.getSimpleName().toString() + '.' + result;
        }
        return result;
    }
}
