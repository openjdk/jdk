/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.util;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Build list of all the preview packages, classes, constructors, fields and methods.
 */
public class PreviewAPIListBuilder extends SummaryAPIListBuilder {

    private final Map<Element, JEP> elementJeps = new HashMap<>();
    private final SortedSet<Element> elementNotes = createSummarySet();
    private final Map<String, JEP> jeps = new HashMap<>();
    private final String previewNoteTag;
    public final String previewFeatureTag;

    /**
     * The JEP for a preview feature in this release.
     */
    public record JEP(int number, String title, String status) implements Comparable<JEP> {
        @Override
        public int compareTo(JEP o) {
            return number == o.number ? title.compareTo(o.title) : number - o.number;
        }
    }

    /**
     * Constructor.
     *
     * @param configuration the current configuration of the doclet
     */
    public PreviewAPIListBuilder(BaseConfiguration configuration) {
        super(configuration);
        this.previewNoteTag = configuration.getOptions().previewNoteTag();
        this.previewFeatureTag = configuration.getOptions().previewFeatureTag();
        // retrieve preview JEPs
        buildPreviewFeatureInfo();
        if (!jeps.isEmpty() || previewFeatureTag != null) {
            // map elements to preview JEPs and preview tags
            buildSummaryAPIInfo();
            // remove unused preview JEPs
            jeps.entrySet().removeIf(e -> !elementJeps.containsValue(e.getValue()));
        }
    }

    private void buildPreviewFeatureInfo() {
        TypeElement featureType = utils.elementUtils.getTypeElement("jdk.internal.javac.PreviewFeature.Feature");
        if (featureType == null) {
            return;
        }
        TypeElement jepType = utils.elementUtils.getTypeElement("jdk.internal.javac.PreviewFeature.JEP");
        featureType.getEnclosedElements().forEach(elem -> {
            for (AnnotationMirror anno : elem.getAnnotationMirrors()) {
                if (anno.getAnnotationType().asElement().equals(jepType)) {
                    Map<? extends ExecutableElement, ? extends AnnotationValue> values = anno.getElementValues();
                    jeps.put(elem.getSimpleName().toString(), new JEP(
                            getAnnotationElementValue(values, "number", 0),
                            getAnnotationElementValue(values, "title", ""),
                            getAnnotationElementValue(values, "status", "Preview"))
                    );
                }
            }
        });
    }

    // Extract a single annotation element value with the given name and default value
    @SuppressWarnings("unchecked")
    private <R> R getAnnotationElementValue(Map<? extends ExecutableElement, ? extends AnnotationValue> values,
                                            String name, R defaultValue) {
        Optional<R> value = values.entrySet().stream()
                .filter(e -> Objects.equals(e.getKey().getSimpleName().toString(), name))
                .map(e -> (R) e.getValue().getValue())
                .findFirst();
        return value.orElse(defaultValue);
    }

    @Override
    protected boolean belongsToSummary(Element element) {
        if (utils.isPreviewAPI(element)) {
            if (previewFeatureTag != null
                    && utils.hasBlockTag(element, DocTree.Kind.UNKNOWN_BLOCK_TAG, previewFeatureTag)) {
                var desc = utils.getBlockTags(element, t -> t.getTagName().equals(previewFeatureTag),
                            UnknownBlockTagTree.class)
                    .stream()
                    .map(t -> t.getContent().toString().trim())
                    .findFirst();
                // Create pseudo-JEP for preview tag
                desc.ifPresent(s -> {
                    var jep = jeps.computeIfAbsent(s, s2 -> new JEP(0, s2, ""));
                    elementJeps.put(element, jep);
                });
                return true;
            } else {
                String feature = Objects.requireNonNull(utils.getPreviewFeature(element),
                        "Preview feature not specified").toString();
                // Preview features without JEP are not included in the list.
                JEP jep = jeps.get(feature);
                if (jep != null) {
                    elementJeps.put(element, jep);
                    return true;
                }
            }
        } else if (previewNoteTag != null) {
            // If preview tag is defined map elements to preview tags
            CommentHelper ch = utils.getCommentHelper(element);
            if (ch.dcTree != null) {
                var jep = ch.dcTree.getFullBody().stream()
                        .filter(dt -> dt.getKind() == DocTree.Kind.UNKNOWN_INLINE_TAG)
                        .map(dt -> (UnknownInlineTagTree) dt)
                        .filter(t -> previewNoteTag.equals(t.getTagName()) && !t.getContent().isEmpty())
                        .map(this::findJEP)
                        .filter(Objects::nonNull)
                        .findFirst();
                if (jep.isPresent()) {
                    elementNotes.add(element);
                    elementJeps.put(element, jep.get());
                    return false; // Not part of preview API.
                }
            }
        }
        return false;
    }

    /**
     * {@return a sorted set of preview feature JEPs in this release}
     */
    public Set<JEP> getJEPs() {
        return new TreeSet<>(jeps.values());
    }

    /**
     * {@return the JEP for a preview element}
     */
    public JEP getJEP(Element e) {
        return elementJeps.get(e);
    }

    /**
     * {@return a sorted set containing elements tagged with preview notes}
     */
    public SortedSet<Element> getElementNotes() {
        return elementNotes;
    }

    private JEP findJEP(UnknownInlineTagTree tag) {
        var content = tag.getContent().toString().trim().split("\\s+", 2);
        try {
            var jnum = Integer.parseInt(content[0]);
            for (var jep : jeps.values()) {
                if (jep.number == jnum) {
                    return jep;
                }
            }
        } catch (NumberFormatException nfe) {
            // print warning?
        }
        return null;
    }
}
