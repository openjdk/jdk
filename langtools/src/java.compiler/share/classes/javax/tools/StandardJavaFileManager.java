/*
 * Copyright (c) 2006, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Path;
import java.util.Arrays;

import static javax.tools.FileManagerUtils.*;

/**
 * File manager based on {@linkplain File java.io.File} and {@linkplain Path java.nio.file.Path}.
 *
 * A common way to obtain an instance of this class is using
 * {@linkplain JavaCompiler#getStandardFileManager getStandardFileManager}, for example:
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
 *     <code>jar:///C:/Documents%20and%20Settings/UncleBob/lib/vendorA.jar!/com/vendora/LibraryClass.class</code>
 *   </li>
 * </ul>
 * Whereas these are not (reason in parentheses):
 * <ul>
 *   <li>
 *     <code>file:BobsApp/Test.java</code> (the file name is relative
 *     and depend on the current directory)
 *   </li>
 *   <li>
 *     <code>jar:lib/vendorA.jar!/com/vendora/LibraryClass.class</code>
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
 * <p>All implementations of this interface must support Path objects representing
 * files in the {@linkplain java.nio.file.FileSystems#getDefault() default file system.}
 * It is recommended that implementations should support Path objects from any filesystem.</p>
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
     * canonical file, zip file entry or path; false otherwise
     *
     * @throws IllegalArgumentException if either of the arguments
     * were created with another file manager implementation
     */
    @Override
    boolean isSameFile(FileObject a, FileObject b);

    /**
     * Returns file objects representing the given files.
     *
     * @param files a list of files
     * @return a list of file objects
     * @throws IllegalArgumentException if the list of files includes
     * a directory
     */
    Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(
        Iterable<? extends File> files);

    /**
     * Returns file objects representing the given paths.
     *
     * <p>The default implementation converts each path to a file and calls
     * {@link #getJavaFileObjectsFromFiles getJavaObjectsFromFiles}.
     * IllegalArgumentException will be thrown if any of the paths
     * cannot be converted to a file.
     *
     * @param paths a list of paths
     * @return a list of file objects
     * @throws IllegalArgumentException if the list of paths includes
     * a directory or if this file manager does not support any of the
     * given paths.
     *
     * @since 9
     */
    default Iterable<? extends JavaFileObject> getJavaFileObjectsFromPaths(
            Iterable<? extends Path> paths) {
        return getJavaFileObjectsFromFiles(asFiles(paths));
    }

    /**
     * Returns file objects representing the given files.
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
     * Returns file objects representing the given paths.
     * Convenience method equivalent to:
     *
     * <pre>
     *     getJavaFileObjectsFromPaths({@linkplain java.util.Arrays#asList Arrays.asList}(paths))
     * </pre>
     *
     * @param paths an array of paths
     * @return a list of file objects
     * @throws IllegalArgumentException if the array of files includes
     * a directory
     * @throws NullPointerException if the given array contains null
     * elements
     *
     * @since 9
     */
    default Iterable<? extends JavaFileObject> getJavaFileObjects(Path... paths) {
        return getJavaFileObjectsFromPaths(Arrays.asList(paths));
    }

    /**
     * Returns file objects representing the given file names.
     *
     * @param names a list of file names
     * @return a list of file objects
     * @throws IllegalArgumentException if the list of file names
     * includes a directory
     */
    Iterable<? extends JavaFileObject> getJavaFileObjectsFromStrings(
        Iterable<String> names);

    /**
     * Returns file objects representing the given file names.
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
     * Associates the given search path with the given location.  Any
     * previous value will be discarded.
     *
     * @param location a location
     * @param files a list of files, if {@code null} use the default
     * search path for this location
     * @see #getLocation
     * @throws IllegalArgumentException if {@code location} is an output
     * location and {@code files} does not contain exactly one element
     * @throws IOException if {@code location} is an output location and
     * does not represent an existing directory
     */
    void setLocation(Location location, Iterable<? extends File> files)
        throws IOException;

    /**
     * Associates the given search path with the given location.  Any
     * previous value will be discarded.
     *
     * <p>The default implementation converts each path to a file and calls
     * {@link #getJavaFileObjectsFromFiles getJavaObjectsFromFiles}.
     * IllegalArgumentException will be thrown if any of the paths
     * cannot be converted to a file.</p>
     *
     * @param location a location
     * @param paths a list of paths, if {@code null} use the default
     * search path for this location
     * @see #getLocation
     * @throws IllegalArgumentException if {@code location} is an output
     * location and {@code paths} does not contain exactly one element
     * or if this file manager does not support any of the given paths
     * @throws IOException if {@code location} is an output location and
     * {@code paths} does not represent an existing directory
     *
     * @since 9
     */
    default void setLocationFromPaths(Location location, Iterable<? extends Path> paths)
        throws IOException {
        setLocation(location, asFiles(paths));
    }

    /**
     * Returns the search path associated with the given location.
     *
     * @param location a location
     * @return a list of files or {@code null} if this location has no
     * associated search path
     * @throws IllegalStateException if any element of the search path
     * cannot be converted to a {@linkplain File}.
     *
     * @see #setLocation
     * @see Path#toFile
     */
    Iterable<? extends File> getLocation(Location location);

    /**
     * Returns the search path associated with the given location.
     *
     * @param location a location
     * @return a list of paths or {@code null} if this location has no
     * associated search path
     *
     * @see #setLocationFromPaths
     * @since 9
     */
    default Iterable<? extends Path> getLocationAsPaths(Location location) {
        return asPaths(getLocation(location));
    }

    /**
     * Returns the path, if any, underlying this file object (optional operation).
     * File objects derived from a {@link java.nio.file.FileSystem FileSystem},
     * including the default file system, typically have a corresponding underlying
     * {@link java.nio.file.Path Path} object. In such cases, this method may be
     * used to access that object.
     *
     * <p>The default implementation throws {@link UnsupportedOperationException}
     * for all files.</p>
     *
     * @param file a file object
     * @return a path representing the same underlying file system artifact
     * @throws IllegalArgumentException if the file object does not have an underlying path
     * @throws UnsupportedOperationException if the operation is not supported by this file manager
     *
     * @since 9
     */
    default Path asPath(FileObject file) {
        throw new UnsupportedOperationException();
    }

}
