/*
 * Copyright (c) 1994, 2008, Oracle and/or its affiliates. All rights reserved.
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

package java.io;


/**
 * Signals that an attempt to open the file denoted by a specified pathname
 * has failed.
 *
 * <p> This exception will be thrown by the {@link FileInputStream}, {@link
 * FileOutputStream}, and {@link RandomAccessFile} constructors when a file
 * with the specified pathname does not exist.  It will also be thrown by these
 * constructors if the file does exist but for some reason is inaccessible, for
 * example when an attempt is made to open a read-only file for writing.
 *
 * @author  unascribed
 * @since   JDK1.0
 */

public class FileNotFoundException extends IOException {
    private static final long serialVersionUID = -897856973823710492L;

    /**
     * Constructs a <code>FileNotFoundException</code> with
     * <code>null</code> as its error detail message.
     */
    public FileNotFoundException() {
        super();
    }

    /**
     * Constructs a <code>FileNotFoundException</code> with the
     * specified detail message. The string <code>s</code> can be
     * retrieved later by the
     * <code>{@link java.lang.Throwable#getMessage}</code>
     * method of class <code>java.lang.Throwable</code>.
     *
     * @param   s   the detail message.
     */
    public FileNotFoundException(String s) {
        super(s);
    }

    /**
     * Constructs a <code>FileNotFoundException</code> with a detail message
     * consisting of the given pathname string followed by the given reason
     * string.  If the <code>reason</code> argument is <code>null</code> then
     * it will be omitted.  This private constructor is invoked only by native
     * I/O methods.
     *
     * @since 1.2
     */
    private FileNotFoundException(String path, String reason) {
        super(path + ((reason == null)
                      ? ""
                      : " (" + reason + ")"));
    }

}
