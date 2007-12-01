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
 * The interface for writing annotation type output.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API.
 *
 * @author Jamie Ho
 * @since 1.5
 */

public interface AnnotationTypeWriter {

    /**
     * Write the header of the page.
     * @param header the header to write.
     */
    public void writeHeader(String header);

    /**
     * Write the signature of the current annotation type.
     *
     * @param modifiers the modifiers for the signature.
     */
    public void writeAnnotationTypeSignature(String modifiers);

    /**
     * Build the annotation type description.
     */
    public void writeAnnotationTypeDescription();

    /**
     * Write the tag information for the current annotation type.
     */
    public void writeAnnotationTypeTagInfo();

    /**
     * If this annotation type is deprecated, write the appropriate information.
     */
    public void writeAnnotationTypeDeprecationInfo();

    /**
     * Write the footer of the page.
     */
    public void writeFooter();

    /**
     * Close the writer.
     */
    public void close() throws IOException;

    /**
     * Return the {@link AnnotationTypeDoc} being documented.
     *
     * @return the AnnotationTypeDoc being documented.
     */
    public AnnotationTypeDoc getAnnotationTypeDoc();

    /**
     * Perform any operations that are necessary when the member summary
     * finished building.
     */
    public void completeMemberSummaryBuild();
}
