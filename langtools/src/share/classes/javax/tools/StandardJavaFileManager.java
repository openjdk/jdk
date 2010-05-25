/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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

package javax.tools;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * File manager based on {@linkplain File java.io.File}.  A common way
 * to obtain an instance of this class is using {@linkplain
 * JavaCompiler#getStandardFileManager
 * getStandardFileManager}, for example:
 *
 * <pre>
 *   JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
 *   {@code DiagnosticCollector<JavaFileObject>} diagnostics =
 *       new {@code DiagnosticCollector<JavaFileObject>()};
 *   StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, null);
 * </pre>
 *
 * This file manager creates file objects representing regular
 * {@linkplain File files},
 * {@linkplain java.util.zip.ZipEntry zip file entries}, or entries in
 * similar file system based containers.  Any file object returned
 * from a file manager implementing this interface must observe the
 * following behavior:
 *
 * <ul>
 *   <li>
 *     File names need not be canonical.
 *   </li>
 *   <li>
 *     For file objects representing regular files
 *     <ul>
 *       <li>
 *         the method <code>{@linkplain FileObject#delete()}</code>
 *         is equivalent to <code>{@linkplain File#delete()}</code>,
 *       </li>
 *       <li>
 *         the method <code>{@linkplain FileObject#getLastModified()}</code>
 *         is equivalent to <code>{@linkplain File#lastModified()}</code>,
 *       </li>
 *       <li>
 *         the methods <code>{@linkplain FileObject#getCharContent(boolean)}</code>,
 *         <code>{@linkplain FileObject#openInputStream()}</code>, and
 *         <code>{@linkplain FileObject#openReader(boolean)}</code>
 *         must succeed if the following would succeed (ignoring
 *         encoding issues):
 *         <blockquote>
 *           <pre>new {@linkplain java.io.FileInputStream#FileInputStream(File) FileInputStream}(new {@linkplain File#File(java.net.URI) File}({@linkplain FileObject fileObject}.{@linkplain FileObject#toUri() toUri}()))</pre>
 *         </blockquote>
 *       </li>
 *       <li>
 *         and the methods
 *         <code>{@linkplain FileObject#openOutputStream()}</code>, and
 *         <code>{@linkplain FileObject#openWriter()}</code> must
 *         succeed if the following would succeed (ignoring encoding
 *         issues):
 *         <blockquote>
 *           <pre>new {@linkplain java.io.FileOutputStream#FileOutputStream(File) FileOutputStream}(new {@linkplain File#File(java.net.URI) File}({@linkplain FileObject fileObject}.{@linkplain FileObject#toUri() toUri}()))</pre>
 *         </blockquote>
 *       </li>
 *     </ul>
 *   </li>
 *   <li>
 *     The {@linkplain java.net.URI URI} returned from
 *     <code>{@linkplain FileObject#toUri()}</code>
 *     <ul>
 *       <li>
 *         must be {@linkplain java.net.URI#isAbsolute() absolute} (have a schema), and
 *       </li>
 *       <li>
 *         must have a {@linkplain java.net.URI#normalize() normalized}
 *         {@linkplain java.net.URI#getPath() path component} which
 *         can be resolved without any process-specific context such
 *         as the current directory (file names must be absolute).
 *       </li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * According to these rules, the following URIs, for example, are
 * allowed:
 * <ul>
 *   <li>
 *     <code>file:///C:/Documents%20and%20Settings/UncleBob/BobsApp/Test.java</code>
 *   </li>
 *   <li>
 *     <code>jar:///C:/Documents%20and%20Settings/UncleBob/lib/vendorA.jar!com/vendora/LibraryClass.class</code>
 *   </li>
 * </ul>
 * Whereas these are not (reason in parentheses):
 * <ul>
 *   <li>
 *     <code>file:BobsApp/Test.java</code> (the file name is relative
 *     and depend on the current directory)
 *   </li>
 *   <li>
 *     <code>jar:lib/vendorA.jar!com/vendora/LibraryClass.class</code>
 *     (the first half of the path depends on the current directory,
 *     whereas the component after ! is legal)
 *   </li>
 *   <li>
 *     <code>Test.java</code> (this URI depends on the current
 *     directory and does not have a schema)
 *   </li>
 *   <li>
 *     <code>jar:///C:/Documents%20and%20Settings/UncleBob/BobsApp/../lib/vendorA.jar!com/vendora/LibraryClass.class</code>
 *     (the path is not normalized)
 *   </li>
 * </ul>
 *
 * @author Peter von der Ah&eacute;
 * @since 1.6
 */
public interface StandardJavaFileManager extends JavaFileManager {

    /**
     * Compares two file objects and return true if they represent the
     * same canonical file, zip file entry, or entry in any file
     * system based container.
     *
     * @param a a file object
     * @param b a file object
     * @return true if the given file objects represent the same
     * canonical file or zip file entry; false otherwise
     *
     * @throws IllegalArgumentException if either of the arguments
     * were created with another file manager implementation
     */
    boolean isSameFile(FileObject a, FileObject b);

    /**
     * Gets file objects representing the given files.
     *
     * @param files a list of files
     * @return a list of file objects
     * @throws IllegalArgumentException if the list of files includes
     * a directory
     */
    Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(
        Iterable<? extends File> files);

    /**
     * Gets file objects representing the given files.
     * Convenience method equivalent to:
     *
     * <pre>
     *     getJavaFileObjectsFromFiles({@linkplain java.util.Arrays#asList Arrays.asList}(files))
     * </pre>
     *
     * @param files an array of files
     * @return a list of file objects
     * @throws IllegalArgumentException if the array of files includes
     * a directory
     * @throws NullPointerException if the given array contains null
     * elements
     */
    Iterable<? extends JavaFileObject> getJavaFileObjects(File... files);

    /**
     * Gets file objects representing the given file names.
     *
     * @param names a list of file names
     * @return a list of file objects
     * @throws IllegalArgumentException if the list of file names
     * includes a directory
     */
    Iterable<? extends JavaFileObject> getJavaFileObjectsFromStrings(
        Iterable<String> names);

    /**
     * Gets file objects representing the given file names.
     * Convenience method equivalent to:
     *
     * <pre>
     *     getJavaFileObjectsFromStrings({@linkplain java.util.Arrays#asList Arrays.asList}(names))
     * </pre>
     *
     * @param names a list of file names
     * @return a list of file objects
     * @throws IllegalArgumentException if the array of file names
     * includes a directory
     * @throws NullPointerException if the given array contains null
     * elements
     */
    Iterable<? extends JavaFileObject> getJavaFileObjects(String... names);

    /**
     * Associates the given path with the given location.  Any
     * previous value will be discarded.
     *
     * @param location a location
     * @param path a list of files, if {@code null} use the default
     * path for this location
     * @see #getLocation
     * @throws IllegalArgumentException if location is an output
     * location and path does not contain exactly one element
     * @throws IOException if location is an output location and path
     * does not represent an existing directory
     */
    void setLocation(Location location, Iterable<? extends File> path)
        throws IOException;

    /**
     * Gets the path associated with the given location.
     *
     * @param location a location
     * @return a list of files or {@code null} if this location has no
     * associated path
     * @see #setLocation
     */
    Iterable<? extends File> getLocation(Location location);

}
