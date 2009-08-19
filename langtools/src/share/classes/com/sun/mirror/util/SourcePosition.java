/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.mirror.util;


import java.io.File;


/**
 * Represents a position in a source file.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  There is no direct
 * replacement for the functionality of this interface since the
 * standardized {@link javax.annotation.processing.Messager Messager}
 * API implicitly takes a source position argument via any element,
 * annotation mirror, or annotation value passed along with the
 * message.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public interface SourcePosition {

    /**
     * Returns the source file containing this position.
     *
     * @return the source file containing this position; never null
     */
    File file();

    /**
     * Returns the line number of this position.  Lines are numbered
     * starting with 1.
     *
     * @return the line number of this position, or 0 if the line
     * number is unknown or not applicable
     */
    int line();

    /**
     * Returns the column number of this position.  Columns are numbered
     * starting with 1.
     *
     * @return the column number of this position, or 0 if the column
     * number is unknown or not applicable
     */
    int column();
}
