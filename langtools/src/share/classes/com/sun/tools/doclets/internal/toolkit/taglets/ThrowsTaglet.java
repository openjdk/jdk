/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.taglets;

import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * A taglet that represents the @throws tag.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @since 1.4
 */
public class ThrowsTaglet extends BaseExecutableMemberTaglet
    implements InheritableTaglet {

    public ThrowsTaglet() {
        name = "throws";
    }

    /**
     * {@inheritDoc}
     */
    public void inherit(DocFinder.Input input, DocFinder.Output output) {
        ClassDoc exception;
        if (input.tagId == null) {
            ThrowsTag throwsTag = (ThrowsTag) input.tag;
            exception = throwsTag.exception();
            input.tagId = exception == null ?
                throwsTag.exceptionName() :
                throwsTag.exception().qualifiedName();
        } else {
            exception = input.method.containingClass().findClass(input.tagId);
        }

        ThrowsTag[] tags = input.method.throwsTags();
        for (int i = 0; i < tags.length; i++) {
            if (input.tagId.equals(tags[i].exceptionName()) ||
                (tags[i].exception() != null &&
                    (input.tagId.equals(tags[i].exception().qualifiedName())))) {
                output.holder = input.method;
                output.holderTag = tags[i];
                output.inlineTags = input.isFirstSentence ?
                    tags[i].firstSentenceTags() : tags[i].inlineTags();
                output.tagList.add(tags[i]);
            } else if (exception != null && tags[i].exception() != null &&
                    tags[i].exception().subclassOf(exception)) {
                output.tagList.add(tags[i]);
            }
        }
    }

    /**
     * Add links for exceptions that are declared but not documented.
     */
    private TagletOutput linkToUndocumentedDeclaredExceptions(
            Type[] declaredExceptionTypes, Set<String> alreadyDocumented,
            TagletWriter writer) {
        TagletOutput result = writer.getOutputInstance();
        //Add links to the exceptions declared but not documented.
        for (int i = 0; i < declaredExceptionTypes.length; i++) {
            if (declaredExceptionTypes[i].asClassDoc() != null &&
                ! alreadyDocumented.contains(
                        declaredExceptionTypes[i].asClassDoc().name()) &&
                ! alreadyDocumented.contains(
                    declaredExceptionTypes[i].asClassDoc().qualifiedName())) {
                if (alreadyDocumented.size() == 0) {
                    result.appendOutput(writer.getThrowsHeader());
                }
                result.appendOutput(writer.throwsTagOutput(declaredExceptionTypes[i]));
                alreadyDocumented.add(declaredExceptionTypes[i].asClassDoc().name());
            }
        }
        return result;
    }

    /**
     * Inherit throws documentation for exceptions that were declared but not
     * documented.
     */
    private TagletOutput inheritThrowsDocumentation(Doc holder,
            Type[] declaredExceptionTypes, Set<String> alreadyDocumented,
            TagletWriter writer) {
        TagletOutput result = writer.getOutputInstance();
        if (holder instanceof MethodDoc) {
            Set<Tag> declaredExceptionTags = new LinkedHashSet<Tag>();
            for (int j = 0; j < declaredExceptionTypes.length; j++) {
                DocFinder.Output inheritedDoc =
                    DocFinder.search(new DocFinder.Input((MethodDoc) holder, this,
                        declaredExceptionTypes[j].typeName()));
                if (inheritedDoc.tagList.size() == 0) {
                    inheritedDoc = DocFinder.search(new DocFinder.Input(
                        (MethodDoc) holder, this,
                        declaredExceptionTypes[j].qualifiedTypeName()));
                }
                declaredExceptionTags.addAll(inheritedDoc.tagList);
            }
            result.appendOutput(throwsTagsOutput(
                declaredExceptionTags.toArray(new ThrowsTag[] {}),
                writer, alreadyDocumented, false));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public TagletOutput getTagletOutput(Doc holder, TagletWriter writer) {
        ExecutableMemberDoc execHolder = (ExecutableMemberDoc) holder;
        ThrowsTag[] tags = execHolder.throwsTags();
        TagletOutput result = writer.getOutputInstance();
        HashSet<String> alreadyDocumented = new HashSet<String>();
        if (tags.length > 0) {
            result.appendOutput(throwsTagsOutput(
                execHolder.throwsTags(), writer, alreadyDocumented, true));
        }
        result.appendOutput(inheritThrowsDocumentation(holder,
            execHolder.thrownExceptionTypes(), alreadyDocumented, writer));
        result.appendOutput(linkToUndocumentedDeclaredExceptions(
            execHolder.thrownExceptionTypes(), alreadyDocumented, writer));
        return result;
    }


    /**
     * Given an array of <code>Tag</code>s representing this custom
     * tag, return its string representation.
     * @param throwTags the array of <code>ThrowsTag</code>s to convert.
     * @param writer the TagletWriter that will write this tag.
     * @param alreadyDocumented the set of exceptions that have already
     *        been documented.
     * @param allowDups True if we allow duplicate throws tags to be documented.
     * @return the TagletOutput representation of this <code>Tag</code>.
     */
    protected TagletOutput throwsTagsOutput(ThrowsTag[] throwTags,
        TagletWriter writer, Set<String> alreadyDocumented, boolean allowDups) {
        TagletOutput result = writer.getOutputInstance();
        if (throwTags.length > 0) {
            for (int i = 0; i < throwTags.length; ++i) {
                ThrowsTag tt = throwTags[i];
                ClassDoc cd = tt.exception();
                if ((!allowDups) && (alreadyDocumented.contains(tt.exceptionName()) ||
                    (cd != null && alreadyDocumented.contains(cd.qualifiedName())))) {
                    continue;
                }
                if (alreadyDocumented.size() == 0) {
                    result.appendOutput(writer.getThrowsHeader());
                }
                result.appendOutput(writer.throwsTagOutput(tt));
                alreadyDocumented.add(cd != null ?
                    cd.qualifiedName() : tt.exceptionName());
            }
        }
        return result;
    }
}
