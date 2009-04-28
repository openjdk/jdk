/*
 * Copyright 2007-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.nio.file;

import java.nio.file.attribute.*;
import java.nio.channels.*;
import java.io.*;
import java.net.URI;
import java.util.*;

/**
 * A file reference that locates a file using a system dependent path. The file
 * is not required to exist.
 *
 * <p> On many platforms a <em>path</em> is the means to locate and access files
 * in a file system. A path is hierarchical and composed of a sequence of
 * directory and file name elements separated by a special separator or
 * delimiter.
 *
 * <h4>Path operations</h4>
 *
 * <p> A system dependent path represented by this class is conceptually a
 * sequence of name elements and optionally a <em>root component</em>. The name
 * that is <em>farthest</em> from the root of the directory hierarchy is the
 * name of a file or directory. The other elements are directory names. The root
 * component typically identifies a file system hierarchy. A {@code Path} can
 * represent a root, a root and a sequence of names, or simply one or more name
 * elements. It defines the {@link #getName() getName}, {@link #getParent
 * getParent}, {@link #getRoot getRoot}, and {@link #subpath subpath} methods
 * to access the components or a subsequence of its name elements.
 *
 * <p> In addition to accessing the components of a path, a {@code Path} also
 * defines {@link #resolve(Path) resolve} and {@link #relativize relativize}
 * operations. Paths can also be {@link #compareTo compared}, and tested
 * against each other using using the {@link #startsWith startsWith} and {@link
 * #endsWith endWith} methods.
 *
 * <h4>File operations</h4>
 *
 * <p> A {@code Path} is either <em>absolute</em> or <em>relative</em>. An
 * absolute path is complete in that does not need to be combined with another
 * path in order to locate a file. All operations on relative paths are first
 * resolved against a file system's default directory as if by invoking the
 * {@link #toAbsolutePath toAbsolutePath} method.
 *
 * <p> In addition to the operations defined by the {@link FileRef} interface,
 * this class defines the following operations:
 *
 * <ul>
 *   <li><p> Files may be {@link #createFile(FileAttribute[]) created}, or
 *     directories may be {@link #createDirectory(FileAttribute[]) created}.
 *     </p></li>
 *   <li><p> Directories can be {@link #newDirectoryStream opened} so as to
 *      iterate over the entries in the directory. </p></li>
 *   <li><p> Files can be {@link #copyTo(Path,CopyOption[]) copied} or
 *     {@link #moveTo(Path,CopyOption[]) moved}. </p></li>
 *   <li><p> Symbolic-links may be {@link #createSymbolicLink created}, or the
 *     target of a link may be {@link #readSymbolicLink read}. </p></li>
 *   <li><p> {@link #newInputStream InputStream} or {@link #newOutputStream
 *     OutputStream} streams can be created to allow for interoperation with the
 *     <a href="../../../java/io/package-summary.html">{@code java.io}</a> package
 *     where required. </li></p>
 *   <li><p> The {@link #toRealPath real} path of an existing file may be
 *     obtained. </li></p>
 * </ul>
 *
 * <p> This class implements {@link Watchable} interface so that a directory
 * located by a path can be {@link #register registered} with a {@link WatchService}.
 * and entries in the directory watched.
 *
 * <h4>File attributes</h4>
 *
 * The <a href="attribute/package-summary.html">{@code java.nio.file.attribute}</a>
 * package provides access to file attributes or <em>meta-data</em> associated
 * with files. The {@link Attributes Attributes} class defines methods that
 * operate on or return file attributes. For example, the file type, size,
 * timestamps, and other <em>basic</em> meta-data are obtained, in bulk, by
 * invoking the {@link Attributes#readBasicFileAttributes
 * Attributes.readBasicFileAttributes} method:
 * <pre>
 *     Path file = ...
 *     BasicFileAttributes attrs = Attributes.readBasicFileAttributes(file);
 * </pre>
 *
 * <a name="interop"><h4>Interoperability</h4></a>
 *
 * <p> Paths created by file systems associated with the default {@link
 * java.nio.file.spi.FileSystemProvider provider} are generally interoperable
 * with the {@link java.io.File java.io.File} class. Paths created by other
 * providers are unlikely to be interoperable with the abstract path names
 * represented by {@code java.io.File}. The {@link java.io.File#toPath
 * File.toPath} method may be used to obtain a {@code Path} from the abstract
 * path name represented by a {@code java.io.File java.io.File} object. The
 * resulting {@code Path} can be used to operate on the same file as the {@code
 * java.io.File} object.
 *
 * <p> Path objects created by file systems associated with the default
 * provider are interoperable with objects created by other file systems created
 * by the same provider. Path objects created by file systems associated with
 * other providers may not be interoperable with other file systems created by
 * the same provider. The reasons for this are provider specific.
 *
 * <h4>Concurrency</h4></a>
 *
 * <p> Instances of this class are immutable and safe for use by multiple concurrent
 * threads.
 *
 * @since 1.7
 */

public abstract class Path
    implements FileRef, Comparable<Path>, Iterable<Path>, Watchable
{
    /**
     * Initializes a new instance of this class.
     */
    protected Path() { }

    /**
     * Returns the file system that created this object.
     *
     * @return  the file system that created this object
     */
    public abstract FileSystem getFileSystem();

    /**
     * Tells whether or not this path is absolute.
     *
     * <p> An absolute path is complete in that it doesn't need to be
     * combined with other path information in order to locate a file.
     *
     * @return  {@code true} if, and only if, this path is absolute
     */
    public abstract boolean isAbsolute();

    /**
     * Returns the root component of this path as a {@code Path} object,
     * or {@code null} if this path does not have a root component.
     *
     * @return  a path representing the root component of this path,
     *          or {@code null}
     */
    public abstract Path getRoot();

    /**
     * Returns the name of the file or directory denoted by this path. The
     * file name is the <em>farthest</em> element from the root in the directory
     * hierarchy.
     *
     * @return  a path representing the name of the file or directory, or
     *          {@code null} if this path has zero elements
     */
    public abstract Path getName();

    /**
     * Returns the <em>parent path</em>, or {@code null} if this path does not
     * have a parent.
     *
     * <p> The parent of this path object consists of this path's root
     * component, if any, and each element in the path except for the
     * <em>farthest</em> from the root in the directory hierarchy. This method
     * does not access the file system; the path or its parent may not exist.
     * Furthermore, this method does not eliminate special names such as "."
     * and ".." that may be used in some implementations. On UNIX for example,
     * the parent of "{@code /a/b/c}" is "{@code /a/b}", and the parent of
     * {@code "x/y/.}" is "{@code x/y}". This method may be used with the {@link
     * #normalize normalize} method, to eliminate redundant names, for cases where
     * <em>shell-like</em> navigation is required.
     *
     * <p> If this path has one or more elements, and no root component, then
     * this method is equivalent to evaluating the expression:
     * <blockquote><pre>
     * subpath(0,&nbsp;getNameCount()-1);
     * </pre></blockquote>
     *
     * @return  a path representing the path's parent
     */
    public abstract Path getParent();

    /**
     * Returns the number of name elements in the path.
     *
     * @return  the number of elements in the path, or {@code 0} if this path
     *          only represents a root component
     */
    public abstract int getNameCount();

   /**
     * Returns a name element of this path.
     *
     * <p> The {@code index} parameter is the index of the name element to return.
     * The element that is <em>closest</em> to the root in the directory hierarchy
     * has index {@code 0}. The element that is <em>farthest</em> from the root
     * has index {@link #getNameCount count}{@code -1}.
     *
     * @param   index
     *          the index of the element
     *
     * @return  the name element
     *
     * @throws  IllegalArgumentException
     *          if {@code index} is negative, {@code index} is greater than or
     *          equal to the number of elements, or this path has zero name
     *          elements
     */
    public abstract Path getName(int index);

    /**
     * Returns a relative {@code Path} that is a subsequence of the name
     * elements of this path.
     *
     * <p> The {@code beginIndex} and {@code endIndex} parameters specify the
     * subsequence of name elements. The name that is <em>closest</em> to the root
     * in the directory hierarchy has index {@code 0}. The name that is
     * <em>farthest</em> from the root has index {@link #getNameCount
     * count}{@code -1}. The returned {@code Path} object has the name elements
     * that begin at {@code beginIndex} and extend to the element at index {@code
     * endIndex-1}.
     *
     * @param   beginIndex
     *          the index of the first element, inclusive
     * @param   endIndex
     *          the index of the last element, exclusive
     *
     * @return  a new {@code Path} object that is a subsequence of the name
     *          elements in this {@code Path}
     *
     * @throws  IllegalArgumentException
     *          if {@code beginIndex} is negative, or greater than or equal to
     *          the number of elements. If {@code endIndex} is less than or
     *          equal to {@code beginIndex}, or larger than the number of elements.
     */
    public abstract Path subpath(int beginIndex, int endIndex);

    /**
     * Tests if this path starts with the given path.
     *
     * <p> This path <em>starts</em> with the given path if this path's root
     * component <em>starts</em> with the root component of the given path,
     * and this path starts with the same name elements as the given path.
     * If the given path has more name elements than this path then {@code false}
     * is returned.
     *
     * <p> Whether or not the root component of this path starts with the root
     * component of the given path is file system specific. If this path does
     * not have a root component and the given path has a root component then
     * this path does not start with the given path.
     *
     * @param   other
     *          the given path
     *
     * @return  {@code true} if this path starts with the given path; otherwise
     *          {@code false}
     */
    public abstract boolean startsWith(Path other);

    /**
     * Tests if this path ends with the given path.
     *
     * <p> If the given path has <em>N</em> elements, and no root component,
     * and this path has <em>N</em> or more elements, then this path ends with
     * the given path if the last <em>N</em> elements of each path, starting at
     * the element farthest from the root, are equal.
     *
     * <p> If the given path has a root component then this path ends with the
     * given path if the root component of this path <em>ends with</em> the root
     * component of the given path, and the corresponding elements of both paths
     * are equal. Whether or not the root component of this path ends with the
     * root component of the given path is file system specific. If this path
     * does not have a root component and the given path has a root component
     * then this path does not end with the given path.
     *
     * @param   other
     *          the given path
     *
     * @return  {@code true} if this path ends with the given path; otherwise
     *          {@code false}
     */
    public abstract boolean endsWith(Path other);

    /**
     * Returns a path that is this path with redundant name elements eliminated.
     *
     * <p> The precise definition of this method is implementation dependent but
     * in general it derives from this path, a path that does not contain
     * <em>redundant</em> name elements. In many file systems, the "{@code .}"
     * and "{@code ..}" are special names used to indicate the current directory
     * and parent directory. In such file systems all occurrences of "{@code .}"
     * are considered redundant. If a "{@code ..}" is preceded by a
     * non-"{@code ..}" name then both names are considered redundant (the
     * process to identify such names is repeated until is it no longer
     * applicable).
     *
     * <p> This method does not access the file system; the path may not locate
     * a file that exists. Eliminating "{@code ..}" and a preceding name from a
     * path may result in the path that locates a different file than the original
     * path. This can arise when the preceding name is a symbolic link.
     *
     * @return  the resulting path, or this path if it does not contain
     *          redundant name elements, or {@code null} if this path does not
     *          have a root component and all name elements are redundant
     *
     * @see #getParent
     * @see #toRealPath
     */
    public abstract Path normalize();

    // -- resolution and relativization --

    /**
     * Resolve the given path against this path.
     *
     * <p> If the {@code other} parameter is an {@link #isAbsolute() absolute}
     * path then this method trivially returns {@code other}. If {@code other}
     * is {@code null} then this path is returned. Otherwise this method
     * considers this path to be a directory and resolves the given path
     * against this path. In the simplest case, the given path does not have
     * a {@link #getRoot root} component, in which case this method <em>joins</em>
     * the given path to this path and returns a resulting path that {@link
     * #endsWith ends} with the given path. Where the given path has a root
     * component then resolution is highly implementation dependent and therefore
     * unspecified.
     *
     * @param   other
     *          the path to resolve against this path; can be {@code null}
     *
     * @return  the resulting path
     *
     * @see #relativize
     */
    public abstract Path resolve(Path other);

    /**
     * Converts a given path string to a {@code Path} and resolves it against
     * this {@code Path} in exactly the manner specified by the {@link
     * #resolve(Path) resolve} method.
     *
     * @param   other
     *          the path string to resolve against this path
     *
     * @return  the resulting path
     *
     * @throws  InvalidPathException
     *          If the path string cannot be converted to a Path.
     *
     * @see FileSystem#getPath
     */
    public abstract Path resolve(String other);

    /**
     * Constructs a relative path between this path and a given path.
     *
     * <p> Relativization is the inverse of {@link #resolve(Path) resolution}.
     * This method attempts to construct a {@link #isAbsolute relative} path
     * that when {@link #resolve(Path) resolved} against this path, yields a
     * path that locates the same file as the given path. For example, on UNIX,
     * if this path is {@code "/a/b"} and the given path is {@code "/a/b/c/d"}
     * then the resulting relative path would be {@code "c/d"}. Where this
     * path and the given path do not have a {@link #getRoot root} component,
     * then a relative path can be constructed. A relative path cannot be
     * constructed if only one of the paths have a root component. Where both
     * paths have a root component then it is implementation dependent if a
     * relative path can be constructed. If this path and the given path are
     * {@link #equals equal} then {@code null} is returned.
     *
     * <p> For any two paths <i>p</i> and <i>q</i>, where <i>q</i> does not have
     * a root component,
     * <blockquote>
     *   <i>p</i><tt>.relativize(</tt><i>p</i><tt>.resolve(</tt><i>q</i><tt>)).equals(</tt><i>q</i><tt>)</tt>
     * </blockquote>
     *
     * <p> When symbolic-links are supported, then whether the resulting path,
     * when resolved against this path, yields a path that can be used to locate
     * the {@link #isSameFile same} file as {@code other} is implementation
     * dependent. For example, if this path is  {@code "/a/b"} and the given
     * path is {@code "/a/x"} then the resulting relative path may be {@code
     * "../x"}. If {@code "b"} is a symbolic-link then is implementation
     * dependent if {@code "a/b/../x"} would locate the same file as {@code "/a/x"}.
     *
     * @param   other
     *          the resulting path
     *
     * @return  the resulting relative path, or {@code null} if both paths are
     *          equal
     *
     * @throws  IllegalArgumentException
     *          if {@code other} is not a {@code Path} that can be relativized
     *          against this path
     */
    public abstract Path relativize(Path other);

    // -- file operations --

    /**
     * Deletes the file located by this path.
     *
     * <p> The {@code failIfNotExists} parameter determines how the method
     * behaves when the file does not exist. When {@code true}, and the file
     * does not exist, then the method fails. When {@code false} then the method
     * does not fail.
     *
     * <p> As with the {@link FileRef#delete delete()} method, an implementation
     * may require to examine the file to determine if the file is a directory.
     * Consequently this method may not be atomic with respect to other file
     * system operations.  If the file is a symbolic-link then the link is
     * deleted and not the final target of the link.
     *
     * <p> If the file is a directory then the directory must be empty. In some
     * implementations a directory has entries for special files or links that
     * are created when the directory is created. In such implementations a
     * directory is considered empty when only the special entries exist.
     *
     * <p> On some operating systems it may not be possible to remove a file when
     * it is open and in use by this Java virtual machine or other programs.
     *
     * @param   failIfNotExists
     *          {@code true} if the method should fail when the file does not
     *          exist
     *
     * @throws  NoSuchFileException
     *          if the value of the {@code failIfNotExists} is {@code true} and
     *          the file does not exist <i>(optional specific exception)</i>
     * @throws  DirectoryNotEmptyException
     *          if the file is a directory and could not otherwise be deleted
     *          because the directory is not empty <i>(optional specific
     *          exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkDelete(String)} method
     *          is invoked to check delete access to the file.
     */
    public abstract void delete(boolean failIfNotExists) throws IOException;

    /**
     * Creates a symbolic link to a target <i>(optional operation)</i>.
     *
     * <p> The {@code target} parameter is the target of the link. It may be an
     * {@link Path#isAbsolute absolute} or relative path and may not exist. When
     * the target is a relative path then file system operations on the resulting
     * link are relative to the path of the link.
     *
     * <p> The {@code attrs} parameter is an optional array of {@link FileAttribute
     * attributes} to set atomically when creating the link. Each attribute is
     * identified by its {@link FileAttribute#name name}. If more than one attribute
     * of the same name is included in the array then all but the last occurrence
     * is ignored.
     *
     * <p> Where symbolic links are supported, but the underlying {@link FileStore}
     * does not support symbolic links, then this may fail with an {@link
     * IOException}. Additionally, some operating systems may require that the
     * Java virtual machine be started with implementation specific privileges to
     * create symbolic links, in which case this method may throw {@code IOException}.
     *
     * @param   target
     *          the target of the link
     * @param   attrs
     *          the array of attributes to set atomically when creating the
     *          symbolic link
     *
     * @return  this path
     *
     * @throws  UnsupportedOperationException
     *          if the implementation does not support symbolic links or the
     *          array contains an attribute that cannot be set atomically when
     *          creating the symbolic link
     * @throws  FileAlreadyExistsException
     *          if a file with the name already exists <i>(optional specific
     *          exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager
     *          is installed, it denies {@link LinkPermission}<tt>("symbolic")</tt>
     *          or its {@link SecurityManager#checkWrite(String) checkWrite}
     *          method denies write access to the path of the symbolic link.
     */
    public abstract Path createSymbolicLink(Path target, FileAttribute<?>... attrs)
        throws IOException;

    /**
     * Creates a new link (directory entry) for an existing file <i>(optional
     * operation)</i>.
     *
     * <p> This path locates the directory entry to create. The {@code existing}
     * parameter is the path to an existing file. This method creates a new
     * directory entry for the file so that it can be accessed using this path.
     * On some file systems this is known as creating a "hard link". Whether the
     * file attributes are maintained for the file or for each directory entry
     * is file system specific and therefore not specified. Typically, a file
     * system requires that all links (directory entries) for a file be on the
     * same file system. Furthermore, on some platforms, the Java virtual machine
     * may require to be started with implementation specific privileges to
     * create hard links or to create links to directories.
     *
     * @param   existing
     *          a reference to an existing file
     *
     * @return  this path
     *
     * @throws  UnsupportedOperationException
     *          if the implementation does not support adding an existing file
     *          to a directory
     * @throws  FileAlreadyExistsException
     *          if the entry could not otherwise be created because a file of
     *          that name already exists <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager
     *          is installed, it denies {@link LinkPermission}<tt>("hard")</tt>
     *          or its {@link SecurityManager#checkWrite(String) checkWrite}
     *          method denies write access to both this path and the path of the
     *          existing file.
     *
     * @see BasicFileAttributes#linkCount
     */
    public abstract Path createLink(Path existing) throws IOException;

    /**
     * Reads the target of a symbolic link <i>(optional operation)</i>.
     *
     * <p> If the file system supports <a href="package-summary.html#links">symbolic
     * links</a> then this method is used read the target of the link, failing
     * if the file is not a link. The target of the link need not exist. The
     * returned {@code Path} object will be associated with the same file
     * system as this {@code Path}.
     *
     * @return  a {@code Path} object representing the target of the link
     *
     * @throws  UnsupportedOperationException
     *          if the implementation does not support symbolic links
     * @throws  NotLinkException
     *          if the target could otherwise not be read because the file
     *          is not a link <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager
     *          is installed, it checks that {@code FilePermission} has been
     *          granted with the "{@code readlink}" action to read the link.
     */
    public abstract Path readSymbolicLink() throws IOException;

    /**
     * Returns a URI to represent this path.
     *
     * <p> This method constructs a hierarchical {@link URI} that is absolute
     * with a non-empty path component. Its {@link URI#getScheme() scheme} is
     * equal to the URI scheme that identifies the provider. The exact form of
     * the other URI components is highly provider dependent. In particular, it
     * is implementation dependent if its query, fragment, and authority
     * components are defined or undefined.
     *
     * <p> For the default provider the {@link URI#getPath() path} component
     * will represent the {@link #toAbsolutePath absolute} path; the query,
     * fragment components are undefined. Whether the authority component is
     * defined or not is implementation dependent. There is no guarantee that
     * the {@code URI} may be used to construct a {@link java.io.File java.io.File}.
     * In particular, if this path represents a Universal Naming Convention (UNC)
     * path, then the UNC server name may be encoded in the authority component
     * of the resulting URI. In the case of the default provider, and the file
     * exists, and it can be determined that the file is a directory, then the
     * resulting {@code URI} will end with a slash.
     *
     * <p> The default provider provides a similar <em>round-trip</em> guarantee
     * to the {@link java.io.File} class. For a given {@code Path} <i>p</i> it
     * is guaranteed that
     * <blockquote><tt>
     * {@link Paths#get(URI) Paths.get}(</tt><i>p</i><tt>.toUri()).equals(</tt><i>p</i>
     * <tt>.{@link #toAbsolutePath() toAbsolutePath}())</tt>
     * </blockquote>
     * so long as the original {@code Path}, the {@code URI}, and the new {@code
     * Path} are all created in (possibly different invocations of) the same
     * Java virtual machine. Whether other providers make any guarantees is
     * provider specific and therefore unspecified.
     *
     * <p> When a file system is constructed to access the contents of a file
     * as a file system then it is highly implementation specific if the returned
     * URI represents the given path in the file system or it represents a
     * <em>compound</em> URI that encodes the URI of the enclosing file system.
     * A format for compound URIs is not defined in this release; such a scheme
     * may be added in a future release.
     *
     * @return  an absolute, hierarchical URI with a non-empty path component
     *
     * @throws  IOError
     *          if an I/O error occurs obtaining the absolute path, or where a
     *          file system is constructed to access the contents of a file as
     *          a file system, and the URI of the enclosing file system cannot be
     *          obtained
     *
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager
     *          is installed, the {@link #toAbsolutePath toAbsolutePath} method
     *          throws a security exception.
     */
    public abstract URI toUri();

    /**
     * Returns a {@code Path} object representing the absolute path of this
     * path.
     *
     * <p> If this path is already {@link Path#isAbsolute absolute} then this
     * method simply returns this path. Otherwise, this method resolves the path
     * in an implementation dependent manner, typically by resolving the path
     * against a file system default directory. Depending on the implementation,
     * this method may throw an I/O error if the file system is not accessible.
     *
     * @return  a {@code Path} object representing the absolute path
     *
     * @throws  IOError
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager
     *          is installed, its {@link SecurityManager#checkPropertyAccess(String)
     *          checkPropertyAccess} method is invoked to check access to the
     *          system property {@code user.dir}
     */
    public abstract Path toAbsolutePath();

    /**
     * Returns the <em>real</em> path of an existing file.
     *
     * <p> The precise definition of this method is implementation dependent but
     * in general it derives from this path, an {@link #isAbsolute absolute}
     * path that locates the {@link #isSameFile same} file as this path, but
     * with name elements that represent the actual name of the directories
     * and the file. For example, where filename comparisons on a file system
     * are case insensitive then the name elements represent the names in their
     * actual case. Additionally, the resulting path has redundant name
     * elements removed.
     *
     * <p> If this path is relative then its absolute path is first obtained,
     * as if by invoking the {@link #toAbsolutePath toAbsolutePath} method.
     *
     * <p> The {@code resolveLinks} parameter specifies if symbolic links
     * should be resolved. This parameter is ignored when symbolic links are
     * not supported. Where supported, and the parameter has the value {@code
     * true} then symbolic links are resolved to their final target. Where the
     * parameter has the value {@code false} then this method does not resolve
     * symbolic links. Some implementations allow special names such as
     * "{@code ..}" to refer to the parent directory. When deriving the <em>real
     * path</em>, and a "{@code ..}" (or equivalent) is preceded by a
     * non-"{@code ..}" name then an implementation will typically causes both
     * names to be removed. When not resolving symbolic links and the preceding
     * name is a symbolic link then the names are only removed if it guaranteed
     * that the resulting path will locate the same file as this path.
     *
     * @return  an absolute path represent the <em>real</em> path of the file
     *          located by this object
     *
     * @throws  IOException
     *          if the file does not exist or an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager
     *          is installed, its {@link SecurityManager#checkRead(String) checkRead}
     *          method is invoked to check read access to the file, and where
     *          this path is not absolute, its {@link SecurityManager#checkPropertyAccess(String)
     *          checkPropertyAccess} method is invoked to check access to the
     *          system property {@code user.dir}
     */
    public abstract Path toRealPath(boolean resolveLinks) throws IOException;

    /**
     * Copy the file located by this path to a target location.
     *
     * <p> This method copies the file located by this {@code Path} to the
     * target location with the {@code options} parameter specifying how the
     * copy is performed. By default, the copy fails if the target file already
     * exists, except if the source and target are the {@link #isSameFile same}
     * file, in which case this method has no effect. File attributes are not
     * required to be copied to the target file. If symbolic links are supported,
     * and the file is a link, then the final target of the link is copied. If
     * the file is a directory then it creates an empty directory in the target
     * location (entries in the directory are not copied). This method can be
     * used with the {@link Files#walkFileTree Files.walkFileTree} utility
     * method to copy a directory and all entries in the directory, or an entire
     * <i>file-tree</i> where required.
     *
     * <p> The {@code options} parameter is an array of options and may contain
     * any of the following:
     *
     * <table border=1 cellpadding=5 summary="">
     * <tr> <th>Option</th> <th>Description</th> </tr>
     * <tr>
     *   <td> {@link StandardCopyOption#REPLACE_EXISTING REPLACE_EXISTING} </td>
     *   <td> If the target file exists, then the target file is replaced if it
     *     is not a non-empty directory. If the target file exists and is a
     *     symbolic-link then the symbolic-link is replaced (not the target of
     *     the link. </td>
     * </tr>
     * <tr>
     *   <td> {@link StandardCopyOption#COPY_ATTRIBUTES COPY_ATTRIBUTES} </td>
     *   <td> Attempts to copy the file attributes associated with this file to
     *     the target file. The exact file attributes that are copied is platform
     *     and file system dependent and therefore unspecified. Minimally, the
     *     {@link BasicFileAttributes#lastModifiedTime last-modified-time} is
     *     copied to the target file. </td>
     * </tr>
     * <tr>
     *   <td> {@link LinkOption#NOFOLLOW_LINKS NOFOLLOW_LINKS} </td>
     *   <td> Symbolic-links are not followed. If the file, located by this path,
     *     is a symbolic-link then the link is copied rather than the target of
     *     the link. It is implementation specific if file attributes can be
     *     copied to the new link. In other words, the {@code COPY_ATTRIBUTES}
     *     option may be ignored when copying a link. </td>
     * </tr>
     * </table>
     *
     * <p> An implementation of this interface may support additional
     * implementation specific options.
     *
     * <p> Copying a file is not an atomic operation. If an {@link IOException}
     * is thrown then it possible that the target file is incomplete or some of
     * its file attributes have not been copied from the source file. When the
     * {@code REPLACE_EXISTING} option is specified and the target file exists,
     * then the target file is replaced. The check for the existence of the file
     * and the creation of the new file may not be atomic with respect to other
     * file system activities.
     *
     * @param   target
     *          the target location
     * @param   options
     *          options specifying how the copy should be done
     *
     * @return  the target
     *
     * @throws  UnsupportedOperationException
     *          if the array contains a copy option that is not supported
     * @throws  FileAlreadyExistsException
     *          if the target file exists and cannot be replaced because the
     *          {@code REPLACE_EXISTING} option is not specified, or the target
     *          file is a non-empty directory <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkRead(String) checkRead}
     *          method is invoked to check read access to the source file, the
     *          {@link SecurityManager#checkWrite(String) checkWrite} is invoked
     *          to check write access to the target file. If a symbolic link is
     *          copied the security manager is invoked to check {@link
     *          LinkPermission}{@code ("symbolic")}.
     */
    public abstract Path copyTo(Path target, CopyOption... options)
        throws IOException;

    /**
     * Move or rename the file located by this path to a target location.
     *
     * <p> By default, this method attempts to move the file to the target
     * location, failing if the target file exists except if the source and
     * target are the {@link #isSameFile same} file, in which case this method
     * has no effect. If the file is a symbolic link then the link is moved and
     * not the target of the link. This method may be invoked to move an empty
     * directory. In some implementations a directory has entries for special
     * files or links that are created when the directory is created. In such
     * implementations a directory is considered empty when only the special
     * entries exist. When invoked to move a directory that is not empty then the
     * directory is moved if it does not require moving the entries in the directory.
     * For example, renaming a directory on the same {@link FileStore} will usually
     * not require moving the entries in the directory. When moving a directory
     * requires that its entries be moved then this method fails (by throwing
     * an {@code IOException}). To move a <i>file tree</i> may involve copying
     * rather than moving directories and this can be done using the {@link
     * #copyTo copyTo} method in conjunction with the {@link
     * Files#walkFileTree Files.walkFileTree} utility method.
     *
     * <p> The {@code options} parameter is an array of options and may contain
     * any of the following:
     *
     * <table border=1 cellpadding=5 summary="">
     * <tr> <th>Option</th> <th>Description</th> </tr>
     * <tr>
     *   <td> {@link StandardCopyOption#REPLACE_EXISTING REPLACE_EXISTING} </td>
     *   <td> If the target file exists, then the target file is replaced if it
     *     is not a non-empty directory. If the target file exists and is a
     *     symbolic-link then the symbolic-link is replaced and not the target of
     *     the link. </td>
     * </tr>
     * <tr>
     *   <td> {@link StandardCopyOption#ATOMIC_MOVE ATOMIC_MOVE} </td>
     *   <td> The move is performed as an atomic file system operation and all
     *     other options are ignored. If the target file exists then it is
     *     implementation specific if the existing file is replaced or this method
     *     fails by throwing an {@link IOException}. If the move cannot be
     *     performed as an atomic file system operation then {@link
     *     AtomicMoveNotSupportedException} is thrown. This can arise, for
     *     example, when the target location is on a different {@code FileStore}
     *     and would require that the file be copied, or target location is
     *     associated with a different provider to this object. </td>
     * </table>
     *
     * <p> An implementation of this interface may support additional
     * implementation specific options.
     *
     * <p> Where the move requires that the file be copied then the {@link
     * BasicFileAttributes#lastModifiedTime last-modified-time} is copied to the
     * new file. An implementation may also attempt to copy other file
     * attributes but is not required to fail if the file attributes cannot be
     * copied. When the move is performed as a non-atomic operation, and a {@code
     * IOException} is thrown, then the state of the files is not defined. The
     * original file and the target file may both exist, the target file may be
     * incomplete or some of its file attributes may not been copied from the
     * original file.
     *
     * @param   target
     *          the target location
     * @param   options
     *          options specifying how the move should be done
     *
     * @return  the target
     *
     * @throws  UnsupportedOperationException
     *          if the array contains a copy option that is not supported
     * @throws  FileAlreadyExistsException
     *          if the target file exists and cannot be replaced because the
     *          {@code REPLACE_EXISTING} option is not specified, or the target
     *          file is a non-empty directory
     * @throws  AtomicMoveNotSupportedException
     *          if the options array contains the {@code ATOMIC_MOVE} option but
     *          the file cannot be moved as an atomic file system operation.
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkWrite(String) checkWrite}
     *          method is invoked to check write access to both the source and
     *          target file.
     */
    public abstract Path moveTo(Path target, CopyOption... options)
        throws IOException;

    /**
     * Opens the directory referenced by this object, returning a {@code
     * DirectoryStream} to iterate over all entries in the directory. The
     * elements returned by the directory stream's {@link DirectoryStream#iterator
     * iterator} are of type {@code Path}, each one representing an entry in the
     * directory. The {@code Path} objects are obtained as if by {@link
     * #resolve(Path) resolving} the name of the directory entry against this
     * path.
     *
     * <p> The directory stream's {@code close} method should be invoked after
     * iteration is completed so as to free any resources held for the open
     * directory. The {@link Files#withDirectory Files.withDirectory} utility
     * method is useful for cases where a task is performed on each accepted
     * entry in a directory. This method closes the directory when iteration is
     * complete (or an error occurs).
     *
     * <p> When an implementation supports operations on entries in the
     * directory that execute in a race-free manner then the returned directory
     * stream is a {@link SecureDirectoryStream}.
     *
     * @return  a new and open {@code DirectoryStream} object
     *
     * @throws  NotDirectoryException
     *          if the file could not otherwise be opened because it is not
     *          a directory <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkRead(String) checkRead}
     *          method is invoked to check read access to the directory.
     */
    public abstract DirectoryStream<Path> newDirectoryStream()
        throws IOException;

    /**
     * Opens the directory referenced by this object, returning a {@code
     * DirectoryStream} to iterate over the entries in the directory. The
     * elements returned by the directory stream's {@link DirectoryStream#iterator
     * iterator} are of type {@code Path}, each one representing an entry in the
     * directory. The {@code Path} objects are obtained as if by {@link
     * #resolve(Path) resolving} the name of the directory entry against this
     * path. The entries returned by the iterator are filtered by matching the
     * {@code String} representation of their file names against the given
     * <em>globbing</em> pattern.
     *
     * <p> For example, suppose we want to iterate over the files ending with
     * ".java" in a directory:
     * <pre>
     *     Path dir = ...
     *     DirectoryStream&lt;Path&gt; stream = dir.newDirectoryStream("*.java");
     * </pre>
     *
     * <p> The globbing pattern is specified by the {@link
     * FileSystem#getPathMatcher getPathMatcher} method.
     *
     * <p> The directory stream's {@code close} method should be invoked after
     * iteration is completed so as to free any resources held for the open
     * directory.
     *
     * <p> When an implementation supports operations on entries in the
     * directory that execute in a race-free manner then the returned directory
     * stream is a {@link SecureDirectoryStream}.
     *
     * @param   glob
     *          the glob pattern
     *
     * @return  a new and open {@code DirectoryStream} object
     *
     * @throws  java.util.regex.PatternSyntaxException
     *          if the pattern is invalid
     * @throws  UnsupportedOperationException
     *          if the pattern syntax is not known to the implementation
     * @throws  NotDirectoryException
     *          if the file could not otherwise be opened because it is not
     *          a directory <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkRead(String) checkRead}
     *          method is invoked to check read access to the directory.
     */
    public abstract DirectoryStream<Path> newDirectoryStream(String glob)
        throws IOException;

    /**
     * Opens the directory referenced by this object, returning a {@code
     * DirectoryStream} to iterate over the entries in the directory. The
     * elements returned by the directory stream's {@link DirectoryStream#iterator
     * iterator} are of type {@code Path}, each one representing an entry in the
     * directory. The {@code Path} objects are obtained as if by {@link
     * #resolve(Path) resolving} the name of the directory entry against this
     * path. The entries returned by the iterator are filtered by the given
     * {@link DirectoryStream.Filter filter}. The {@link DirectoryStreamFilters}
     * class defines factory methods that create useful filters.
     *
     * <p> The directory stream's {@code close} method should be invoked after
     * iteration is completed so as to free any resources held for the open
     * directory. The {@link Files#withDirectory Files.withDirectory} utility
     * method is useful for cases where a task is performed on each accepted
     * entry in a directory. This method closes the directory when iteration is
     * complete (or an error occurs).
     *
     * <p> Where the filter terminates due to an uncaught error or runtime
     * exception then it propogated to the caller of the iterator's {@link
     * Iterator#hasNext() hasNext} or {@link Iterator#next() next} methods.
     *
     * <p> When an implementation supports operations on entries in the
     * directory that execute in a race-free manner then the returned directory
     * stream is a {@link SecureDirectoryStream}.
     *
     * <p> <b>Usage Example:</b>
     * Suppose we want to iterate over the files in a directory that are
     * larger than 8K.
     * <pre>
     *     DirectoryStream.Filter&lt;Path&gt; filter = new DirectoryStream.Filter&lt;Path&gt;() {
     *         public boolean accept(Path file) {
     *             try {
     *                 long size = Attributes.readBasicFileAttributes(file).size();
     *                 return (size > 8192L);
     *             } catch (IOException e) {
     *                 // failed to get size
     *                 return false;
     *             }
     *         }
     *     };
     *     Path dir = ...
     *     DirectoryStream&lt;Path&gt; stream = dir.newDirectoryStream(filter);
     * </pre>
     * @param   filter
     *          the directory stream filter
     *
     * @return  a new and open {@code DirectoryStream} object
     *
     * @throws  NotDirectoryException
     *          if the file could not otherwise be opened because it is not
     *          a directory <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkRead(String) checkRead}
     *          method is invoked to check read access to the directory.
     */
    public abstract DirectoryStream<Path> newDirectoryStream(DirectoryStream.Filter<? super Path> filter)
        throws IOException;

    /**
     * Creates a new and empty file, failing if the file already exists.
     *
     * <p> This {@code Path} locates the file to create. The check for the
     * existence of the file and the creation of the new file if it does not
     * exist are a single operation that is atomic with respect to all other
     * filesystem activities that might affect the directory.
     *
     * <p> The {@code attrs} parameter is an optional array of {@link FileAttribute
     * file-attributes} to set atomically when creating the file. Each attribute
     * is identified by its {@link FileAttribute#name name}. If more than one
     * attribute of the same name is included in the array then all but the last
     * occurrence is ignored.
     *
     * @param   attrs
     *          an optional list of file attributes to set atomically when
     *          creating the file
     *
     * @return  this path
     *
     * @throws  UnsupportedOperationException
     *          if the array contains an attribute that cannot be set atomically
     *          when creating the file
     * @throws  FileAlreadyExistsException
     *          if a file of that name already exists
     *          <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkWrite(String) checkWrite}
     *          method is invoked to check write access to the new file.
     */
    public abstract Path createFile(FileAttribute<?>... attrs) throws IOException;

    /**
     * Creates a new directory.
     *
     * <p> This {@code Path} locates the directory to create. The check for the
     * existence of the file and the creation of the directory if it does not
     * exist are a single operation that is atomic with respect to all other
     * filesystem activities that might affect the directory.
     *
     * <p> The {@code attrs} parameter is an optional array of {@link FileAttribute
     * file-attributes} to set atomically when creating the directory. Each
     * file attribute is identified by its {@link FileAttribute#name name}. If
     * more than one attribute of the same name is included in the array then all
     * but the last occurrence is ignored.
     *
     * @param   attrs
     *          an optional list of file attributes to set atomically when
     *          creating the directory
     *
     * @return  this path
     *
     * @throws  UnsupportedOperationException
     *          if the array contains an attribute that cannot be set atomically
     *          when creating the directory
     * @throws  FileAlreadyExistsException
     *          if a directory could not otherwise be created because a file of
     *          that name already exists <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkWrite(String) checkWrite}
     *          method is invoked to check write access to the new directory.
     */
    public abstract Path createDirectory(FileAttribute<?>... attrs)
        throws IOException;

    /**
     * Opens or creates a file, returning a seekable byte channel to access the
     * file.
     *
     * <p> The {@code options} parameter determines how the file is opened.
     * The {@link StandardOpenOption#READ READ} and {@link StandardOpenOption#WRITE WRITE}
     * options determine if the file should be opened for reading and/or writing.
     * If neither option (or the {@link StandardOpenOption#APPEND APPEND}
     * option) is contained in the array then the file is opened for reading.
     * By default reading or writing commences at the beginning of the file.
     *
     * <p> In the addition to {@code READ} and {@code WRITE}, the following
     * options may be present:
     *
     * <table border=1 cellpadding=5 summary="">
     * <tr> <th>Option</th> <th>Description</th> </tr>
     * <tr>
     *   <td> {@link StandardOpenOption#APPEND APPEND} </td>
     *   <td> If this option is present then the file is opened for writing and
     *     each invocation of the channel's {@code write} method first advances
     *     the position to the end of the file and then writes the requested
     *     data. Whether the advancement of the position and the writing of the
     *     data are done in a single atomic operation is system-dependent and
     *     therefore unspecified. This option may not be used in conjunction
     *     with the {@code READ} or {@code TRUNCATE_EXISTING} options. </td>
     * </tr>
     * <tr>
     *   <td> {@link StandardOpenOption#TRUNCATE_EXISTING TRUNCATE_EXISTING} </td>
     *   <td> If this option is present then the existing file is truncated to
     *   a size of 0 bytes. This option is ignored when the file is opened only
     *   for reading. </td>
     * </tr>
     * <tr>
     *   <td> {@link StandardOpenOption#CREATE_NEW CREATE_NEW} </td>
     *   <td> If this option is present then a new file is created, failing if
     *   the file already exists or is a symbolic link. When creating a file the
     *   check for the existence of the file and the creation of the file if it
     *   does not exist is atomic with respect to other file system operations.
     *   This option is ignored when the file is opened only for reading. </td>
     * </tr>
     * <tr>
     *   <td > {@link StandardOpenOption#CREATE CREATE} </td>
     *   <td> If this option is present then an existing file is opened if it
     *   exists, otherwise a new file is created. This option is ignored if the
     *   {@code CREATE_NEW} option is also present or the file is opened only
     *   for reading. </td>
     * </tr>
     * <tr>
     *   <td > {@link StandardOpenOption#DELETE_ON_CLOSE DELETE_ON_CLOSE} </td>
     *   <td> When this option is present then the implementation makes a
     *   <em>best effort</em> attempt to delete the file when closed by the
     *   {@link SeekableByteChannel#close close} method. If the {@code close}
     *   method is not invoked then a <em>best effort</em> attempt is made to
     *   delete the file when the Java virtual machine terminates. </td>
     * </tr>
     * <tr>
     *   <td>{@link StandardOpenOption#SPARSE SPARSE} </td>
     *   <td> When creating a new file this option is a <em>hint</em> that the
     *   new file will be sparse. This option is ignored when not creating
     *   a new file. </td>
     * </tr>
     * <tr>
     *   <td> {@link StandardOpenOption#SYNC SYNC} </td>
     *   <td> Requires that every update to the file's content or metadata be
     *   written synchronously to the underlying storage device. (see <a
     *   href="package-summary.html#integrity"> Synchronized I/O file
     *   integrity</a>). </td>
     * <tr>
     * <tr>
     *   <td> {@link StandardOpenOption#DSYNC DSYNC} </td>
     *   <td> Requires that every update to the file's content be written
     *   synchronously to the underlying storage device. (see <a
     *   href="package-summary.html#integrity"> Synchronized I/O file
     *   integrity</a>). </td>
     * </tr>
     * </table>
     *
     * <p> An implementation may also support additional implementation specific
     * options.
     *
     * <p> The {@code attrs} parameter is an optional array of file {@link
     * FileAttribute file-attributes} to set atomically when a new file is created.
     *
     * <p> In the case of the default provider, the returned seekable byte channel
     * is a {@link FileChannel}.
     *
     * <p> <b>Usage Examples:</b>
     * <pre>
     *     Path file = ...
     *
     *     // open file for reading
     *     ReadableByteChannel rbc = file.newByteChannel(EnumSet.of(READ)));
     *
     *     // open file for writing to the end of an existing file, creating
     *     // the file if it doesn't already exist
     *     WritableByteChannel wbc = file.newByteChannel(EnumSet.of(CREATE,APPEND));
     *
     *     // create file with initial permissions, opening it for both reading and writing
     *     FileAttribute&lt;Set&lt;PosixFilePermission&gt;&gt; perms = ...
     *     SeekableByteChannel sbc = file.newByteChannel(EnumSet.of(CREATE_NEW,READ,WRITE), perms);
     * </pre>
     *
     * @param   options
     *          Options specifying how the file is opened
     * @param   attrs
     *          An optional list of file attributes to set atomically when
     *          creating the file
     *
     * @return  a new seekable byte channel
     *
     * @throws  IllegalArgumentException
     *          if the set contains an invalid combination of options
     * @throws  UnsupportedOperationException
     *          if an unsupported open option is specified or the array contains
     *          attributes that cannot be set atomically when creating the file
     * @throws  FileAlreadyExistsException
     *          if a file of that name already exists and the {@link
     *          StandardOpenOption#CREATE_NEW CREATE_NEW} option is specified
     *          <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkRead(String) checkRead}
     *          method is invoked to check read access to the path if the file is
     *          opened for reading. The {@link SecurityManager#checkWrite(String)
     *          checkWrite} method is invoked to check write access to the path
     *          if the file is opened for writing.
     */
    public abstract SeekableByteChannel newByteChannel(Set<? extends OpenOption> options,
                                                       FileAttribute<?>... attrs)
        throws IOException;

    /**
     * Opens or creates a file, returning a seekable byte channel to access the
     * file.
     *
     * <p> This method extends the options defined by the {@code FileRef}
     * interface and to the options specified by the {@link
     * #newByteChannel(Set,FileAttribute[]) newByteChannel} method
     * except that the options are specified by an array. In the case of the
     * default provider, the returned seekable byte channel is a {@link
     * FileChannel}.
     *
     * @param   options
     *          options specifying how the file is opened
     *
     * @return  a new seekable byte channel
     *
     * @throws  IllegalArgumentException
     *          if the set contains an invalid combination of options
     * @throws  UnsupportedOperationException
     *          if an unsupported open option is specified
     * @throws  FileAlreadyExistsException
     *          if a file of that name already exists and the {@link
     *          StandardOpenOption#CREATE_NEW CREATE_NEW} option is specified
     *          <i>(optional specific exception)</i>
     * @throws  IOException                 {@inheritDoc}
     * @throws  SecurityException           {@inheritDoc}
     */
    @Override
    public abstract SeekableByteChannel newByteChannel(OpenOption... options)
        throws IOException;

    /**
     * Opens the file located by this path for reading, returning an input
     * stream to read bytes from the file. The stream will not be buffered, and
     * is not required to support the {@link InputStream#mark mark} or {@link
     * InputStream#reset reset} methods. The stream will be safe for access by
     * multiple concurrent threads. Reading commences at the beginning of the file.
     *
     * @return  an input stream to read bytes from the file
     *
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkRead(String) checkRead}
     *          method is invoked to check read access to the file.
     */
    public abstract InputStream newInputStream() throws IOException;

    /**
     * Opens or creates the file located by this path for writing, returning an
     * output stream to write bytes to the file.
     *
     * <p> This method opens or creates a file in exactly the manner specified
     * by the {@link Path#newByteChannel(Set,FileAttribute[]) newByteChannel}
     * method except that the {@link StandardOpenOption#READ READ} option may not
     * be present in the array of open options. If no open options are present
     * then this method creates a new file for writing or truncates an existing
     * file.
     *
     * <p> The resulting stream will not be buffered. The stream will be safe
     * for access by multiple concurrent threads.
     *
     * <p> <b>Usage Example:</b>
     * Suppose we wish to open a log file for writing so that we append to the
     * file if it already exists, or create it when it doesn't exist.
     * <pre>
     *     Path logfile = ...
     *     OutputStream out = new BufferedOutputStream(logfile.newOutputStream(CREATE, APPEND));
     * </pre>
     *
     * @param   options
     *          options specifying how the file is opened
     *
     * @return  a new seekable byte channel
     *
     * @throws  IllegalArgumentException
     *          if {@code options} contains an invalid combination of options
     * @throws  UnsupportedOperationException
     *          if an unsupported open option is specified
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkWrite(String) checkWrite}
     *          method is invoked to check write access to the file.
     */
    public abstract OutputStream newOutputStream(OpenOption... options)
        throws IOException;

    /**
     * Opens or creates the file located by this path for writing, returning an
     * output stream to write bytes to the file.
     *
     * <p> This method opens or creates a file in exactly the manner specified
     * by the {@link Path#newByteChannel(Set,FileAttribute[]) newByteChannel}
     * method except that {@code options} parameter may not contain the {@link
     * StandardOpenOption#READ READ} option. If no open options are present
     * then this method creates a new file for writing or truncates an existing
     * file.
     *
     * <p> The resulting stream will not be buffered. The stream will be safe
     * for access by multiple concurrent threads.
     *
     * @param   options
     *          options specifying how the file is opened
     * @param   attrs
     *          an optional list of file attributes to set atomically when
     *          creating the file
     *
     * @return  a new output stream
     *
     * @throws  IllegalArgumentException
     *          if the set contains an invalid combination of options
     * @throws  UnsupportedOperationException
     *          if an unsupported open option is specified or the array contains
     *          attributes that cannot be set atomically when creating the file
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkWrite(String) checkWrite}
     *          method is invoked to check write access to the file.
     */
    public abstract OutputStream newOutputStream(Set<? extends OpenOption> options,
                                                 FileAttribute<?>... attrs)
        throws IOException;

    /**
     * Tells whether or not the file located by this object is considered
     * <em>hidden</em>. The exact definition of hidden is platform or provider
     * dependent. On UNIX for example a file is considered to be hidden if its
     * name begins with a period character ('.'). On Windows a file is
     * considered hidden if it isn't a directory and the DOS {@link
     * DosFileAttributes#isHidden hidden} attribute is set.
     *
     * <p> Depending on the implementation this method may require to access
     * the file system to determine if the file is considered hidden.
     *
     * @return  {@code true} if the file is considered hidden
     *
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkRead(String) checkRead}
     *          method is invoked to check read access to the file.
     */
    public abstract boolean isHidden() throws IOException;

    /**
     * Tests whether the file located by this path exists.
     *
     * <p> This convenience method is intended for cases where it is required to
     * take action when it can be confirmed that a file exists. This method simply
     * invokes the {@link #checkAccess checkAccess} method to check if the file
     * exists. If the {@code checkAccess} method succeeds then this method returns
     * {@code true}, otherwise if an {@code IOException} is thrown (because the
     * file doesn't exist or cannot be accessed by this Java virtual machine)
     * then {@code false} is returned.
     *
     * <p> Note that the result of this method is immediately outdated. If this
     * method indicates the file exists then there is no guarantee that a
     * subsequence access will succeed. Care should be taken when using this
     * method in security sensitive applications.
     *
     * @return  {@code true} if the file exists; {@code false} if the file does
     *          not exist or its existence cannot be determined.
     *
     * @throws  SecurityException
     *          In the case of the default provider, the {@link
     *          SecurityManager#checkRead(String)} is invoked to check
     *          read access to the file.
     *
     * @see #notExists
     */
    public abstract boolean exists();

    /**
     * Tests whether the file located by this path does not exist.
     *
     * <p> This convenience method is intended for cases where it is required to
     * take action when it can be confirmed that a file does not exist. This
     * method invokes the {@link #checkAccess checkAccess} method to check if the
     * file exists. If the file does not exist then {@code true} is returned,
     * otherwise the file exists or cannot be accessed by this Java virtual
     * machine and {@code false} is returned.
     *
     * <p> Note that this method is not the complement of the {@link #exists
     * exists} method. Where it is not possible to determine if a file exists
     * or not then both methods return {@code false}. As with the {@code exists}
     * method, the result of this method is immediately outdated. If this
     * method indicates the file does exist then there is no guarantee that a
     * subsequence attempt to create the file will succeed. Care should be taken
     * when using this method in security sensitive applications.
     *
     * @return  {@code true} if the file does not exist; {@code false} if the
     *          file exists or its existence cannot be determined.
     *
     * @throws  SecurityException
     *          In the case of the default provider, the {@link
     *          SecurityManager#checkRead(String)} is invoked to check
     *          read access to the file.
     */
    public abstract boolean notExists();

    // -- watchable --

    /**
     * Registers the file located by this path with a watch service.
     *
     * <p> In this release, this path locates a directory that exists. The
     * directory is registered with the watch service so that entries in the
     * directory can be watched. The {@code events} parameter is an array of
     * events to register and may contain the following events:
     * <ul>
     *   <li>{@link StandardWatchEventKind#ENTRY_CREATE ENTRY_CREATE} -
     *       entry created or moved into the directory</li>
     *   <li>{@link StandardWatchEventKind#ENTRY_DELETE ENTRY_DELETE} -
     *        entry deleted or moved out of the directory</li>
     *   <li>{@link StandardWatchEventKind#ENTRY_MODIFY ENTRY_MODIFY} -
     *        entry in directory was modified</li>
     * </ul>
     *
     * <p> The {@link WatchEvent#context context} for these events is the
     * relative path between the directory located by this path, and the path
     * that locates the directory entry that is created, deleted, or modified.
     *
     * <p> The set of events may include additional implementation specific
     * event that are not defined by the enum {@link StandardWatchEventKind}
     *
     * <p> The {@code modifiers} parameter is an array of <em>modifiers</em>
     * that qualify how the directory is registered. This release does not
     * define any <em>standard</em> modifiers. The array may contain
     * implementation specific modifiers.
     *
     * <p> Where a file is registered with a watch service by means of a symbolic
     * link then it is implementation specific if the watch continues to depend
     * on the existence of the link after it is registered.
     *
     * @param   watcher
     *          the watch service to which this object is to be registered
     * @param   events
     *          the events for which this object should be registered
     * @param   modifiers
     *          the modifiers, if any, that modify how the object is registered
     *
     * @return  a key representing the registration of this object with the
     *          given watch service
     *
     * @throws  UnsupportedOperationException
     *          if unsupported events or modifiers are specified
     * @throws  IllegalArgumentException
     *          if an invalid combination of events or modifiers is specified
     * @throws  ClosedWatchServiceException
     *          if the watch service is closed
     * @throws  NotDirectoryException
     *          if the file is registered to watch the entries in a directory
     *          and the file is not a directory  <i>(optional specific exception)</i>
     * @throws  IOException
     *          if an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkRead(String) checkRead}
     *          method is invoked to check read access to the file.
     */
    @Override
    public abstract WatchKey register(WatchService watcher,
                                      WatchEvent.Kind<?>[] events,
                                      WatchEvent.Modifier... modifiers)
        throws IOException;

    /**
     * Registers the file located by this path with a watch service.
     *
     * <p> An invocation of this method behaves in exactly the same way as the
     * invocation
     * <pre>
     *     watchable.{@link #register(WatchService,WatchEvent.Kind[],WatchEvent.Modifier[]) register}(watcher, events, new WatchEvent.Modifier[0]);
     * </pre>
     *
     * <p> <b>Usage Example:</b>
     * Suppose we wish to register a directory for entry create, delete, and modify
     * events:
     * <pre>
     *     Path dir = ...
     *     WatchService watcher = ...
     *
     *     WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
     * </pre>
     * @param   watcher
     *          The watch service to which this object is to be registered
     * @param   events
     *          The events for which this object should be registered
     *
     * @return  A key representing the registration of this object with the
     *          given watch service
     *
     * @throws  UnsupportedOperationException
     *          If unsupported events are specified
     * @throws  IllegalArgumentException
     *          If an invalid combination of events is specified
     * @throws  ClosedWatchServiceException
     *          If the watch service is closed
     * @throws  NotDirectoryException
     *          If the file is registered to watch the entries in a directory
     *          and the file is not a directory  <i>(optional specific exception)</i>
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default provider, and a security manager is
     *          installed, the {@link SecurityManager#checkRead(String) checkRead}
     *          method is invoked to check read access to the file.
     */
    @Override
    public abstract WatchKey register(WatchService watcher,
                                      WatchEvent.Kind<?>... events)
        throws IOException;

    // -- Iterable --

    /**
     * Returns an iterator over the name elements of this path.
     *
     * <p> The first element returned by the iterator represents the name
     * element that is closest to the root in the directory hierarchy, the
     * second element is the next closest, and so on. The last element returned
     * is the name of the file or directory denoted by this path. The {@link
     * #getRoot root} component, if present, is not returned by the iterator.
     *
     * @return  an iterator over the name elements of this path.
     */
    @Override
    public abstract Iterator<Path> iterator();

    // -- compareTo/equals/hashCode --

    /**
     * Compares two abstract paths lexicographically. The ordering defined by
     * this method is provider specific, and in the case of the default
     * provider, platform specific. This method does not access the file system
     * and neither file is required to exist.
     *
     * @param   other  the path compared to this path.
     *
     * @return  zero if the argument is {@link #equals equal} to this path, a
     *          value less than zero if this path is lexicographically less than
     *          the argument, or a value greater than zero if this path is
     *          lexicographically greater than the argument
     */
    @Override
    public abstract int compareTo(Path other);

    /**
     * Tests this path for equality with the given object.
     *
     * <p> If the given object is not a Path, or is a Path associated with a
     * different provider, then this method immediately returns {@code false}.
     *
     * <p> Whether or not two path are equal depends on the file system
     * implementation. In some cases the paths are compared without regard
     * to case, and others are case sensitive. This method does not access the
     * file system and the file is not required to exist.
     *
     * <p> This method satisfies the general contract of the {@link
     * java.lang.Object#equals(Object) Object.equals} method. </p>
     *
     * @param   other
     *          the object to which this object is to be compared
     *
     * @return  {@code true} if, and only if, the given object is a {@code Path}
     *          that is identical to this {@code Path}
     */
    @Override
    public abstract boolean equals(Object other);

    /**
     * Computes a hash code for this path.
     *
     * <p> The hash code is based upon the components of the path, and
     * satisfies the general contract of the {@link Object#hashCode
     * Object.hashCode} method.
     *
     * @return  the hash-code value for this path
     */
    @Override
    public abstract int hashCode();

    /**
     * Returns the string representation of this path.
     *
     * <p> If this path was created by converting a path string using the
     * {@link FileSystem#getPath getPath} method then the path string returned
     * by this method may differ from the original String used to create the path.
     *
     * <p> The returned path string uses the default name {@link
     * FileSystem#getSeparator separator} to separate names in the path.
     *
     * @return  the string representation of this path
     */
    @Override
    public abstract String toString();
}
