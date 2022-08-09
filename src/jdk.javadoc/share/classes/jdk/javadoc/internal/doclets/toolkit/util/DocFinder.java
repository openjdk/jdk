/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.util.function.Function;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.taglets.InheritableTaglet;

/**
 * Search for the requested documentation.  Inherit documentation if necessary.
 */
public class DocFinder {

    public record DocTreeInfo(DocTree docTree, Element element) { }

    /**
     * The class that encapsulates the input.
     */
    public static class Input {

        /**
         * The element to search documentation from.
         */
        public Element element;

        /**
         * The taglet to search for documentation on behalf of. Null if we want
         * to search for overall documentation.
         */
        public InheritableTaglet taglet;

        /**
         * The id of the tag to retrieve documentation for.
         */
        public String tagId;

        /**
         * The tag to retrieve documentation for.  This is only used for the
         * {@code {@inheritDoc}} tag.
         */
        public final DocTreeInfo docTreeInfo;

        /**
         * True if we only want to search for the first sentence.
         */
        public boolean isFirstSentence;

        /**
         * True if we are looking for documentation to replace the {@code {@inheritDoc}} tag.
         */
        public boolean isInheritDocTag;

        /**
         * Used to distinguish between type variable param tags and regular
         * param tags.
         */
        public boolean isTypeVariableParamTag;

        public final Utils utils;

        public Input(Utils utils,
                     Element element,
                     InheritableTaglet taglet,
                     String tagId) {
            this(utils, element);
            this.taglet = taglet;
            this.tagId = tagId;
        }

        public Input(Utils utils,
                     Element element,
                     InheritableTaglet taglet,
                     String tagId,
                     boolean isTypeVariableParamTag) {
            this(utils, element);
            this.taglet = taglet;
            this.tagId = tagId;
            this.isTypeVariableParamTag = isTypeVariableParamTag;
        }

        public Input(Utils utils, Element element, InheritableTaglet taglet) {
            this(utils, element);
            this.taglet = taglet;
        }

        public Input(Utils utils, Element element) {
            this.element = Objects.requireNonNull(element);
            this.utils = utils;
            this.docTreeInfo = new DocTreeInfo(null, null);
        }

        public Input(Utils utils,
                     Element element,
                     InheritableTaglet taglet,
                     DocTreeInfo dtInfo,
                     boolean isFirstSentence,
                     boolean isInheritDocTag) {
            this.utils = utils;
            this.element = Objects.requireNonNull(element);
            this.taglet = taglet;
            this.isFirstSentence = isFirstSentence;
            this.isInheritDocTag = isInheritDocTag;
            this.docTreeInfo = dtInfo;
        }

        private Input copy() {
            var copy = new Input(utils, element, taglet, docTreeInfo,
                    isFirstSentence, isInheritDocTag);
            copy.tagId = tagId;
            copy.isTypeVariableParamTag = isTypeVariableParamTag;
            return copy;
        }

        /**
         * For debugging purposes.
         */
        @Override
        public String toString() {
            String encl = element == null ? "" : element.getEnclosingElement().toString() + "::";
            return "Input{" + "element=" + encl + element
                    + ", taglet=" + taglet
                    + ", tagId=" + tagId + ", tag=" + docTreeInfo
                    + ", isFirstSentence=" + isFirstSentence
                    + ", isInheritDocTag=" + isInheritDocTag
                    + ", isTypeVariableParamTag=" + isTypeVariableParamTag
                    + ", utils=" + utils + '}';
        }
    }

    /**
     * The class that encapsulates the output.
     */
    public static class Output {

        /**
         * The tag that holds the documentation.  Null if documentation
         * is not held by a tag.
         */
        public DocTree holderTag;

        /**
         * The element that holds the documentation.
         */
        public Element holder;

        /**
         * The inherited documentation.
         */
        public List<? extends DocTree> inlineTags = List.of();

        /**
         * False if documentation could not be inherited.
         */
        public boolean isValidInheritDocTag = true;

        /**
         * When automatically inheriting throws tags, you sometimes must inherit
         * more than one tag.  For example, if a method declares that it throws
         * IOException and the overridden method has {@code @throws} tags for IOException and
         * ZipException, both tags would be inherited because ZipException is a
         * subclass of IOException.  This allows multiple tag inheritance.
         */
        public final List<DocTree> tagList = new ArrayList<>();

        /**
         * For debugging purposes.
         */
        @Override
        public String toString() {
            String encl = holder == null ? "" : holder.getEnclosingElement().toString() + "::";
            return "Output{" + "holderTag=" + holderTag
                    + ", holder=" + encl + holder
                    + ", inlineTags=" + inlineTags
                    + ", isValidInheritDocTag=" + isValidInheritDocTag
                    + ", tagList=" + tagList + '}';
        }
    }

    /**
     * Search for the requested comments in the given element.  If it does not
     * have comments, return the inherited comments if possible.
     *
     * @param input the input object used to perform the search.
     *
     * @return an Output object representing the documentation that was found.
     */
    public static Output search(BaseConfiguration configuration, Input input) {
        Output output = new Output();
        Utils utils = configuration.utils;
        if (input.isInheritDocTag) {
            // Get documentation that {@inheritDoc} contained in the inheritable
            // tag description or main description expands to. To do that, we
            // must first ascend the inheritance hierarchy.
        } else if (input.taglet == null) {
            // either get main description (the part of the doc comment from
            // the beginning of the comment, up to the first block tag), or
            // get its first sentence
            output.inlineTags = input.isFirstSentence
                    ? utils.getFirstSentenceTrees(input.element)
                    : utils.getFullBody(input.element);
            output.holder = input.element;
        } else {
            // get contents of the inheritable tag
            input.taglet.inherit(input, output);
        }

        if (!output.inlineTags.isEmpty()) {
            return output;
        }
        // if we are unable to find methods up the inheritance hierarchy and
        // this search was initiated for {@inheritDoc}, that tag will be
        // considered erroneous
        output.isValidInheritDocTag = false;
        // a less cleaner alternative to copy() would be to restore values
        // mutated by recursive call in try-finally
        Input inheritedSearchInput = input.copy();
        inheritedSearchInput.isInheritDocTag = false;
        if (utils.isMethod(input.element)) {
            ExecutableElement m = (ExecutableElement) input.element;
            ExecutableElement overriddenMethod = utils.overriddenMethod(m);
            if (overriddenMethod != null) {
                inheritedSearchInput.element = overriddenMethod;
                output = search(configuration, inheritedSearchInput);
                output.isValidInheritDocTag = true;
                if (!output.inlineTags.isEmpty()) {
                    return output;
                }
            }
            TypeElement encl = utils.getEnclosingTypeElement(input.element);
            VisibleMemberTable vmt = configuration.getVisibleMemberTable(encl);
            List<ExecutableElement> implementedMethods = vmt.getImplementedMethods(m);
            for (ExecutableElement implementedMethod : implementedMethods) {
                inheritedSearchInput.element = implementedMethod;
                output = search(configuration, inheritedSearchInput);
                output.isValidInheritDocTag = true;
                if (!output.inlineTags.isEmpty()) {
                    return output;
                }
            }
        }
        return output;
    }

    static class InvalidInheritDocException extends Exception {
        @java.io.Serial
        static final long serialVersionUID = 1L;
    }

    public static Optional<List<? extends DocTree>> expandInheritDoc(
            ExecutableElement method,
            Function<? super ExecutableElement, Optional<List<? extends DocTree>>> documentationExtractor,
            BaseConfiguration configuration) throws InvalidInheritDocException
    {
        var overriddenMethods = methodsOverriddenBy(method, configuration);
        if (!overriddenMethods.hasNext()) {
            throw new InvalidInheritDocException();
        }
        do {
            var m = overriddenMethods.next();
            var d = documentationExtractor.apply(m);
            if (d.isPresent()) {
                return d;
            }
        } while (overriddenMethods.hasNext());
        return Optional.empty();
    }

    public static <T> Optional<T> search(
            ExecutableElement method,
            Function<? super ExecutableElement, Optional<T>> criteria,
            BaseConfiguration configuration)
    {
        var d = criteria.apply(method);
        if (d.isPresent()) {
            return d;
        }
        var overriddenMethods = methodsOverriddenBy(method, configuration);
        while (overriddenMethods.hasNext()) {
            var m = overriddenMethods.next();
            d = criteria.apply(m);
            if (d.isPresent()) {
                return d;
            }
        }
        return Optional.empty();
    }

    static Iterator<ExecutableElement> methodsOverriddenBy(ExecutableElement method,
                                                           BaseConfiguration configuration) {
        return new HierarchyTraversingIterator(method, configuration);
    }

    private static class HierarchyTraversingIterator implements Iterator<ExecutableElement> {

        final BaseConfiguration configuration;
        final Deque<LazilyAccessedImplementedMethods> path = new LinkedList<>();
        ExecutableElement next;

        /*
         * Constructs an iterator over methods overridden by the given method.
         *
         * The iteration order is as defined in the Documentation Comment
         * Specification for the Standard Doclet. The iteration sequence
         * does not include the given method itself.
         */
        public HierarchyTraversingIterator(ExecutableElement method, BaseConfiguration configuration) {
            assert method.getKind() == ElementKind.METHOD : method.getKind();
            this.configuration = configuration;
            next = method;
            updateNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public ExecutableElement next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            var r = next;
            updateNext();
            return r;
        }

        private void updateNext() {
            assert next != null;
            var superClassMethod = configuration.utils.overriddenMethod(next);
            path.push(new LazilyAccessedImplementedMethods(next));
            if (superClassMethod != null) {
                next = superClassMethod;
                return;
            }
            while (!path.isEmpty()) {
                var superInterfaceMethods = path.peek();
                if (superInterfaceMethods.hasNext()) {
                    next = superInterfaceMethods.next();
                    return;
                } else {
                    path.pop();
                }
            }
            next = null; // end-of-hierarchy
        }

        class LazilyAccessedImplementedMethods implements Iterator<ExecutableElement> {

            final ExecutableElement method;
            Iterator<ExecutableElement> iterator;

            public LazilyAccessedImplementedMethods(ExecutableElement method) {
                this.method = method;
            }

            @Override
            public boolean hasNext() {
                return getIterator().hasNext();
            }

            @Override
            public ExecutableElement next() {
                return getIterator().next();
            }

            Iterator<ExecutableElement> getIterator() {
                if (iterator != null) {
                    return iterator;
                }
                var type = configuration.utils.getEnclosingTypeElement(next);
                var table = configuration.getVisibleMemberTable(type);
                return iterator = table.getImplementedMethods(method).iterator();
            }
        }
    }
}
