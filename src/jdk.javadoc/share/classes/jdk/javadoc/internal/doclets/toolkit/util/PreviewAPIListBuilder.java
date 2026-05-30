/*
 * Copyright (c) 1998, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.NoteTree;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import static com.sun.source.doctree.DocTree.Kind.NOTE;

/**
 * Builds a list of all the preview packages, classes, constructors, fields and methods.
 * Preview features in the JDK are marked by the {@code jdk.internal.javac.PreviewFeature}
 * annotation interface, which provides information about the associated preview JEP.
 *
 * <p>In addition to the annotation-based mechanism for marking preview elements, this
 * builder supports two alternative ways based on JavaDoc tags to add elements to this page.
 * These are controlled by the following undocumented options:</p>
 *
 * <dl>
 *     <dt>--preview-note-tag tagName</dt>
 *
 *     <dd>This option can be used to register a custom JavaDoc note tag with the given name
 *     that marks permanent (non-preview) elements as affected by a preview feature.
 *     The elements are listed in this page in a separate table captioned "Permanent APIs
 *     affected by Preview Features". The tag uses attribute "jep" with a JEP number as value
 *     to associate the element with a preview feature. For instance, assuming "previewNote"
 *     as tag name:
 *
 *     <pre>{@code
 *        @previewNote [jep=401]
 *        This class is affected by the Value Classes preview feature.
 *     }</pre>
 *
 *     <p>The body of the note is displayed in the documentation of the element as if it was
 *     a custom note tag. All note tag attributes such as "title", "id", or "kind" are available
 *     in the tag, and the tag can be used both as a block tag and as an inline tag.</dd>
 *
 *     <dt>--preview-feature-tag tagName</dt>
 *
 *     <dd>This option registers a JavaDoc note tag with the given name that can be used to
 *     mark API elements as preview elements. It is meant to allow use of the preview feature
 *     page to non-JDK projects. For example, with "previewFeature" as tag name:
 *
 *     <pre>{@code
 *      @previewFeature [title="Preview feature name" url="<URL>" status="<status>"]
 *      Alternative preview note body.
 *     }</pre>
 *
 *     All attributes except for {@code title} are optional; if provided, they are used as
 *     additional information in the list of preview features in this page. The body of
 *     the tag is used as alternative preview note in the documentation of the element,
 *     using the default style for preview notes. Other attributes in the tag are ignored,
 *     and the tag can only be used as a block tag.
 *     </dd>
 * </dl>
 */
public class PreviewAPIListBuilder extends SummaryAPIListBuilder {

    private final Map<Element, JEP> elementJeps = new HashMap<>();
    private final SortedSet<Element> elementsWithNotes = createSummarySet();
    private final Map<String, JEP> jepsByName = new HashMap<>();
    private final Map<Integer, JEP> jepsByNumber = new HashMap<>();
    private final String previewNoteTag;
    public final String previewFeatureTag;

    /**
     * The JEP for a preview feature in this release.
     */
    public record JEP(int number, String title, String url, String status) implements Comparable<JEP> {
        @Override
        public int compareTo(JEP o) {
            return number == o.number ? title.compareTo(o.title) : number - o.number;
        }
        public String titleAndStatus() {
            return status.isBlank() ? title : title + " (" + status + ")";
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
        if (!jepsByName.isEmpty() || previewFeatureTag != null) {
            // map elements to preview JEPs and preview tags
            buildSummaryAPIInfo();
            // remove unused preview JEPs
            var usedJeps = new HashSet<>(elementJeps.values());
            jepsByName.entrySet().removeIf(e -> !usedJeps.contains(e.getValue()));
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
                    var number = getAnnotationElementValue(values, "number", 0, Integer.class);
                    var title = getAnnotationElementValue(values, "title", "", String.class);
                    var url = configuration.getDocResources().getText("doclet.Preview_JEP_URL", String.valueOf(number));
                    var status = getAnnotationElementValue(values, "status", "Preview", String.class);
                    var jep = new JEP(number, title, url, status);
                    jepsByName.put(elem.getSimpleName().toString(), jep);
                    jepsByNumber.put(number, jep);
                }
            }
        });
    }

    // Extract a single annotation element value with the given name and default value
    private <R> R getAnnotationElementValue(Map<? extends ExecutableElement, ? extends AnnotationValue> values,
                                            String name, R defaultValue, Class<R> type) {
        Optional<R> value = values.entrySet().stream()
                .filter(e -> Objects.equals(e.getKey().getSimpleName().toString(), name))
                .map(e -> type.cast(e.getValue().getValue()))
                .findFirst();
        return value.orElse(defaultValue);
    }

    @Override
    protected boolean belongsToSummary(Element element) {
        if (utils.isPreviewAPI(element)) {
            if (previewFeatureTag != null
                    && utils.hasBlockTag(element, NOTE, previewFeatureTag)) {
                var tags = utils.getBlockTags(element,
                                t -> t.getKind() == NOTE && t.getTagName().equals(previewFeatureTag), NoteTree.class);
                // Create pseudo-JEP for preview tag
                return !tags.isEmpty() && jepFromFeatureTag(element, tags.getFirst());
            } else {
                String feature = Objects.requireNonNull(utils.getPreviewFeature(element),
                        "Preview feature not specified").toString();
                // Preview features without JEP are not included in the list.
                JEP jep = jepsByName.get(feature);
                if (jep != null) {
                    elementJeps.put(element, jep);
                    return true;
                }
            }
        } else if (previewNoteTag != null) {
            // If preview tag is defined map elements to preview tags
            var tags = utils.getBlockTags(element,
                    t -> t.getKind() == NOTE && previewNoteTag.equals(t.getTagName()), NoteTree.class);
            if (tags.isEmpty()) {
                tags = getInlinePreviewNotes(element);
            }
            if (tags.size() > 1) {
                configuration.getMessages().warning("doclet.PreviewMultipleNotes", utils.getSimpleName(element));
            }
            if (!tags.isEmpty()) {
                var jep = findJEP(tags.getFirst());
                if (jep != null) {
                    elementsWithNotes.add(element);
                    elementJeps.put(element, jep);
                }
            }
        }
        return false;
    }

    /**
     * {@return a sorted set of preview feature JEPs in this release}
     */
    public Set<JEP> getJEPs() {
        return new TreeSet<>(jepsByName.values());
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
        return elementsWithNotes;
    }

    private JEP findJEP(NoteTree tag) {
        var jepNumber = getNoteTagAttribute(tag, "jep");
        if (jepNumber != null) {
            try {
                return jepsByNumber.get(Integer.parseInt(jepNumber));
            } catch (NumberFormatException nfe) {
                // warning is triggered below
            }
        }
        configuration.getMessages().warning("doclet.PreviewFeatureNotFound", jepNumber);
        return null;
    }

    private String getNoteTagAttribute(NoteTree noteTree, String name) {
        var attr = noteTree.getAttributes().stream()
                .filter(dt -> dt.getKind() == DocTree.Kind.ATTRIBUTE)
                .map(t -> (AttributeTree) t)
                .filter(at -> name.equalsIgnoreCase(at.getName().toString()) && at.getValue() != null)
                .findFirst();
        return attr.map(attributeTree -> attributeTree.getValue().toString()).orElse(null);
    }

    private boolean jepFromFeatureTag(Element element, NoteTree tag) {
        var title = getNoteTagAttribute(tag, "title");
        var url = Objects.requireNonNullElse(getNoteTagAttribute(tag, "url"), "");
        var status = Objects.requireNonNullElse(getNoteTagAttribute(tag, "status"), "");
        if (title != null) {
            var jep = jepsByName.computeIfAbsent(title.toLowerCase(Locale.ROOT).trim(),
                    _ -> new JEP(0, title, url, status));
            elementJeps.put(element, jep);
            return true;
        }
        return false;
    }

    private List<NoteTree> getInlinePreviewNotes(Element element) {
        var comment = utils.getCommentHelper(element).dcTree;
        if (comment != null) {
            return Stream.concat(comment.getFirstSentence().stream(), comment.getBody().stream())
                    .filter(t -> t.getKind() == NOTE)
                    .map(t -> (NoteTree) t)
                    .filter(t -> previewNoteTag.equals(t.getTagName())).toList();
        }
        return List.of();
    }
}
