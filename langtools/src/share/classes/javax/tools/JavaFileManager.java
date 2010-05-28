/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import static javax.tools.JavaFileObject.Kind;

/**
 * File manager for tools operating on Java&trade; programming language
 * source and class files.  In this context, <em>file</em> means an
 * abstraction of regular files and other sources of data.
 *
 * <p>When constructing new JavaFileObjects, the file manager must
 * determine where to create them.  For example, if a file manager
 * manages regular files on a file system, it would most likely have a
 * current/working directory to use as default location when creating
 * or finding files.  A number of hints can be provided to a file
 * manager as to where to create files.  Any file manager might choose
 * to ignore these hints.
 *
 * <p>Some methods in this interface use class names.  Such class
 * names must be given in the Java Virtual Machine internal form of
 * fully qualified class and interface names.  For convenience '.'
 * and '/' are interchangeable.  The internal form is defined in
 * chapter four of the
 * <a href="http://java.sun.com/docs/books/vmspec/2nd-edition/jvms-maintenance.html">Java
 * Virtual Machine Specification</a>.

 * <blockquote><p>
 *   <i>Discussion:</i> this means that the names
 *   "java/lang.package-info", "java/lang/package-info",
 *   "java.lang.package-info", are valid and equivalent.  Compare to
 *   binary name as defined in the
 *   <a href="http://java.sun.com/docs/books/jls/">Java Language
 *   Specification (JLS)</a> section 13.1 "The Form of a Binary".
 * </p></blockquote>
 *
 * <p>The case of names is significant.  All names should be treated
 * as case-sensitive.  For example, some file systems have
 * case-insensitive, case-aware file names.  File objects representing
 * such files should take care to preserve case by using {@link
 * java.io.File#getCanonicalFile} or similar means.  If the system is
 * not case-aware, file objects must use other means to preserve case.
 *
 * <p><em><a name="relative_name">Relative names</a>:</em> some
 * methods in this interface use relative names.  A relative name is a
 * non-null, non-empty sequence of path segments separated by '/'.
 * '.' or '..'  are invalid path segments.  A valid relative name must
 * match the "path-rootless" rule of <a
 * href="http://www.ietf.org/rfc/rfc3986.txt">RFC&nbsp;3986</a>,
 * section&nbsp;3.3.  Informally, this should be true:
 *
 * <!-- URI.create(relativeName).normalize().getPath().equals(relativeName) -->
 * <pre>  URI.{@linkplain java.net.URI#create create}(relativeName).{@linkplain java.net.URI#normalize normalize}().{@linkplain java.net.URI#getPath getPath}().equals(relativeName)</pre>
 *
 * <p>All methods in this interface might throw a SecurityException.
 *
 * <p>An object of this interface is not required to support
 * multi-threaded access, that is, be synchronized.  However, it must
 * support concurrent access to different file objects created by this
 * object.
 *
 * <p><em>Implementation note:</em> a consequence of this requirement
 * is that a trivial implementation of output to a {@linkplain
 * java.util.jar.JarOutputStream} is not a sufficient implementation.
 * That is, rather than creating a JavaFileObject that returns the
 * JarOutputStream directly, the contents must be cached until closed
 * and then written to the JarOutputStream.
 *
 * <p>Unless explicitly allowed, all methods in this interface might
 * throw a NullPointerException if given a {@code null} argument.
 *
 * @author Peter von der Ah&eacute;
 * @author Jonathan Gibbons
 * @see JavaFileObject
 * @see FileObject
 * @since 1.6
 */
public interface JavaFileManager extends Closeable, Flushable, OptionChecker {

    /**
     * Interface for locations of file objects.  Used by file managers
     * to determine where to place or search for file objects.
     */
    interface Location {
        /**
         * Gets the name of this location.
         *
         * @return a name
         */
        String getName();

        /**
         * Determines if this is an output location.  An output
         * location is a location that is conventionally used for
         * output.
         *
         * @return true if this is an output location, false otherwise
         */
        boolean isOutputLocation();
    }

    /**
     * Gets a class loader for loading plug-ins from the given
     * location.  For example, to load annotation processors, a
     * compiler will request a class loader for the {@link
     * StandardLocation#ANNOTATION_PROCESSOR_PATH
     * ANNOTATION_PROCESSOR_PATH} location.
     *
     * @param location a location
     * @return a class loader for the given location; or {@code null}
     * if loading plug-ins from the given location is disabled or if
     * the location is not known
     * @throws SecurityException if a class loader can not be created
     * in the current security context
     * @throws IllegalStateException if {@link #close} has been called
     * and this file manager cannot be reopened
     */
    ClassLoader getClassLoader(Location location);

    /**
     * Lists all file objects matching the given criteria in the given
     * location.  List file objects in "subpackages" if recurse is
     * true.
     *
     * <p>Note: even if the given location is unknown to this file
     * manager, it may not return {@code null}.  Also, an unknown
     * location may not cause an exception.
     *
     * @param location     a location
     * @param packageName  a package name
     * @param kinds        return objects only of these kinds
     * @param recurse      if true include "subpackages"
     * @return an Iterable of file objects matching the given criteria
     * @throws IOException if an I/O error occurred, or if {@link
     * #close} has been called and this file manager cannot be
     * reopened
     * @throws IllegalStateException if {@link #close} has been called
     * and this file manager cannot be reopened
     */
    Iterable<JavaFileObject> list(Location location,
                                  String packageName,
                                  Set<Kind> kinds,
                                  boolean recurse)
        throws IOException;

    /**
     * Infers a binary name of a file object based on a location.  The
     * binary name returned might not be a valid JLS binary name.
     *
     * @param location a location
     * @param file a file object
     * @return a binary name or {@code null} the file object is not
     * found in the given location
     * @throws IllegalStateException if {@link #close} has been called
     * and this file manager cannot be reopened
     */
    String inferBinaryName(Location location, JavaFileObject file);

    /**
     * Compares two file objects and return true if they represent the
     * same underlying object.
     *
     * @param a a file object
     * @param b a file object
     * @return true if the given file objects represent the same
     * underlying object
     *
     * @throws IllegalArgumentException if either of the arguments
     * were created with another file manager and this file manager
     * does not support foreign file objects
     */
    boolean isSameFile(FileObject a, FileObject b);

    /**
     * Handles one option.  If {@code current} is an option to this
     * file manager it will consume any arguments to that option from
     * {@code remaining} and return true, otherwise return false.
     *
     * @param current current option
     * @param remaining remaining options
     * @return true if this option was handled by this file manager,
     * false otherwise
     * @throws IllegalArgumentException if this option to this file
     * manager is used incorrectly
     * @throws IllegalStateException if {@link #close} has been called
     * and this file manager cannot be reopened
     */
    boolean handleOption(String current, Iterator<String> remaining);

    /**
     * Determines if a location is known to this file manager.
     *
     * @param location a location
     * @return true if the location is known
     */
    boolean hasLocation(Location location);

    /**
     * Gets a {@linkplain JavaFileObject file object} for input
     * representing the specified class of the specified kind in the
     * given location.
     *
     * @param location a location
     * @param className the name of a class
     * @param kind the kind of file, must be one of {@link
     * JavaFileObject.Kind#SOURCE SOURCE} or {@link
     * JavaFileObject.Kind#CLASS CLASS}
     * @return a file object, might return {@code null} if the
     * file does not exist
     * @throws IllegalArgumentException if the location is not known
     * to this file manager and the file manager does not support
     * unknown locations, or if the kind is not valid
     * @throws IOException if an I/O error occurred, or if {@link
     * #close} has been called and this file manager cannot be
     * reopened
     * @throws IllegalStateException if {@link #close} has been called
     * and this file manager cannot be reopened
     */
    JavaFileObject getJavaFileForInput(Location location,
                                       String className,
                                       Kind kind)
        throws IOException;

    /**
     * Gets a {@linkplain JavaFileObject file object} for output
     * representing the specified class of the specified kind in the
     * given location.
     *
     * <p>Optionally, this file manager might consider the sibling as
     * a hint for where to place the output.  The exact semantics of
     * this hint is unspecified.  Sun's compiler, javac, for
     * example, will place class files in the same directories as
     * originating source files unless a class file output directory
     * is provided.  To facilitate this behavior, javac might provide
     * the originating source file as sibling when calling this
     * method.
     *
     * @param location a location
     * @param className the name of a class
     * @param kind the kind of file, must be one of {@link
     * JavaFileObject.Kind#SOURCE SOURCE} or {@link
     * JavaFileObject.Kind#CLASS CLASS}
     * @param sibling a file object to be used as hint for placement;
     * might be {@code null}
     * @return a file object for output
     * @throws IllegalArgumentException if sibling is not known to
     * this file manager, or if the location is not known to this file
     * manager and the file manager does not support unknown
     * locations, or if the kind is not valid
     * @throws IOException if an I/O error occurred, or if {@link
     * #close} has been called and this file manager cannot be
     * reopened
     * @throws IllegalStateException {@link #close} has been called
     * and this file manager cannot be reopened
     */
    JavaFileObject getJavaFileForOutput(Location location,
                                        String className,
                                        Kind kind,
                                        FileObject sibling)
        throws IOException;

    /**
     * Gets a {@linkplain FileObject file object} for input
     * representing the specified <a href="JavaFileManager.html#relative_name">relative
     * name</a> in the specified package in the given location.
     *
     * <p>If the returned object represents a {@linkplain
     * JavaFileObject.Kind#SOURCE source} or {@linkplain
     * JavaFileObject.Kind#CLASS class} file, it must be an instance
     * of {@link JavaFileObject}.
     *
     * <p>Informally, the file object returned by this method is
     * located in the concatenation of the location, package name, and
     * relative name.  For example, to locate the properties file
     * "resources/compiler.properties" in the package
     * "com.sun.tools.javac" in the {@linkplain
     * StandardLocation#SOURCE_PATH SOURCE_PATH} location, this method
     * might be called like so:
     *
     * <pre>getFileForInput(SOURCE_PATH, "com.sun.tools.javac", "resources/compiler.properties");</pre>
     *
     * <p>If the call was executed on Windows, with SOURCE_PATH set to
     * <code>"C:\Documents&nbsp;and&nbsp;Settings\UncleBob\src\share\classes"</code>,
     * a valid result would be a file object representing the file
     * <code>"C:\Documents&nbsp;and&nbsp;Settings\UncleBob\src\share\classes\com\sun\tools\javac\resources\compiler.properties"</code>.
     *
     * @param location a location
     * @param packageName a package name
     * @param relativeName a relative name
     * @return a file object, might return {@code null} if the file
     * does not exist
     * @throws IllegalArgumentException if the location is not known
     * to this file manager and the file manager does not support
     * unknown locations, or if {@code relativeName} is not valid
     * @throws IOException if an I/O error occurred, or if {@link
     * #close} has been called and this file manager cannot be
     * reopened
     * @throws IllegalStateException if {@link #close} has been called
     * and this file manager cannot be reopened
     */
    FileObject getFileForInput(Location location,
                               String packageName,
                               String relativeName)
        throws IOException;

    /**
     * Gets a {@linkplain FileObject file object} for output
     * representing the specified <a href="JavaFileManager.html#relative_name">relative
     * name</a> in the specified package in the given location.
     *
     * <p>Optionally, this file manager might consider the sibling as
     * a hint for where to place the output.  The exact semantics of
     * this hint is unspecified.  Sun's compiler, javac, for
     * example, will place class files in the same directories as
     * originating source files unless a class file output directory
     * is provided.  To facilitate this behavior, javac might provide
     * the originating source file as sibling when calling this
     * method.
     *
     * <p>If the returned object represents a {@linkplain
     * JavaFileObject.Kind#SOURCE source} or {@linkplain
     * JavaFileObject.Kind#CLASS class} file, it must be an instance
     * of {@link JavaFileObject}.
     *
     * <p>Informally, the file object returned by this method is
     * located in the concatenation of the location, package name, and
     * relative name or next to the sibling argument.  See {@link
     * #getFileForInput getFileForInput} for an example.
     *
     * @param location a location
     * @param packageName a package name
     * @param relativeName a relative name
     * @param sibling a file object to be used as hint for placement;
     * might be {@code null}
     * @return a file object
     * @throws IllegalArgumentException if sibling is not known to
     * this file manager, or if the location is not known to this file
     * manager and the file manager does not support unknown
     * locations, or if {@code relativeName} is not valid
     * @throws IOException if an I/O error occurred, or if {@link
     * #close} has been called and this file manager cannot be
     * reopened
     * @throws IllegalStateException if {@link #close} has been called
     * and this file manager cannot be reopened
     */
    FileObject getFileForOutput(Location location,
                                String packageName,
                                String relativeName,
                                FileObject sibling)
        throws IOException;

    /**
     * Flushes any resources opened for output by this file manager
     * directly or indirectly.  Flushing a closed file manager has no
     * effect.
     *
     * @throws IOException if an I/O error occurred
     * @see #close
     */
    void flush() throws IOException;

    /**
     * Releases any resources opened by this file manager directly or
     * indirectly.  This might render this file manager useless and
     * the effect of subsequent calls to methods on this object or any
     * objects obtained through this object is undefined unless
     * explicitly allowed.  However, closing a file manager which has
     * already been closed has no effect.
     *
     * @throws IOException if an I/O error occurred
     * @see #flush
     */
    void close() throws IOException;
}
