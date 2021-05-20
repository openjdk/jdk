/*
 * Copyright (c) 1998, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.doclet;

import java.io.PrintWriter;
import java.util.Locale;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

import com.sun.source.util.DocTreePath;

/**
 * This interface provides error, warning and notice reporting.
 *
 * @since 9
 */
public interface Reporter {

    /**
     * Print error message and increment error count.
     *
     * @param kind specify the diagnostic kind
     * @param msg message to print
     */
    void print(Diagnostic.Kind kind, String msg);

    /**
     * Print an error message and increment error count.
     *
     * @param kind specify the diagnostic kind
     * @param path the DocTreePath of item where the error occurs
     * @param msg message to print
     */
    void print(Diagnostic.Kind kind, DocTreePath path, String msg);

    /**
     * Print an error message and increment error count.
     *
     * @param kind specify the diagnostic kind
     * @param e the Element for which  the error occurs
     * @param msg message to print
     */
    void print(Diagnostic.Kind kind, Element e, String msg);


    /**
     * Returns a writer that can be used by a doclet to write non-diagnostic output,
     * or {@code null} if no such writer is available.
     *
     * @apiNote
     * The value may or may not be the same as that returned by {@link #getDiagnosticWriter()}.
     *
     * @implSpec
     * This implementation returns {@code null}.
     * The implementation provided by the {@code javadoc} tool to
     * {@link Doclet#init(Locale, Reporter) initialize a doclet}
     * always returns a non-{@code null} value.
     *
     * @return the writer
     * @since 17
     */
    default PrintWriter getStandardWriter() {
        return null;
    }

    /**
     * Returns a writer that can be used by a doclet to write diagnostic output,
     * or {@code null} if no such writer is available.
     *
     * @apiNote
     * The value may or may not be the same as that returned by {@link #getStandardWriter()}.
     *
     * @implSpec
     * This implementation returns {@code null}.
     * The implementation provided by the {@code javadoc} tool to
     * {@link Doclet#init(Locale, Reporter) initialize a doclet}
     * always returns a non-{@code null} value.
     *
     * @return the writer
     * @since 17
     */
    default PrintWriter getDiagnosticWriter() {
        return null;
    }

}
