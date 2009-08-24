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

package com.sun.mirror.apt;


import java.io.*;


/**
 * This interface supports the creation of new files by an
 * annotation processor.
 * Files created in this way will be known to the annotation processing
 * tool implementing this interface, better enabling the tool to manage them.
 * Four kinds of files are distinguished:
 * source files, class files, other text files, and other binary files.
 * The latter two are collectively referred to as <i>auxiliary</i> files.
 *
 * <p> There are two distinguished locations (subtrees within the
 * file system) where newly created files are placed:
 * one for new source files, and one for new class files.
 * (These might be specified on a tool's command line, for example,
 * using flags such as <tt>-s</tt> and <tt>-d</tt>.)
 * Auxiliary files may be created in either location.
 *
 * <p> During each run of an annotation processing tool, a file
 * with a given pathname may be created only once.  If that file already
 * exists before the first attempt to create it, the old contents will
 * be deleted.  Any subsequent attempt to create the same file during
 * a run will fail.
 *
 * @deprecated All components of this API have been superseded by the
 * standardized annotation processing API.  The replacement for the
 * functionality of this interface is {@link
 * javax.annotation.processing.Filer}.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @since 1.5
 */
@Deprecated
@SuppressWarnings("deprecation")
public interface Filer {

    /**
     * Creates a new source file and returns a writer for it.
     * The file's name and path (relative to the root of all newly created
     * source files) is based on the type to be declared in that file.
     * If more than one type is being declared, the name of the principal
     * top-level type (the public one, for example) should be used.
     *
     * <p> The {@linkplain java.nio.charset.Charset charset} used to
     * encode the file is determined by the implementation.
     * An annotation processing tool may have an <tt>-encoding</tt>
     * flag or the like for specifying this.  It will typically use
     * the platform's default encoding if none is specified.
     *
     * @param name  canonical (fully qualified) name of the principal type
     *          being declared in this file
     * @return a writer for the new file
     * @throws IOException if the file cannot be created
     */
    PrintWriter createSourceFile(String name) throws IOException;

    /**
     * Creates a new class file, and returns a stream for writing to it.
     * The file's name and path (relative to the root of all newly created
     * class files) is based on the name of the type being written.
     *
     * @param name canonical (fully qualified) name of the type being written
     * @return a stream for writing to the new file
     * @throws IOException if the file cannot be created
     */
    OutputStream createClassFile(String name) throws IOException;

    /**
     * Creates a new text file, and returns a writer for it.
     * The file is located along with either the
     * newly created source or newly created binary files.  It may be
     * named relative to some package (as are source and binary files),
     * and from there by an arbitrary pathname.  In a loose sense, the
     * pathname of the new file will be the concatenation of
     * <tt>loc</tt>, <tt>pkg</tt>, and <tt>relPath</tt>.
     *
     * <p> A {@linkplain java.nio.charset.Charset charset} for
     * encoding the file may be provided.  If none is given, the
     * charset used to encode source files
     * (see {@link #createSourceFile(String)}) will be used.
     *
     * @param loc location of the new file
     * @param pkg package relative to which the file should be named,
     *          or the empty string if none
     * @param relPath final pathname components of the file
     * @param charsetName the name of the charset to use, or null if none
     *          is being explicitly specified
     * @return a writer for the new file
     * @throws IOException if the file cannot be created
     */
    PrintWriter createTextFile(Location loc,
                               String pkg,
                               File relPath,
                               String charsetName) throws IOException;

    /**
     * Creates a new binary file, and returns a stream for writing to it.
     * The file is located along with either the
     * newly created source or newly created binary files.  It may be
     * named relative to some package (as are source and binary files),
     * and from there by an arbitrary pathname.  In a loose sense, the
     * pathname of the new file will be the concatenation of
     * <tt>loc</tt>, <tt>pkg</tt>, and <tt>relPath</tt>.
     *
     * @param loc location of the new file
     * @param pkg package relative to which the file should be named,
     *          or the empty string if none
     * @param relPath final pathname components of the file
     * @return a stream for writing to the new file
     * @throws IOException if the file cannot be created
     */
    OutputStream createBinaryFile(Location loc,
                                  String pkg,
                                  File relPath) throws IOException;


    /**
     * Locations (subtrees within the file system) where new files are created.
     *
     * @deprecated All components of this API have been superseded by
     * the standardized annotation processing API.  The replacement
     * for the functionality of this enum is {@link
     * javax.tools.StandardLocation}.
     */
    @Deprecated
    enum Location {
        /** The location of new source files. */
        SOURCE_TREE,
        /** The location of new class files. */
        CLASS_TREE
    }
}
