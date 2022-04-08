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

package jdk.javadoc.internal.doclets.toolkit;

import javax.lang.model.element.ExecutableElement;

/**
 * The interface for writing constructor output.
 */
public interface ConstructorWriter extends MemberWriter {

    /**
     * {@return the constructor details header}
     *
     * @param content the content representing member details
     */
    Content getConstructorDetailsHeader(Content content);

    /**
     * {@return the constructor documentation header}
     *
     * @param constructor the constructor being documented
     */
    Content getConstructorHeaderContent(ExecutableElement constructor);

    /**
     * {@return the signature for the given constructor}
     *
     * @param constructor the constructor being documented
     */
    Content getSignature(ExecutableElement constructor);

    /**
     * Add the deprecated output for the given constructor.
     *
     * @param constructor the constructor being documented
     * @param constructorContent the content to which the deprecated information will be added
     */
    void addDeprecated(ExecutableElement constructor, Content constructorContent);

    /**
     * Add the preview output for the given member.
     *
     * @param member the member being documented
     * @param content the content to which the preview information will be added
     */
    void addPreview(ExecutableElement member, Content content);

    /**
     * Add the comments for the given constructor.
     *
     * @param constructor the constructor being documented
     * @param constructorContent the content to which the comments will be added
     */
    void addComments(ExecutableElement constructor, Content constructorContent);

    /**
     * Add the tags for the given constructor.
     *
     * @param constructor the constructor being documented
     * @param constructorContent the content to which the tags will be added
     */
    void addTags(ExecutableElement constructor, Content constructorContent);

    /**
     * {@return the constructor details}
     *
     * @param memberDetailsHeader the content representing member details header
     * @param memberDetails the content representing member details
     */
    Content getConstructorDetails(Content memberDetailsHeader, Content memberDetails);

    /**
     * Let the writer know whether a non public constructor was found.
     *
     * @param foundNonPubConstructor true if we found a non public constructor.
     */
    void setFoundNonPubConstructor(boolean foundNonPubConstructor);

    /**
     * @return the member header}
     */
    Content getMemberHeader();
}
