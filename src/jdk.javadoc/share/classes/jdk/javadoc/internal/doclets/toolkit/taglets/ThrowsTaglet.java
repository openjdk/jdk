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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ThrowsTree;

import jdk.javadoc.doclet.Taglet.Location;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder.Result;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * A taglet that processes {@link ThrowsTree}, which represents {@code @throws}
 * and {@code @exception} tags, collectively referred to as exception tags.
 */
public class ThrowsTaglet extends BaseTaglet implements InheritableTaglet {

    /*
     * Relevant bits from JLS
     * ======================
     *
     * 11.1.1 The Kinds of Exceptions
     *
     *   Throwable and all its subclasses are, collectively, the exception
     *   classes.
     *
     * 8.4.6 Method Throws
     *
     *   Throws:
     *     throws ExceptionTypeList
     *
     *   ExceptionTypeList:
     *     ExceptionType {, ExceptionType}
     *
     *   ExceptionType:
     *     ClassType
     *     TypeVariable
     *
     *   It is a compile-time error if an ExceptionType mentioned in a throws
     *   clause is not a subtype (4.10) of Throwable.
     *
     *   Type variables are allowed in a throws clause even though they are
     *   not allowed in a catch clause (14.20).
     *
     *   It is permitted but not required to mention unchecked exception
     *   classes (11.1.1) in a throws clause.
     *
     * 8.1.2 Generic Classes and Type Parameters
     *
     *   It is a compile-time error if a generic class is a direct or indirect
     *   subclass of Throwable.
     *
     * 8.8.5. Constructor Throws
     *
     *   The throws clause for a constructor is identical in structure and
     *   behavior to the throws clause for a method (8.4.6).
     *
     * 8.8. Constructor Declarations
     *
     *   Constructor declarations are ... never inherited and therefore are not
     *   subject to hiding or overriding.
     *
     * General Comments
     * ================
     *
     * Map<ThrowsTree, ExecutableElement> associates a doc tree with its holder
     * element externally. Such maps are ordered, have non-null keys and values.
     */

    public ThrowsTaglet() {
        // of all language elements only constructors and methods can declare
        // thrown exceptions and, hence, document them
        super(DocTree.Kind.THROWS, false, EnumSet.of(Location.CONSTRUCTOR, Location.METHOD));
    }

    @Override
    public Output inherit(Element owner, DocTree tag, boolean isFirstSentence, BaseConfiguration configuration) {
        // This method shouldn't be called because {@inheritDoc} tags inside
        // exception tags aren't dealt with individually. {@inheritDoc} tags
        // inside exception tags are collectively dealt with in
        // getAllBlockTagOutput.
        throw newAssertionError(owner, tag, isFirstSentence);
    }

    @Override
    public Content getAllBlockTagOutput(Element holder, TagletWriter writer) {
        ElementKind kind = holder.getKind();
        if (kind != ElementKind.METHOD && kind != ElementKind.CONSTRUCTOR) {
            // Elements are processed by applicable taglets only. This taglet
            // is only applicable to executable elements such as methods
            // and constructors.
            throw newAssertionError(holder, kind);
        }
        var executable = (ExecutableElement) holder;
        var utils = writer.configuration().utils;
        ExecutableType instantiatedType = utils.asInstantiatedMethodType(
                writer.getCurrentPageElement(), executable);
        List<? extends TypeMirror> substitutedExceptionTypes = instantiatedType.getThrownTypes();
        List<? extends TypeMirror> originalExceptionTypes = executable.getThrownTypes();
        Map<TypeMirror, TypeMirror> typeSubstitutions = getSubstitutedThrownTypes(
                utils.typeUtils,
                originalExceptionTypes,
                substitutedExceptionTypes);
        var exceptionSection = new ExceptionSectionBuilder(writer);
        try {
            // Step 1. Document exception tags
            Set<TypeMirror> alreadyDocumentedExceptions = new HashSet<>();
            List<ThrowsTree> exceptionTags = utils.getThrowsTrees(executable);
            for (ThrowsTree t : exceptionTags) {
                outputAnExceptionTagDeeply(exceptionSection, t, executable, alreadyDocumentedExceptions, typeSubstitutions, writer, utils);
            }
            // Step 2. Document exception types from the `throws` clause
            if (executable.getKind() == ElementKind.METHOD) { // methods are inherited, but constructors are not (JLS 8.8)
                var docFinder = utils.docFinder();
                for (TypeMirror exceptionType : substitutedExceptionTypes) {
                    var r = docFinder.search(executable,
                            false, // false: do not look for documentation in `executable`;
                            // if documentation were there, we would find it on step 1
                            m -> extract(m, exceptionType, utils)).toOptional();
                    if (r.isEmpty()) {
                        // if the result is empty, `exceptionType` will be
                        // documented on Step 3; skip it for now
                        continue;
                    }
                    if (!alreadyDocumentedExceptions.add(exceptionType)) {
                        continue;
                    }
                    for (ThrowsTree t : r.get().throwsTrees()) {
                        outputAnExceptionTagDeeply(exceptionSection, t, r.get().method(), alreadyDocumentedExceptions, typeSubstitutions, writer, utils);
                    }
                }
            }
            // Step 3. List those exceptions from the `throws` clause for which
            //         no documentation was found on Step 2.
            for (TypeMirror e : substitutedExceptionTypes) {
                if (!alreadyDocumentedExceptions.add(e)) {
                    continue;
                }
                exceptionSection.beginEntry(e);
                exceptionSection.endEntry();
            }
            assert alreadyDocumentedExceptions.containsAll(substitutedExceptionTypes);
        } catch (Failure.ExceptionTypeNotFound f) {
            var ch = utils.getCommentHelper(f.holder());
            utils.configuration.getMessages().warning(ch.getDocTreePath(f.tag().getExceptionName()),
                    "doclet.throws.reference_not_found");
            // add bad entry to section
        } catch (Failure.NotExceptionType f) {
            var ch = utils.getCommentHelper(f.holder());
            var name = diagnosticDescriptionOf(f.type());
            utils.configuration.getMessages().warning(ch.getDocTreePath(f.tag().getExceptionName()),
                    "doclet.throws.reference_bad_type", name);
            // add bad entry to section
        } catch (Failure.InvalidMarkup f) {

        } catch (Failure.Unsupported f) {
            var ch = utils.getCommentHelper(f.holder());
            utils.configuration.getMessages().warning(ch.getDocTreePath(f.tag().getExceptionName()),
                    "doclet.throwsInheritDocUnsupported");
        }
        return exceptionSection.build();
    }

    private static Map<ThrowsTree, ExecutableElement> toExceptionTags(ExecutableElement e, List<ThrowsTree> tags) {
        return tags.stream()
                .collect(Collectors.toMap(Function.identity(), t -> e, (e1, e2) -> {
                    // there should be no equal exception tags, hence no merging is expected
                    throw newAssertionError(e1, e2);
                }, LinkedHashMap::new));
    }

    // - we need to hijack tag processing just for the "@throws + @inheritDoc"
    //   combination (because one-to-many we need access to exception section
    //   being written rather than individual exception description)
    // - populates exception section, depth-first
    // - content elements are composable, tags are not (because of the DocTreePath,
    //   one cannot practically use a list of tags cherry-picked from different doc comments)

    // This adds entries late; who starts an entry also ends it

    private void outputAnExceptionTagDeeply(ExceptionSectionBuilder exceptionSection,
                                            ThrowsTree tag,
                                            ExecutableElement holder,
                                            Set<TypeMirror> alreadyDocumentedExceptions,
                                            Map<TypeMirror, TypeMirror> typeSubstitutions,
                                            TagletWriter writer,
                                            Utils utils)
            throws Failure.ExceptionTypeNotFound,
                   Failure.NotExceptionType,
                   Failure.InvalidMarkup,
                   Failure.Unsupported
    {
        var exceptionType = getExceptionType(tag, holder, utils);
        outputAnExceptionTagDeeply(exceptionSection, exceptionType, tag, holder, true, alreadyDocumentedExceptions, typeSubstitutions, writer, utils);
    }

    private void outputAnExceptionTagDeeply(ExceptionSectionBuilder exceptionSection,
                                            Element originalExceptionType,
                                            ThrowsTree tag,
                                            ExecutableElement holder,
                                            boolean addNewEntry,
                                            Set<TypeMirror> alreadyDocumentedExceptions,
                                            Map<TypeMirror, TypeMirror> typeSubstitutions,
                                            TagletWriter writer,
                                            Utils utils)
            throws Failure.InvalidMarkup,
                   Failure.ExceptionTypeNotFound,
                   Failure.NotExceptionType,
                   Failure.Unsupported
    {
        var ch = utils.getCommentHelper(holder);
        var t = originalExceptionType.asType();
        var exceptionType = typeSubstitutions.getOrDefault(t, t);
        alreadyDocumentedExceptions.add(exceptionType);
        var description = tag.getDescription();
        int i = indexOfInheritDoc(tag, holder);

        if (i == -1) {
            // appending to an existing entry or adding a new one?
            if (addNewEntry) {
                exceptionSection.beginEntry(exceptionType);
            }
            exceptionSection.continueEntry(writer.commentTagsToOutput(holder, description));
            if (addNewEntry) {
                exceptionSection.endEntry();
            }
        } else {
            boolean loneInheritDoc = description.size() == 1;
            assert !loneInheritDoc || i == 0 : i;
            boolean add = !loneInheritDoc && addNewEntry;
            if (add) {
                exceptionSection.beginEntry(exceptionType);
            }
            if (i > 0) { // if there's anything preceding {@inheritDoc}, assume the entry has been started before
                Content beforeInheritDoc = writer.commentTagsToOutput(holder, description.subList(0, i));
                exceptionSection.continueEntry(beforeInheritDoc);
            }
            Optional<Map<ThrowsTree, ExecutableElement>> expansion = expandShallowly(tag, holder, utils);
            if (expansion.isEmpty()) {
                if (!add) {
                    exceptionSection.beginEntry(exceptionType);
                }
                Resources resources = utils.configuration.getDocResources();
                String text = resources.getText("doclet.tag.invalid_input", tag.toString());
                exceptionSection.continueEntry(writer.invalidTagOutput(text, Optional.empty()));
                // it might be helpful to output the type we found for the user to diagnose the issue
                String n = diagnosticDescriptionOf(utils.typeUtils.asElement(exceptionType));
                utils.configuration.getMessages().warning(ch.getDocTreePath(tag), "doclet.inheritDocNoDoc", n);
                if (!add) {
                    exceptionSection.endEntry();
                }
                return;
            }
            // if {@inheritDoc} is the only tag in the @throws description and
            // this call can add new entries to the exception section,
            // so can the recursive call
            boolean addNewEntryRecursively = addNewEntry && !add;
            var tags = expansion.get();
            if (!addNewEntryRecursively && tags.size() > 1) {
                // current tag has more to description than just {@inheritDoc}
                // and thus cannot expand to multiple tags;
                // it's likely a documentation error
                utils.configuration.getMessages().error(utils.getCommentHelper(holder).getDocTreePath(tag), "doclet.inheritDocWithinInappropriateTag");
                return;
            }
            for (Map.Entry<ThrowsTree, ExecutableElement> e : tags.entrySet()) {
                outputAnExceptionTagDeeply(exceptionSection, originalExceptionType, e.getKey(), e.getValue(), addNewEntryRecursively, alreadyDocumentedExceptions, typeSubstitutions, writer, utils);
            }
            // this might be an empty list, which is fine
            if (!loneInheritDoc) {
                Content afterInheritDoc = writer.commentTagsToOutput(holder, description.subList(i + 1, description.size()));
                exceptionSection.continueEntry(afterInheritDoc);
            }
            if (add) {
                exceptionSection.endEntry();
            }
        }
    }

    private static int indexOfInheritDoc(ThrowsTree tag, ExecutableElement holder)
            throws Failure.InvalidMarkup
    {
        var tags = tag.getDescription();
        int i = -1;
        for (var iterator = tags.listIterator(); iterator.hasNext(); ) {
            DocTree t = iterator.next();
            if (t.getKind() == DocTree.Kind.INHERIT_DOC) {
                if (i != -1) {
                    throw new Failure.InvalidMarkup(t, holder);
                }
                i = iterator.previousIndex();
            }
        }
        return i;
    }

    private static Element getExceptionType(ThrowsTree tag,
                                            ExecutableElement holder,
                                            Utils utils)
            throws Failure.ExceptionTypeNotFound,
                   Failure.NotExceptionType
    {
        Element e = utils.getCommentHelper(holder).getException(tag);
        if (e == null) {
            throw new Failure.ExceptionTypeNotFound(tag, holder);
        }
        var t = e.asType();
        var subtypeTestInapplicable = t.getKind() == TypeKind.EXECUTABLE
                || t.getKind() == TypeKind.PACKAGE
                || t.getKind() == TypeKind.MODULE;
        if (subtypeTestInapplicable || !utils.typeUtils.isSubtype(t, utils.getThrowableType())) {
            // aside from trivial documentation errors, this condition
            // might arise when we found something which is not what
            // the documentation author intended
            throw new Failure.NotExceptionType(tag, holder, e);
        }
        var k = e.getKind();
        assert k == ElementKind.CLASS || k == ElementKind.TYPE_PARAMETER : k; // JLS 8.4.6
        return e;
    }

    private static sealed class Failure extends Exception {

        @java.io.Serial private static final long serialVersionUID = 1L;

        private final transient DocTree tag;
        private final transient ExecutableElement holder;

        Failure(DocTree tag, ExecutableElement holder) {
            super();
            this.tag = tag;
            this.holder = holder;
        }

        DocTree tag() { return tag; }

        ExecutableElement holder() { return holder; }

        static final class ExceptionTypeNotFound extends Failure {

            @java.io.Serial private static final long serialVersionUID = 1L;

            ExceptionTypeNotFound(ThrowsTree tag, ExecutableElement holder) {
                super(tag, holder);
            }

            @Override ThrowsTree tag() { return (ThrowsTree) super.tag(); }
        }

        static final class NotExceptionType extends Failure {

            @java.io.Serial private static final long serialVersionUID = 1L;

            private final transient Element type;

            public NotExceptionType(ThrowsTree tag, ExecutableElement holder, Element type) {
                super(tag, holder);
                this.type = type;
            }

            Element type() { return type; }

            @Override ThrowsTree tag() { return (ThrowsTree) super.tag(); }
        }

        static final class InvalidMarkup extends Failure {

            @java.io.Serial private static final long serialVersionUID = 1L;

            public InvalidMarkup(DocTree tag, ExecutableElement holder) {
                super(tag, holder);
            }
        }

        static final class Unsupported extends Failure {

            @java.io.Serial private static final long serialVersionUID = 1L;

            Unsupported(ThrowsTree tag, ExecutableElement holder) {
                super(tag, holder);
            }

            @Override ThrowsTree tag() { return (ThrowsTree) super.tag(); }
        }
    }

    /*
     * Returns tags immediately inherited by the provided tag.
     *
     * This method provides shallow expansion. If required, deep expansion may
     * be obtained by calling this method recursively for tags returned from
     * this method.
     */
    private static Optional<Map<ThrowsTree, ExecutableElement>> expandShallowly(ThrowsTree tag,
                                                                                ExecutableElement holder,
                                                                                Utils utils)
            throws Failure.ExceptionTypeNotFound,
                   Failure.NotExceptionType,
                   Failure.Unsupported {
        Element target = getExceptionType(tag, holder, utils);
        ElementKind kind = target.getKind();

        DocFinder.Criterion<Map<ThrowsTree, ExecutableElement>, RuntimeException> c;
        if (kind.isClass()) {
            c = method -> {
                var tags = findByExceptionType(target, method, utils);
                return Result.fromOptional(!tags.isEmpty() ? Optional.of(toExceptionTags(method, tags)) : Optional.empty());
            };
        } else {
            // the basis of parameter position matching is JLS sections 8.4.2 and 8.4.4;
            int i = holder.getTypeParameters().indexOf(target);
            if (i == -1) {
                throw new Failure.Unsupported(tag, holder);
            }
            c = method -> {
                assert utils.elementUtils.overrides(holder, method, (TypeElement) holder.getEnclosingElement());
                // those lists must have the same number of elements
                var typeParameterElement = method.getTypeParameters().get(i);
                var tags = findByExceptionType(typeParameterElement, method, utils);
                return Result.fromOptional(!tags.isEmpty() ? Optional.of(toExceptionTags(method, tags)) : Optional.empty());
            };
        }
        var result = utils.docFinder().search(holder, false, c).toOptional();
        assert !(result.isPresent() && result.get().isEmpty());
        return result;
    }

    private static List<ThrowsTree> findByExceptionType(Element exceptionType,
                                                        ExecutableElement executable,
                                                        Utils utils) {
        var result = new LinkedList<ThrowsTree>();
        for (ThrowsTree t : utils.getThrowsTrees(executable)) {
            CommentHelper ch = utils.getCommentHelper(executable);
            Element candidate = ch.getException(t);
            if (exceptionType.equals(candidate)) { // candidate may be null
                result.add(t);
            }
        }
        return result;
    }

    /*
     * An exception section (that is, the "Throws:" section in the Method
     * or Constructor Details section) builder.
     *
     * The section is being built sequentially from top to bottom.
     *
     * Adapts one off methods of writer to continuous building.
     */
    private static class ExceptionSectionBuilder {

        private final TagletWriter writer;
        private final Content result;
        private ContentBuilder current;
        private boolean began;
        private boolean headerAdded;
        private TypeMirror exceptionType;

        ExceptionSectionBuilder(TagletWriter writer) {
            this.writer = writer;
            this.result = writer.getOutputInstance();
        }

        void beginEntry(TypeMirror exceptionType) {
            if (began) {
                throw new IllegalStateException();
            }
            began = true;
            current = new ContentBuilder();
            this.exceptionType = exceptionType;
        }

        void continueEntry(Content c) {
            if (!began) {
                throw new IllegalStateException();
            }
            current.add(c);
        }

        public void endEntry() {
            if (!began) {
                throw new IllegalStateException();
            }
            began = false;
            if (!headerAdded) {
                headerAdded = true;
                result.add(writer.getThrowsHeader());
            }
            result.add(writer.throwsTagOutput(exceptionType, current.isEmpty() ? Optional.empty() : Optional.of(current)));
            current = null;
        }

        Content build() {
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
        Map<TypeMirror, TypeMirror> map = new HashMap<>();
        var i1 = declaredThrownTypes.iterator();
        var i2 = instantiatedThrownTypes.iterator();
        while (i1.hasNext() && i2.hasNext()) {
            TypeMirror t1 = i1.next();
            TypeMirror t2 = i2.next();
            if (!types.isSameType(t1, t2)) {
                map.put(t1, t2);
            }
        }
        // correspondence between types is established positionally, i.e.
        // pairwise, which means that the lists must have the same
        // number of elements; if they don't, this algorithm is
        // broken
        assert !i1.hasNext() && !i2.hasNext();
        return map;
    }

    private record Documentation(List<? extends ThrowsTree> throwsTrees, ExecutableElement method) { }

    private static Result<Documentation> extract(ExecutableElement method, TypeMirror targetExceptionType, Utils utils) {
        // FIXME: find substitutions?
        var ch = utils.getCommentHelper(method);
        List<ThrowsTree> tags = new LinkedList<>();
        for (ThrowsTree tag : utils.getThrowsTrees(method)) {
            Element candidate = ch.getException(tag);
            if (candidate == null) {
                errorUnknownException(utils.configuration, ch, tag);
                continue;
            }
            if (utils.typeUtils.isSameType(candidate.asType(), targetExceptionType)) {
                tags.add(tag);
            }
        }
        if (tags.isEmpty()) {
            // check if the exception is reachable through the throws clause
            // TODO: need complete search: type variables and probably substitutions
            Optional<? extends TypeMirror> any = method.getThrownTypes().stream().filter(targetExceptionType::equals).findAny();
            if (any.isPresent()) {
                return Result.CONTINUE();
            } else {
                return Result.SKIP();
            }
        }
        return Result.TERMINATE(new Documentation(tags, method));
    }

    private static void errorUnknownException(BaseConfiguration configuration,
                                              CommentHelper ch,
                                              ThrowsTree tag) {
        configuration.getMessages().error(ch.getDocTreePath(tag),
                "doclet.throws.reference_not_found", tag.getExceptionName());
    }

    private static AssertionError newAssertionError(Object... objects) {
        return new AssertionError(Arrays.toString(objects));
    }

    private static String diagnosticDescriptionOf(Element e) {
        var name = e instanceof QualifiedNameable q ? q.getQualifiedName() : e.getSimpleName();
        return name + " (" + detailedDescriptionOf(e) + ")";
    }

    private static String detailedDescriptionOf(Element e) {
        // It might be important to describe the element in detail. Sometimes
        // elements share the same simple and/or qualified name. Outputting
        // individual components of that name as well as their kinds helps
        // the user disambiguate such elements.
        // TODO: is it feasible to use javax.lang.model.util.Elements.getBinaryName(TypeElement)
        //  to augment/replace detailed information for _type elements_?
        //  (binary names don't work for _type variables_)
        var thisElementDescription = e.getKind() + " " + switch (e.getKind()) {
            // A package is never enclosed in a package and a module is
            // never never enclosed in a module, no matter what their
            // qualified name might suggest. Get their qualified
            // name directly. Also, unnamed packages and
            // modules require special treatment.
            case PACKAGE -> {
                var p = (PackageElement) e;
                // TODO: i18n
                yield p.isUnnamed() ? "<unnamed package>" : p.getQualifiedName();
            }
            case MODULE -> {
                var m = (ModuleElement) e;
                // TODO: i18n + is there any value is displaying unnamed module?
                yield m.isUnnamed() ? "<unnamed module>" : m.getQualifiedName();
            }
            default -> e.getSimpleName();
        };
        if (e.getEnclosingElement() == null) {
            return thisElementDescription;
        }
        var enclosingElementDescription = detailedDescriptionOf(e.getEnclosingElement());
        return enclosingElementDescription + " " + thisElementDescription;
    }
}
