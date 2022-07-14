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
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ThrowsTree;

import jdk.javadoc.doclet.Taglet.Location;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;

/**
 * A taglet that processes {@link ThrowsTree}, which represents
 * {@code @throws} and {@code @exception} tags.
 */
public class ThrowsTaglet extends BaseTaglet implements InheritableTaglet {

    public ThrowsTaglet() {
        super(DocTree.Kind.THROWS, false, EnumSet.of(Location.CONSTRUCTOR, Location.METHOD));
    }

    @Override
    public void inherit(DocFinder.Input input, DocFinder.Output output) {
        var utils = input.utils;
        Element target;
        var ch = utils.getCommentHelper(input.element);
        if (input.tagId == null) {
            var tag = (ThrowsTree) input.docTreeInfo.docTree();
            target = ch.getException(tag);
            input.tagId = target == null
                    ? tag.getExceptionName().getSignature()
                    : utils.getFullyQualifiedName(target);
        } else {
            target = input.utils.findClass(input.element, input.tagId);
        }

        // TODO warn if target == null as we cannot guarantee type-match, but at most FQN-match.

        for (ThrowsTree tag : input.utils.getThrowsTrees(input.element)) {
            Element candidate = ch.getException(tag);
            if (candidate != null && (input.tagId.equals(utils.getSimpleName(candidate)) ||
                    (input.tagId.equals(utils.getFullyQualifiedName(candidate))))) {
                output.holder = input.element;
                output.holderTag = tag;
                output.inlineTags = ch.getBody(output.holderTag);
                output.tagList.add(tag);
            } else if (target != null && candidate != null &&
                    utils.isTypeElement(candidate) && utils.isTypeElement(target) &&
                    utils.isSubclassOf((TypeElement) candidate, (TypeElement) target)) {
                output.tagList.add(tag);
            }
        }
    }

    @Override
    public Content getAllBlockTagOutput(Element holder, TagletWriter writer) {
        var utils = writer.configuration().utils;
        var executable = (ExecutableElement) holder;
        ExecutableType instantiatedType = utils.asInstantiatedMethodType(
                writer.getCurrentPageElement(), executable);
        List<? extends TypeMirror> thrownTypes = instantiatedType.getThrownTypes();
        Map<String, TypeMirror> typeSubstitutions = getSubstitutedThrownTypes(
                utils.typeUtils,
                executable.getThrownTypes(),
                thrownTypes);
        Map<ThrowsTree, ExecutableElement> tagsMap = new LinkedHashMap<>();
        utils.getThrowsTrees(executable).forEach(t -> tagsMap.put(t, executable));
        Content result = writer.getOutputInstance();
        Set<String> alreadyDocumented = new HashSet<>();
        result.add(throwsTagsOutput(tagsMap, alreadyDocumented, typeSubstitutions, writer));
        result.add(inheritThrowsDocumentation(executable, thrownTypes, alreadyDocumented, typeSubstitutions, writer));
        result.add(linkToUndocumentedDeclaredExceptions(thrownTypes, alreadyDocumented, writer));
        return result;
    }

    /**
     * Returns a map of substitutions for a list of thrown types with the original type-variable
     * name as a key and the instantiated type as a value. If no types need to be substituted
     * an empty map is returned.
     * @param declaredThrownTypes the originally declared thrown types.
     * @param instantiatedThrownTypes the thrown types in the context of the current type.
     * @return map of declared to instantiated thrown types or an empty map.
     */
    private Map<String, TypeMirror> getSubstitutedThrownTypes(Types types,
                                                              List<? extends TypeMirror> declaredThrownTypes,
                                                              List<? extends TypeMirror> instantiatedThrownTypes) {
        if (!declaredThrownTypes.equals(instantiatedThrownTypes)) {
            Map<String, TypeMirror> map = new HashMap<>();
            Iterator<? extends TypeMirror> i1 = declaredThrownTypes.iterator();
            Iterator<? extends TypeMirror> i2 = instantiatedThrownTypes.iterator();
            while (i1.hasNext() && i2.hasNext()) {
                TypeMirror t1 = i1.next();
                TypeMirror t2 = i2.next();
                if (!types.isSameType(t1, t2))
                    map.put(t1.toString(), t2);
            }
            return map;
        }
        return Map.of();
    }

    /**
     * Returns the generated content for a collection of {@code @throws} tags.
     *
     * @param throwsTags        the tags to be converted; each tag is mapped to
     *                          a method it appears on
     * @param alreadyDocumented the set of exceptions that have already been
     *                          documented and thus must not be documented by
     *                          this method. All exceptions documented by this
     *                          method will be added to this set upon the
     *                          method's return.
     * @param writer            the taglet-writer used by the doclet
     * @return the generated content for the tags
     */
    private Content throwsTagsOutput(Map<ThrowsTree, ExecutableElement> throwsTags,
                                     Set<String> alreadyDocumented,
                                     Map<String, TypeMirror> typeSubstitutions,
                                     TagletWriter writer) {
        var utils = writer.configuration().utils;
        Content result = writer.getOutputInstance();
        var documentedInThisCall = new HashSet<String>();
        Map<ThrowsTree, ExecutableElement> flattenedExceptions = flatten(throwsTags, writer);
        flattenedExceptions.forEach((ThrowsTree t, ExecutableElement e) -> {
            var ch = utils.getCommentHelper(e);
            Element te = ch.getException(t);
            String excName = t.getExceptionName().toString();
            TypeMirror substituteType = typeSubstitutions.get(excName);
            if (alreadyDocumented.contains(excName)
                    || (te != null && alreadyDocumented.contains(utils.getFullyQualifiedName(te, false)))
                    || (substituteType != null && alreadyDocumented.contains(substituteType.toString()))) {
                return;
            }
            if (alreadyDocumented.isEmpty() && documentedInThisCall.isEmpty()) {
                result.add(writer.getThrowsHeader());
            }
            result.add(writer.throwsTagOutput(e, t, substituteType));
            if (substituteType != null) {
                documentedInThisCall.add(substituteType.toString());
            } else {
                documentedInThisCall.add(te != null
                        ? utils.getFullyQualifiedName(te, false)
                        : excName);
            }
        });
        alreadyDocumented.addAll(documentedInThisCall);
        return result;
    }

    /*
     * A single @throws tag from an overriding method can correspond to multiple
     * @throws tags from an overridden method.
     */
    private Map<ThrowsTree, ExecutableElement> flatten(Map<ThrowsTree, ExecutableElement> throwsTags,
                                                       TagletWriter writer) {
        Map<ThrowsTree, ExecutableElement> result = new LinkedHashMap<>();
        throwsTags.forEach((tag, taggedElement) -> {
            var expandedTags = expand(tag, taggedElement, writer);
            assert Collections.disjoint(result.entrySet(), expandedTags.entrySet());
            result.putAll(expandedTags);
        });
        return result;
    }

    private Map<ThrowsTree, ExecutableElement> expand(ThrowsTree tag,
                                                      ExecutableElement e,
                                                      TagletWriter writer) {

        // This method uses Map.of() to create maps of size zero and one.
        // While such maps are effectively ordered, the syntax is more
        // compact than that of LinkedHashMap.

        // peek into @throws description
        if (tag.getDescription().stream().noneMatch(d -> d.getKind() == DocTree.Kind.INHERIT_DOC)) {
            // nothing to inherit
            return Map.of(tag, e);
        }
        var input = new DocFinder.Input(writer.configuration().utils, e, this, new DocFinder.DocTreeInfo(tag, e), false, true);
        var output = DocFinder.search(writer.configuration(), input);
        if (output.tagList.size() <= 1) {
            // outer code will handle this trivial case of inheritance
            return Map.of(tag, e);
        }
        if (tag.getDescription().size() > 1) {
            // there's more to description than just {@inheritDoc}
            // it's likely a documentation error
            var ch = writer.configuration().utils.getCommentHelper(e);
            writer.configuration().getMessages().error(ch.getDocTreePath(tag), "doclet.inheritDocWithinInappropriateTag");
            return Map.of();
        }
        Map<ThrowsTree, ExecutableElement> tags = new LinkedHashMap<>();
        output.tagList.forEach(t -> tags.put((ThrowsTree) t, (ExecutableElement) output.holder));
        return tags;
    }

    /**
     * Inherit throws documentation for exceptions that were declared but not
     * documented.
     */
    private Content inheritThrowsDocumentation(ExecutableElement holder,
                                               List<? extends TypeMirror> declaredExceptionTypes,
                                               Set<String> alreadyDocumented,
                                               Map<String, TypeMirror> typeSubstitutions,
                                               TagletWriter writer) {
        Content result = writer.getOutputInstance();
        if (holder.getKind() != ElementKind.METHOD) {
            // (Optimization.)
            // Of all executable elements, only methods and constructors are documented.
            // Of these two, only methods inherit documentation.
            // Don't waste time on constructors.
            assert holder.getKind() == ElementKind.CONSTRUCTOR : holder.getKind();
            return result;
        }
        var utils = writer.configuration().utils;
        Map<ThrowsTree, ExecutableElement> declaredExceptionTags = new LinkedHashMap<>();
        for (TypeMirror declaredExceptionType : declaredExceptionTypes) {
            var input = new DocFinder.Input(utils, holder, this,
                    utils.getTypeName(declaredExceptionType, false));
            DocFinder.Output inheritedDoc = DocFinder.search(writer.configuration(), input);
            if (inheritedDoc.tagList.isEmpty()) {
                input = new DocFinder.Input(utils, holder, this,
                        utils.getTypeName(declaredExceptionType, true));
                inheritedDoc = DocFinder.search(writer.configuration(), input);
            }
            if (!inheritedDoc.tagList.isEmpty()) {
                if (inheritedDoc.holder == null) {
                    inheritedDoc.holder = holder;
                }
                var h = (ExecutableElement) inheritedDoc.holder;
                inheritedDoc.tagList.forEach(t -> declaredExceptionTags.put((ThrowsTree) t, h));
            }
        }
        result.add(throwsTagsOutput(declaredExceptionTags, alreadyDocumented, typeSubstitutions,
                writer));
        return result;
    }

    private Content linkToUndocumentedDeclaredExceptions(List<? extends TypeMirror> declaredExceptionTypes,
                                                         Set<String> alreadyDocumented,
                                                         TagletWriter writer) {
        // TODO: assert declaredExceptionTypes are instantiated
        var utils = writer.configuration().utils;
        Content result = writer.getOutputInstance();
        for (TypeMirror declaredExceptionType : declaredExceptionTypes) {
            TypeElement te = utils.asTypeElement(declaredExceptionType);
            if (te != null &&
                    !alreadyDocumented.contains(declaredExceptionType.toString()) &&
                    !alreadyDocumented.contains(utils.getFullyQualifiedName(te, false))) {
                if (alreadyDocumented.isEmpty()) {
                    result.add(writer.getThrowsHeader());
                }
                result.add(writer.throwsTagOutput(declaredExceptionType));
                alreadyDocumented.add(utils.getSimpleName(te));
            }
        }
        return result;
    }
}
