/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.util;

import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.taglets.*;

/**
 * Search for the requested documentation.  Inherit documentation if necessary.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @since 1.5
 */
public class DocFinder {

    /**
     * The class that encapsulates the input.
     */
    public static class Input {
        /**
         * The element to search documentation from.
         */
        public ProgramElementDoc element;
        /**
         * The taglet to search for documentation on behalf of. Null if we want
         * to search for overall documentation.
         */
        public InheritableTaglet taglet = null;

        /**
         * The id of the tag to retrieve documentation for.
         */
        public String tagId = null;

        /**
         * The tag to retrieve documentation for.  This is only used for the
         * inheritDoc tag.
         */
        public Tag tag = null;

        /**
         * True if we only want to search for the first sentence.
         */
        public boolean isFirstSentence = false;

        /**
         * True if we are looking for documentation to replace the inheritDocTag.
         */
        public boolean isInheritDocTag = false;

        /**
         * Used to distinguish between type variable param tags and regular
         * param tags.
         */
        public boolean isTypeVariableParamTag = false;

        public Input(ProgramElementDoc element, InheritableTaglet taglet, Tag tag,
                boolean isFirstSentence, boolean isInheritDocTag) {
            this(element);
            this.taglet = taglet;
            this.tag = tag;
            this.isFirstSentence = isFirstSentence;
            this.isInheritDocTag = isInheritDocTag;
        }

        public Input(ProgramElementDoc element, InheritableTaglet taglet, String tagId) {
            this(element);
            this.taglet = taglet;
            this.tagId = tagId;
        }

        public Input(ProgramElementDoc element, InheritableTaglet taglet, String tagId,
            boolean isTypeVariableParamTag) {
            this(element);
            this.taglet = taglet;
            this.tagId = tagId;
            this.isTypeVariableParamTag = isTypeVariableParamTag;
        }

        public Input(ProgramElementDoc element, InheritableTaglet taglet) {
            this(element);
            this.taglet = taglet;
        }

        public Input(ProgramElementDoc element) {
            if (element == null)
                throw new NullPointerException();
            this.element = element;
        }

        public Input(ProgramElementDoc element, boolean isFirstSentence) {
            this(element);
            this.isFirstSentence = isFirstSentence;
        }

        public Input copy() {
            Input clone = new Input(this.element);
            clone.taglet = this.taglet;
            clone.tagId = this.tagId;
            clone.tag = this.tag;
            clone.isFirstSentence = this.isFirstSentence;
            clone.isInheritDocTag = this.isInheritDocTag;
            clone.isTypeVariableParamTag = this.isTypeVariableParamTag;
            if (clone.element == null)
                throw new NullPointerException();
            return clone;

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
        public Tag holderTag;

        /**
         * The Doc object that holds the documentation.
         */
        public Doc holder;

        /**
         * The inherited documentation.
         */
        public Tag[] inlineTags = new Tag[] {};

        /**
         * False if documentation could not be inherited.
         */
        public boolean isValidInheritDocTag = true;

        /**
         * When automatically inheriting throws tags, you sometime must inherit
         * more than one tag.  For example if the element declares that it throws
         * IOException and the overridden element has throws tags for IOException and
         * ZipException, both tags would be inherited because ZipException is a
         * subclass of IOException.  This subclass of DocFinder.Output allows
         * multiple tag inheritence.
         */
        public List<Tag> tagList  = new ArrayList<>();
    }

    /**
     * Search for the requested comments in the given element.  If it does not
     * have comments, return documentation from the overriden element if possible.
     * If the overriden element does not exist or does not have documentation to
     * inherit, search for documentation to inherit from implemented methods.
     *
     * @param input the input object used to perform the search.
     *
     * @return an Output object representing the documentation that was found.
     */
    public static Output search(Input input) {
        Output output = new Output();
        if (input.isInheritDocTag) {
            //Do nothing because "element" does not have any documentation.
            //All it has it {@inheritDoc}.
        } else if (input.taglet == null) {
            //We want overall documentation.
            output.inlineTags = input.isFirstSentence ?
                input.element.firstSentenceTags() :
                input.element.inlineTags();
            output.holder = input.element;
        } else {
            input.taglet.inherit(input, output);
        }

        if (output.inlineTags != null && output.inlineTags.length > 0) {
            return output;
        }
        output.isValidInheritDocTag = false;
        Input inheritedSearchInput = input.copy();
        inheritedSearchInput.isInheritDocTag = false;
        if (input.element instanceof MethodDoc) {
            MethodDoc overriddenMethod = ((MethodDoc) input.element).overriddenMethod();
            if (overriddenMethod != null) {
                inheritedSearchInput.element = overriddenMethod;
                output = search(inheritedSearchInput);
                output.isValidInheritDocTag = true;
                if (output.inlineTags.length > 0) {
                    return output;
                }
            }
            //NOTE:  When we fix the bug where ClassDoc.interfaceTypes() does
            //       not pass all implemented interfaces, we will use the
            //       appropriate element here.
            MethodDoc[] implementedMethods =
                (new ImplementedMethods((MethodDoc) input.element, null)).build(false);
            for (MethodDoc implementedMethod : implementedMethods) {
                inheritedSearchInput.element = implementedMethod;
                output = search(inheritedSearchInput);
                output.isValidInheritDocTag = true;
                if (output.inlineTags.length > 0) {
                    return output;
                }
            }
        } else if (input.element instanceof ClassDoc) {
            ProgramElementDoc superclass = ((ClassDoc) input.element).superclass();
            if (superclass != null) {
                inheritedSearchInput.element = superclass;
                output = search(inheritedSearchInput);
                output.isValidInheritDocTag = true;
                if (output.inlineTags.length > 0) {
                    return output;
                }
            }
        }
        return output;
    }
}
