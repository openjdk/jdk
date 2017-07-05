/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.bind;

/**
 * This event indicates that a problem was encountered while validating the
 * incoming XML data during an unmarshal operation, while performing
 * on-demand validation of the Java content tree, or while marshalling the
 * Java content tree back to XML data.
 *
 * @author <ul><li>Ryan Shoemaker, Sun Microsystems, Inc.</li><li>Kohsuke Kawaguchi, Sun Microsystems, Inc.</li><li>Joe Fialli, Sun Microsystems, Inc.</li></ul>
 * @see Validator
 * @see ValidationEventHandler
 * @since 1.6, JAXB 1.0
 */
public interface ValidationEvent {

    /**
     * Conditions that are not errors or fatal errors as defined by the
     * XML 1.0 recommendation
     */
    public static final int WARNING     = 0;

    /**
     * Conditions that correspond to the definition of "error" in section
     * 1.2 of the W3C XML 1.0 Recommendation
     */
    public static final int ERROR       = 1;

    /**
     * Conditions that correspond to the definition of "fatal error" in section
     * 1.2 of the W3C XML 1.0 Recommendation
     */
    public static final int FATAL_ERROR = 2;

    /**
     * Retrieve the severity code for this warning/error.
     *
     * <p>
     * Must be one of {@code ValidationEvent.WARNING},
     * {@code ValidationEvent.ERROR}, or {@code ValidationEvent.FATAL_ERROR}.
     *
     * @return the severity code for this warning/error
     */
    public int getSeverity();

    /**
     * Retrieve the text message for this warning/error.
     *
     * @return the text message for this warning/error or null if one wasn't set
     */
    public String getMessage();

    /**
     * Retrieve the linked exception for this warning/error.
     *
     * @return the linked exception for this warning/error or null if one
     *         wasn't set
     */
    public Throwable getLinkedException();

    /**
     * Retrieve the locator for this warning/error.
     *
     * @return the locator that indicates where the warning/error occurred
     */
    public ValidationEventLocator getLocator();

}
