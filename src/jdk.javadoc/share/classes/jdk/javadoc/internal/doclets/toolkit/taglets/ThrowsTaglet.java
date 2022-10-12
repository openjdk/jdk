/*
 * Copyright (c) 2001, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ThrowsTree;

import jdk.javadoc.doclet.Taglet.Location;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * A taglet that processes {@link ThrowsTree}, which represents
 * {@code @throws} and {@code @exception} tags.
 */
public class ThrowsTaglet extends BaseTaglet implements InheritableTaglet {

    public ThrowsTaglet() {
        super(DocTree.Kind.THROWS, false, EnumSet.of(Location.CONSTRUCTOR, Location.METHOD));
    }

    @Override
    public Output inherit(Element owner, DocTree tag, boolean isFirstSentence, BaseConfiguration configuration) {
        assert tag.getKind() == DocTree.Kind.THROWS || tag.getKind() == DocTree.Kind.EXCEPTION
                : tag.getKind();
        assert owner.getKind() == ElementKind.METHOD;
        var t = (ThrowsTree) tag;
        CommentHelper ch = configuration.utils.getCommentHelper(owner);
        Element exceptionElement = ch.getException(t);
        if (exceptionElement == null) {
            // we cannot type-match; at most FQN-match (if we were to implement it)
            errorUnknownException(configuration, ch, t);
            return new Output(null, null, List.of(), true);
        }
        TypeMirror exception = exceptionElement.asType();
        try {
            var docFinder = configuration.utils.docFinder();
            var r = docFinder.trySearch((ExecutableElement) owner, m -> extract(m, exception, configuration.utils));
            // Take care of one-to-many
            return r.map(result -> new Output(result.throwsTrees.get(0), result.method, result.throwsTrees.get(0).getDescription(), true))
                    .orElseGet(() -> new Output(null, null, List.of(), true));
        } catch (DocFinder.NoOverriddenMethodsFound e) {
            return new Output(null, null, List.of(), false);
        }
    }

    @Override
    public Content getAllBlockTagOutput(Element holder, TagletWriter writer) {
        var utils = writer.configuration().utils;
        var executable = (ExecutableElement) holder;
        ExecutableType instantiatedType = utils.asInstantiatedMethodType(
                writer.getCurrentPageElement(), executable);
        List<? extends TypeMirror> thrownTypes = instantiatedType.getThrownTypes();
        Map<TypeMirror, TypeMirror> typeSubstitutions = getSubstitutedThrownTypes(
                utils.typeUtils,
                executable.getThrownTypes(),
                thrownTypes);
        Map<ThrowsTree, ExecutableElement> tagsMap = new LinkedHashMap<>();
        utils.getThrowsTrees(executable).forEach(t -> tagsMap.put(t, executable));
        var section = new ThrowsSection(writer);
        Set<TypeMirror> alreadyDocumented = new HashSet<>();
        throwsTagsOutput(tagsMap, alreadyDocumented, typeSubstitutions, writer.configuration(), section);
        inheritThrowsDocumentation(executable, thrownTypes, alreadyDocumented, typeSubstitutions, writer.configuration(), section);
        linkToUndocumentedDeclaredExceptions(thrownTypes, alreadyDocumented, section);
        return section.getContent();
    }

    /*
     * A stateful exception section (i.e. "Throws:") being written sequentially
     * from top to bottom; extracted to ensure structure (e.g. the header is
     * written iff the section has entries).
     *
     * Call addXYZEntry methods, then finally call getContent() to get
     * the resulting section.
     */
    private static class ThrowsSection {

        private final TagletWriter writer;
        private final Content result;
        private boolean isHeaderAdded;

        ThrowsSection(TagletWriter writer) {
            this.writer = writer;
            this.result = writer.getOutputInstance();
        }

        void addEntry(ExecutableElement element, ThrowsTree throwsTag, TypeMirror substituteType) {
            ensureHeaderAdded();
            result.add(writer.throwsTagOutput(element, throwsTag, substituteType));
        }

        void addEntry(TypeMirror throwsType) {
            ensureHeaderAdded();
            result.add(writer.throwsTagOutput(throwsType));
        }

        void addInvalidEntry(String summary, String detail) {
            ensureHeaderAdded();
            result.add(writer.invalidTagOutput(summary, Optional.of(detail)));
        }

        private void ensureHeaderAdded() {
            if (!isHeaderAdded) {
                isHeaderAdded = true;
                result.add(writer.getThrowsHeader());
            }
        }

        Content getContent() {
            return result;
        }
    }

    /**
     * Returns a map of substitutions for a list of thrown types with the original type-variable
     * as a key and the instantiated type as a value. If no types need to be substituted
     * an empty map is returned.
     * @param declaredThrownTypes the originally declared thrown types.
     * @param instantiatedThrownTypes the thrown types in the context of the current type.
     * @return map of declared to instantiated thrown types or an empty map.
     */
    private Map<TypeMirror, TypeMirror> getSubstitutedThrownTypes(Types types,
                                                              List<? extends TypeMirror> declaredThrownTypes,
                                                              List<? extends TypeMirror> instantiatedThrownTypes) {
        if (!declaredThrownTypes.equals(instantiatedThrownTypes)) {
            Map<TypeMirror, TypeMirror> map = new HashMap<>();
            Iterator<? extends TypeMirror> i1 = declaredThrownTypes.iterator();
            Iterator<? extends TypeMirror> i2 = instantiatedThrownTypes.iterator();
            while (i1.hasNext() && i2.hasNext()) {
                TypeMirror t1 = i1.next();
                TypeMirror t2 = i2.next();
                if (!types.isSameType(t1, t2))
                    map.put(t1, t2);
            }
            return map;
        }
        return Map.of();
    }

    /**
     * Adds entries for the given {@code @throws} tags to the exception section.
     *
     * @param throwsTags        the tags to be converted; each tag is mapped to
     *                          a method it appears on
     * @param alreadyDocumented the set of exceptions that have already been
     *                          documented and thus must not be documented by
     *                          this method. All exceptions documented by this
     *                          method will be added to this set upon the
     *                          method's return.
     * @param configuration     doclet configuration
     * @param section           the exception section being written
     */
    private void throwsTagsOutput(Map<ThrowsTree, ExecutableElement> throwsTags,
                                  Set<TypeMirror> alreadyDocumented,
                                  Map<TypeMirror, TypeMirror> typeSubstitutions,
                                  BaseConfiguration configuration,
                                  ThrowsSection section) {
        var utils = configuration.utils;
        var documentedInThisCall = new HashSet<TypeMirror>();
        Map<ThrowsTree, ExecutableElement> flattenedExceptions = flatten(throwsTags, configuration);
        flattenedExceptions.forEach((ThrowsTree t, ExecutableElement e) -> {
            var ch = utils.getCommentHelper(e);
            Element exception = ch.getException(t);
            if (exception == null) {
                errorUnknownException(configuration, ch, t);
                return; // couldn't find and thus cannot reliably process
            }
            TypeMirror tm = exception.asType();
            TypeMirror substituteType = typeSubstitutions.get(tm);
            if ((substituteType != null && alreadyDocumented.contains(substituteType))
                || alreadyDocumented.contains(tm)) {
                return;
            }
            section.addEntry(e, t, substituteType);
            if (substituteType != null) {
                documentedInThisCall.add(substituteType);
            } else {
                documentedInThisCall.add(tm);
            }
        });
        alreadyDocumented.addAll(documentedInThisCall);
    }

    /*
     * A single @throws tag from an overriding method can correspond to multiple
     * @throws tags from an overridden method.
     */
    private Map<ThrowsTree, ExecutableElement> flatten(Map<ThrowsTree, ExecutableElement> throwsTags,
                                                       BaseConfiguration configuration) {
        Map<ThrowsTree, ExecutableElement> result = new LinkedHashMap<>();
        throwsTags.forEach((tag, taggedElement) -> {
            var expandedTags = expand(tag, taggedElement, configuration);
            assert Collections.disjoint(result.entrySet(), expandedTags.entrySet());
            result.putAll(expandedTags);
        });
        return result;
    }

    private Map<ThrowsTree, ExecutableElement> expand(ThrowsTree tag,
                                                      ExecutableElement e,
                                                      BaseConfiguration configuration) {

        // This method uses Map.of() to create maps of size zero and one.
        // While such maps are also ordered (effectively), the syntax is
        // more compact than that of LinkedHashMap.

        // peek into @throws description
        if (tag.getDescription().stream().noneMatch(d -> d.getKind() == DocTree.Kind.INHERIT_DOC)) {
            // nothing to inherit
            return Map.of(tag, e);
        }
        Utils utils = configuration.utils;
        var ch = utils.getCommentHelper(e);
        var docFinder = utils.docFinder();
        Element exceptionElement = ch.getException(tag);
        if (exceptionElement == null) {
            errorUnknownException(configuration, ch, tag);
            return Map.of(tag, e);
        }
        TypeMirror exception = exceptionElement.asType();
        var r = docFinder.search(e, false, m -> extract(m, exception, utils));
        if (r.isEmpty()) {
            return Map.of(tag, e);
        }
        var output = r.get();
        if (output.throwsTrees.size() <= 1) {
            // outer code will handle this trivial case of inheritance
            return Map.of(tag, e);
        }
        if (tag.getDescription().size() > 1) {
            // there's more to description than just {@inheritDoc}
            // it's likely a documentation error
            configuration.getMessages().error(ch.getDocTreePath(tag), "doclet.inheritDocWithinInappropriateTag");
            return Map.of();
        }
        Map<ThrowsTree, ExecutableElement> tags = new LinkedHashMap<>();
        output.throwsTrees.forEach(t -> tags.put(t, output.method));
        return tags;
    }

    /**
     * Adds entries for the exceptions listed in the {@code throws} clause to
     * the exception section.
     *
     * Inherits throws documentation for exceptions that were declared but
     * not documented.
     */
    private void inheritThrowsDocumentation(ExecutableElement holder,
                                            List<? extends TypeMirror> declaredExceptionTypes,
                                            Set<TypeMirror> alreadyDocumented,
                                            Map<TypeMirror, TypeMirror> typeSubstitutions,
                                            BaseConfiguration configuration,
                                            ThrowsSection section) {
        if (holder.getKind() != ElementKind.METHOD) {
            // (Optimization.)
            // Of all executable elements, only methods and constructors are documented.
            // Of these two, only methods inherit documentation.
            // Don't waste time on constructors.
            assert holder.getKind() == ElementKind.CONSTRUCTOR : holder.getKind();
            return;
        }
        var utils = configuration.utils;
        var docFinder = utils.docFinder();
        Map<ThrowsTree, ExecutableElement> declaredExceptionTags = new LinkedHashMap<>();
        for (TypeMirror declaredExceptionType : declaredExceptionTypes) {
            var r = docFinder.search(holder, false, m -> extract(m, declaredExceptionType, utils));
            r.ifPresent(value -> value.throwsTrees.forEach(t -> declaredExceptionTags.put(t, value.method())));
        }
        throwsTagsOutput(declaredExceptionTags, alreadyDocumented, typeSubstitutions, configuration,
                section);
    }

    private record Result(List<? extends ThrowsTree> throwsTrees, ExecutableElement method) { }

    private static Optional<Result> extract(ExecutableElement method, TypeMirror targetException, Utils utils) {
        var ch = utils.getCommentHelper(method);
        List<ThrowsTree> tags = new LinkedList<>();
        for (ThrowsTree tag : utils.getThrowsTrees(method)) {
            Element candidate = ch.getException(tag);
            if (candidate == null) {
                errorUnknownException(utils.configuration, ch, tag);
                continue;
            }
            if (utils.typeUtils.isSameType(candidate.asType(), targetException)) {
                tags.add(tag);
            }
        }
        return tags.isEmpty() ? Optional.empty() : Optional.of(new Result(tags, method));
    }

    private void linkToUndocumentedDeclaredExceptions(List<? extends TypeMirror> declaredExceptionTypes,
                                                      Set<TypeMirror> alreadyDocumented,
                                                      ThrowsSection section) {
        // TODO: assert declaredExceptionTypes are instantiated
        for (TypeMirror declaredExceptionType : declaredExceptionTypes) {
            if (alreadyDocumented.add(declaredExceptionType)) {
                section.addEntry(declaredExceptionType);
            }
        }
    }

    private static void errorUnknownException(BaseConfiguration configuration,
                                              CommentHelper ch,
                                              ThrowsTree tag) {
        configuration.getMessages().error(ch.getDocTreePath(tag),
                "doclet.throws.reference_not_found", tag.getExceptionName());
    }
}
