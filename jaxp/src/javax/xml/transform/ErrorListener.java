/*
 * Copyright (c) 2000, 2005, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.transform;

/**
 * <p>To provide customized error handling, implement this interface and
 * use the <code>setErrorListener</code> method to register an instance of the
 * implmentation with the {@link javax.xml.transform.Transformer}.  The
 * <code>Transformer</code> then reports all errors and warnings through this
 * interface.</p>
 *
 * <p>If an application does <em>not</em> register its own custom
 * <code>ErrorListener</code>, the default <code>ErrorListener</code>
 * is used which reports all warnings and errors to <code>System.err</code>
 * and does not throw any <code>Exception</code>s.
 * Applications are <em>strongly</em> encouraged to register and use
 * <code>ErrorListener</code>s that insure proper behavior for warnings and
 * errors.</p>
 *
 * <p>For transformation errors, a <code>Transformer</code> must use this
 * interface instead of throwing an <code>Exception</code>: it is up to the
 * application to decide whether to throw an <code>Exception</code> for
 * different types of errors and warnings.  Note however that the
 * <code>Transformer</code> is not required to continue with the transformation
 * after a call to {@link #fatalError(TransformerException exception)}.</p>
 *
 * <p><code>Transformer</code>s may use this mechanism to report XML parsing
 * errors as well as transformation errors.</p>
 */
public interface ErrorListener {

    /**
     * Receive notification of a warning.
     *
     * <p>{@link javax.xml.transform.Transformer} can use this method to report
     * conditions that are not errors or fatal errors.  The default behaviour
     * is to take no action.</p>
     *
     * <p>After invoking this method, the Transformer must continue with
     * the transformation. It should still be possible for the
     * application to process the document through to the end.</p>
     *
     * @param exception The warning information encapsulated in a
     *                  transformer exception.
     *
     * @throws javax.xml.transform.TransformerException if the application
     * chooses to discontinue the transformation.
     *
     * @see javax.xml.transform.TransformerException
     */
    public abstract void warning(TransformerException exception)
        throws TransformerException;

    /**
     * Receive notification of a recoverable error.
     *
     * <p>The transformer must continue to try and provide normal transformation
     * after invoking this method.  It should still be possible for the
     * application to process the document through to the end if no other errors
     * are encountered.</p>
     *
     * @param exception The error information encapsulated in a
     *                  transformer exception.
     *
     * @throws javax.xml.transform.TransformerException if the application
     * chooses to discontinue the transformation.
     *
     * @see javax.xml.transform.TransformerException
     */
    public abstract void error(TransformerException exception)
        throws TransformerException;

    /**
     * <p>Receive notification of a non-recoverable error.</p>
     *
     * <p>The processor may choose to continue, but will not normally
     * proceed to a successful completion.</p>
     *
     * <p>The method should throw an exception if it is unable to
     * process the error, or if it wishes execution to terminate
     * immediately. The processor will not necessarily honor this
     * request.</p>
     *
     * @param exception The error information encapsulated in a
     *    <code>TransformerException</code>.
     *
     * @throws javax.xml.transform.TransformerException if the application
     * chooses to discontinue the transformation.
     *
     * @see javax.xml.transform.TransformerException
     */
    public abstract void fatalError(TransformerException exception)
        throws TransformerException;
}
