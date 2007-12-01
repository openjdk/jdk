/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.doclets.internal.toolkit;

import java.io.*;
import com.sun.javadoc.*;

/**
 * The interface for writing annotation type required member output.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.5
 */

public interface AnnotationTypeRequiredMemberWriter {

    /**
     * Write the header for the member documentation.
     *
     * @param classDoc the annotation type that the members belong to.
     * @param header the header to write.
     */
    public void writeHeader(ClassDoc classDoc, String header);

    /**
     * Write the member header for the given member.
     *
     * @param member the member being documented.
     * @param isFirst the flag to indicate whether or not the member is
     *                the first to be documented.
     */
    public void writeMemberHeader(MemberDoc member, boolean isFirst);

    /**
     * Write the signature for the given member.
     *
     * @param member the member being documented.
     */
    public void writeSignature(MemberDoc member);

    /**
     * Write the deprecated output for the given member.
     *
     * @param member the member being documented.
     */
    public void writeDeprecated(MemberDoc member);

    /**
     * Write the comments for the given member.
     *
     * @param member the member being documented.
     */
    public void writeComments(MemberDoc member);

    /**
     * Write the tag output for the given member.
     *
     * @param member the member being documented.
     */
    public void writeTags(MemberDoc member);

    /**
     * Write the member footer.
     */
    public void writeMemberFooter();

    /**
     * Write the footer for the member documentation.
     *
     * @param classDoc the class that the member belong to.
     */
    public void writeFooter(ClassDoc classDoc);

    /**
     * Close the writer.
     */
    public void close() throws IOException;
}
