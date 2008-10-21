/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.tools.javac.api;

import java.util.Locale;
import javax.tools.Diagnostic;

/**
 * Provides simple functionalities for javac diagnostic formatting
 * @param <D> type of diagnostic handled by this formatter
 */
public interface DiagnosticFormatter<D extends Diagnostic<?>> {

    /**
     * Whether the source code output for this diagnostic is to be displayed
     *
     * @param diag diagnostic to be formatted
     * @return true if the source line this diagnostic refers to is to be displayed
     */
    boolean displaySource(D diag);

    /**
     * Format the contents of a diagnostics
     *
     * @param diag the diagnostic to be formatted
     * @param l locale object to be used for i18n
     * @return a string representing the diagnostic
     */
    public String format(D diag, Locale l);

    /**
     * Controls the way in which a diagnostic message is displayed.
     *
     * @param diag diagnostic to be formatted
     * @param l locale object to be used for i18n
     * @return string representation of the diagnostic message
     */
    public String formatMessage(D diag,Locale l);

    /**
     * Controls the way in which a diagnostic kind is displayed.
     *
     * @param diag diagnostic to be formatted
     * @param l locale object to be used for i18n
     * @return string representation of the diagnostic prefix
     */
    public String formatKind(D diag, Locale l);

    /**
     * Controls the way in which a diagnostic source is displayed.
     *
     * @param diag diagnostic to be formatted
     * @param l locale object to be used for i18n
     * @param fullname whether the source fullname should be printed
     * @return string representation of the diagnostic source
     */
    public String formatSource(D diag, boolean fullname, Locale l);

    /**
     * Controls the way in which a diagnostic position is displayed.
     *
     * @param diag diagnostic to be formatted
     * @param pk enum constant representing the position kind
     * @param l locale object to be used for i18n
     * @return string representation of the diagnostic position
     */
    public String formatPosition(D diag, PositionKind pk, Locale l);
    //where
    /**
     * This enum defines a set of constants for all the kinds of position
     * that a diagnostic can be asked for. All positions are intended to be
     * relative to a given diagnostic source.
     */
    public enum PositionKind {
        /**
         * Start position
         */
        START,
        /**
         * End position
         */
        END,
        /**
         * Line number
         */
        LINE,
        /**
         * Column number
         */
        COLUMN,
        /**
         * Offset position
         */
        OFFSET
    }
}
