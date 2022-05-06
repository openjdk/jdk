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
import javax.lang.model.type.TypeMirror;

/**
 * The interface for writing method output.
 */
public interface MethodWriter extends MemberWriter {

    /**
     * Get the method details header.
     *
     * @param content the content representing member details
     * @return the method details header
     */
    Content getMethodDetailsHeader(Content content);

    /**
     * Get the method documentation header.
     *
     * @param method the method being documented
     * @return the method documentation header
     */
    Content getMethodHeader(ExecutableElement method);

    /**
     * Get the signature for the given method.
     *
     * @param method the method being documented
     * @return the method signature
     */
    Content getSignature(ExecutableElement method);

    /**
     * Add the deprecated output for the given method.
     *
     * @param method the method being documented
     * @param methodContent the content to which the deprecated information will be added
     */
    void addDeprecated(ExecutableElement method, Content methodContent);

    /**
     * Adds the preview output for the given member.
     *
     * @param member the member being documented
     * @param content the content to which the preview information will be added
     */
    void addPreview(ExecutableElement member, Content content);

    /**
     * Add the comments for the given method.
     *
     * @param holder the holder type (not erasure) of the method
     * @param method the method being documented
     * @param methodContent the content to which the comments will be added
     */
    void addComments(TypeMirror holder, ExecutableElement method, Content methodContent);

    /**
     * Add the tags for the given method.
     *
     * @param method the method being documented
     * @param methodContent the content to which the tags will be added
     */
    void addTags(ExecutableElement method, Content methodContent);

    /**
     * Get the method details.
     *
     * @param methodDetailsHeader the content representing method details header
     * @param methodDetails the content representing method details
     * @return the method details
     */
    Content getMethodDetails(Content methodDetailsHeader, Content methodDetails);

    /**
     * Gets the member header.
     *
     * @return the member header
     */
    Content getMemberHeader();
}
