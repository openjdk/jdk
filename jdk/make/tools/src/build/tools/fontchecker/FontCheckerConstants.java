/*
 * Copyright 2002-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package build.tools.fontchecker;

public interface FontCheckerConstants {

    /* code sent to indicate child process started OK */
    public static final int CHILD_STARTED_OK   = 100;

    /* error codes returned from child process */
    public static final int ERR_FONT_OK         = 65;
    public static final int ERR_FONT_NOT_FOUND  = 60;
    public static final int ERR_FONT_BAD_FORMAT = 61;
    public static final int ERR_FONT_READ_EXCPT = 62;
    public static final int ERR_FONT_DISPLAY    = 64;
    public static final int ERR_FONT_EOS        = -1;
    /* nl char sent after child crashes */
    public static final int ERR_FONT_CRASH      = 10;

    /* 0 and 1 are reserved, and commands can only be a single digit integer */
    public static final int EXITCOMMAND = 2;
}
