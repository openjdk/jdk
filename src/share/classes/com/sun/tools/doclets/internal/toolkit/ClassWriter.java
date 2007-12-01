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
 * The interface for writing class output.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.5
 */

public interface ClassWriter {

    /**
     * Write the header of the page.
     * @param header the header to write.
     */
    public void writeHeader(String header);

    /**
     * Write the class tree documentation.
     */
    public void writeClassTree();

    /**
     * Write all implemented interfaces if this is a class.
     */
    public void writeImplementedInterfacesInfo();

    /**
     * Write all super interfaces if this is an interface.
     */
    public void writeSuperInterfacesInfo();

    /**
     * Write the type parameter information.
     */
    public void writeTypeParamInfo();

    /**
     * Write all the classes that extend this one.
     */
    public void writeSubClassInfo();

    /**
     * Write all the interfaces that extend this one.
     */
    public void writeSubInterfacesInfo();

    /**
     * If this is an interface, write all classes that implement this
     * interface.
     */
    public void writeInterfaceUsageInfo ();

    /**
     * If this is an inner class or interface, write the enclosing class or
     * interface.
     */
    public void writeNestedClassInfo ();

    /**
     * If this class is deprecated, write the appropriate information.
     */
    public void writeClassDeprecationInfo ();

    /**
     * Write the signature of the current class.
     *
     * @param modifiers the modifiers for the signature.
     */
    public void writeClassSignature(String modifiers);

    /**
     * Build the class description.
     */
    public void writeClassDescription();

    /**
     * Write the tag information for the current class.
     */
    public void writeClassTagInfo();

    /**
     * Write the footer of the page.
     */
    public void writeFooter();

    /**
     * Close the writer.
     */
    public void close() throws IOException;

    /**
     * Return the classDoc being documented.
     *
     * @return the classDoc being documented.
     */
    public ClassDoc getClassDoc();

    /**
     * Perform any operations that are necessary when the member summary
     * finished building.
     */
    public void completeMemberSummaryBuild();
}
