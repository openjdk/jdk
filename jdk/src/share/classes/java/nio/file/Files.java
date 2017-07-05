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

import java.nio.file.spi.FileTypeDetector;
import java.io.IOException;
import java.util.*;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Utility methods for files and directories.
 *
 * @since 1.7
 */

public final class Files {
    private Files() { }

    // lazy loading of default and installed file type detectors
    private static class DefaultFileTypeDetectorHolder {
        static final FileTypeDetector defaultFileTypeDetector =
            sun.nio.fs.DefaultFileTypeDetector.create();
        static final List<FileTypeDetector> installeDetectors =
            loadInstalledDetectors();

        // loads all installed file type detectors
        private static List<FileTypeDetector> loadInstalledDetectors() {
            return AccessController
                .doPrivileged(new PrivilegedAction<List<FileTypeDetector>>() {
                    @Override public List<FileTypeDetector> run() {
                        List<FileTypeDetector> list = new ArrayList<FileTypeDetector>();
                        ServiceLoader<FileTypeDetector> loader = ServiceLoader
                            .load(FileTypeDetector.class, ClassLoader.getSystemClassLoader());
                        for (FileTypeDetector detector: loader) {
                            list.add(detector);
                        }
                        return list;
                }});
        }
    }

    /**
     * Probes the content type of a file.
     *
     * <p> This method uses the installed {@link FileTypeDetector} implementations
     * to probe the given file to determine its content type. Each file type
     * detector's {@link FileTypeDetector#probeContentType probeContentType} is
     * invoked, in turn, to probe the file type. If the file is recognized then
     * the content type is returned. If the file is not recognized by any of the
     * installed file type detectors then a system-default file type detector is
     * invoked to guess the content type.
     *
     * <p> A given invocation of the Java virtual machine maintains a system-wide
     * list of file type detectors. Installed file type detectors are loaded
     * using the service-provider loading facility defined by the {@link ServiceLoader}
     * class. Installed file type detectors are loaded using the system class
     * loader. If the system class loader cannot be found then the extension class
     * loader is used; If the extension class loader cannot be found then the
     * bootstrap class loader is used. File type detectors are typically installed
     * by placing them in a JAR file on the application class path or in the
     * extension directory, the JAR file contains a provider-configuration file
     * named {@code java.nio.file.spi.FileTypeDetector} in the resource directory
     * {@code META-INF/services}, and the file lists one or more fully-qualified
     * names of concrete subclass of {@code FileTypeDetector } that have a zero
     * argument constructor. If the process of locating or instantiating the
     * installed file type detectors fails then an unspecified error is thrown.
     * The ordering that installed providers are located is implementation
     * specific.
     *
     * <p> The return value of this method is the string form of the value of a
     * Multipurpose Internet Mail Extension (MIME) content type as
     * defined by <a href="http://www.ietf.org/rfc/rfc2045.txt"><i>RFC&nbsp;2045:
     * Multipurpose Internet Mail Extensions (MIME) Part One: Format of Internet
     * Message Bodies</i></a>. The string is guaranteed to be parsable according
     * to the grammar in the RFC.
     *
     * @param   file
     *          The file reference
     *
     * @return  The content type of the file, or {@code null} if the content
     *          type cannot be determined
     *
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          If a security manager is installed and it denies an unspecified
     *          permission required by a file type detector implementation.
     *
     * @see DirectoryStreamFilters#newContentTypeFilter
     */
    public static String probeContentType(FileRef file)
        throws IOException
    {
        // try installed file type detectors
        for (FileTypeDetector detector: DefaultFileTypeDetectorHolder.installeDetectors) {
            String result = detector.probeContentType(file);
            if (result != null)
                return result;
        }

        // fallback to default
        return DefaultFileTypeDetectorHolder.defaultFileTypeDetector
            .probeContentType(file);
    }

    /**
     * Invokes a {@link FileAction} for each entry in a directory accepted
     * by a given {@link java.nio.file.DirectoryStream.Filter filter}.
     *
     * <p> This method opens the given directory and invokes the file action's
     * {@link FileAction#invoke invoke} method for each entry accepted by the
     * filter. When iteration is completed then the directory is closed. If the
     * {@link DirectoryStream#close close} method throws an {@code IOException}
     * then it is silently ignored.
     *
     * <p> If the {@code FileAction}'s {@code invoke} method terminates due
     * to an uncaught {@link IOException}, {@code Error} or {@code RuntimeException}
     * then the exception is propagated by this method after closing the
     * directory.
     *
     * @param   dir
     *          The directory
     * @param   filter
     *          The filter
     * @param   action
     *          The {@code FileAction} to invoke for each accepted entry
     *
     * @throws  NotDirectoryException
     *          If the {@code dir} parameter is not a directory <i>(optional
     *          specific exception)</i>
     * @throws  IOException
     *          If an I/O error occurs or the {@code invoke} method terminates
     *          due to an uncaught {@code IOException}
     * @throws  SecurityException
     *          In the case of the default provider, the {@link
     *          SecurityManager#checkRead(String) checkRead} method is invoked
     *          to check read access to the directory.
     */
    public static void withDirectory(Path dir,
                                     DirectoryStream.Filter<? super Path> filter,
                                     FileAction<? super Path> action)
        throws IOException
    {
        // explicit null check required in case directory is empty
        if (action == null)
            throw new NullPointerException();

        DirectoryStream<Path> stream = dir.newDirectoryStream(filter);
        try {
            // set to true when invoking the action so as to distinguish a
            // CME thrown by the iteration from a CME thrown by the invoke
            boolean inAction = false;
            try {
                for (Path entry: stream) {
                    inAction = true;
                    action.invoke(entry);
                    inAction = false;
                }
            } catch (ConcurrentModificationException cme) {
                if (!inAction) {
                    Throwable cause = cme.getCause();
                    if (cause instanceof IOException)
                        throw (IOException)cause;
                }
                throw cme;
            }
        } finally {
            try {
                stream.close();
            } catch (IOException x) { }
        }
    }

    /**
     * Invokes a {@link FileAction} for each entry in a directory with a
     * file name that matches a given pattern.
     *
     * <p> This method opens the given directory and invokes the file action's
     * {@link FileAction#invoke invoke} method for each entry that matches the
     * given pattern. When iteration is completed then the directory is closed.
     * If the {@link DirectoryStream#close close} method throws an {@code
     * IOException} then it is silently ignored.
     *
     * <p> If the {@code FileAction}'s {@code invoke} method terminates due
     * to an uncaught {@link IOException}, {@code Error} or {@code RuntimeException}
     * then the exception is propagated by this method after closing the
     * directory.
     *
     * <p> The globbing pattern language supported by this method is as
     * specified by the {@link FileSystem#getPathMatcher getPathMatcher} method.
     *
     * @param   dir
     *          The directory
     * @param   glob
     *          The globbing pattern
     * @param   action
     *          The {@code FileAction} to invoke for each entry
     *
     * @throws  NotDirectoryException
     *          If the {@code dir} parameter is not a directory <i>(optional
     *          specific exception)</i>
     * @throws  IOException
     *          If an I/O error occurs or the {@code invoke} method terminates
     *          due to an uncaught {@code IOException}
     * @throws  SecurityException
     *          In the case of the default provider, the {@link
     *          SecurityManager#checkRead(String) checkRead} method is invoked
     *          to check read access to the directory.
     */
    public static void withDirectory(Path dir,
                                     String glob,
                                     FileAction<? super Path> action)
        throws IOException
    {
        if (glob == null)
            throw new NullPointerException("'glob' is null");
        final PathMatcher matcher = dir.getFileSystem().getPathMatcher("glob:" + glob);
        DirectoryStream.Filter<Path> filter = new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path entry)  {
                return matcher.matches(entry.getName());
            }
        };
        withDirectory(dir, filter, action);
    }

    /**
     * Invokes a {@link FileAction} for all entries in a directory.
     *
     * <p> This method works as if invoking it were equivalent to evaluating the
     * expression:
     * <blockquote><pre>
     * withDirectory(dir, "*", action)
     * </pre></blockquote>
     *
     * @param   dir
     *          The directory
     * @param   action
     *          The {@code FileAction} to invoke for each entry
     *
     * @throws  NotDirectoryException
     *          If the {@code dir} parameter is not a directory <i>(optional
     *          specific exception)</i>
     * @throws  IOException
     *          If an I/O error occurs or the {@code invoke} method terminates
     *          due to an uncaught {@code IOException}
     * @throws  SecurityException
     *          In the case of the default provider, the {@link
     *          SecurityManager#checkRead(String) checkRead} method is invoked
     *          to check read access to the directory.
     */
    public static void withDirectory(Path dir, FileAction<? super Path> action)
        throws IOException
    {
        withDirectory(dir, "*", action);
    }

    /**
     * Walks a file tree.
     *
     * <p> This method walks a file tree rooted at a given starting file. The
     * file tree traversal is <em>depth-first</em> with the given {@link
     * FileVisitor} invoked for each file encountered. File tree traversal
     * completes when all accessible files in the tree have been visited, a
     * visitor returns a result of {@link FileVisitResult#TERMINATE TERMINATE},
     * or the visitor terminates due to an uncaught {@code Error} or {@code
     * RuntimeException}.
     *
     * <p> For each file encountered this method attempts to gets its {@link
     * java.nio.file.attribute.BasicFileAttributes}. If the file is not a
     * directory then the {@link FileVisitor#visitFile visitFile} method is
     * invoked with the file attributes. If the file attributes cannot be read,
     * due to an I/O exception, then the {@link FileVisitor#visitFileFailed
     * visitFileFailed} method is invoked with the I/O exception.
     *
     * <p> Where the file is a directory, this method attempts to open it by
     * invoking its {@link Path#newDirectoryStream newDirectoryStream} method.
     * Where the directory could not be opened, due to an {@code IOException},
     * then the {@link FileVisitor#preVisitDirectoryFailed preVisitDirectoryFailed}
     * method is invoked with the I/O exception, after which, the file tree walk
     * continues, by default, at the next <em>sibling</em> of the directory.
     *
     * <p> Where the directory is opened successfully, then the entries in the
     * directory, and their <em>descendants</em> are visited. When all entries
     * have been visited, or an I/O error occurs during iteration of the
     * directory, then the directory is closed and the visitor's {@link
     * FileVisitor#postVisitDirectory postVisitDirectory} method is invoked.
     * The file tree walk then continues, by default, at the next <em>sibling</em>
     * of the directory.
     *
     * <p> By default, symbolic links are not automatically followed by this
     * method. If the {@code options} parameter contains the {@link
     * FileVisitOption#FOLLOW_LINKS FOLLOW_LINKS} option then symbolic links are
     * followed. When following links, and the attributes of the target cannot
     * be read, then this method attempts to get the {@code BasicFileAttributes}
     * of the link. If they can be read then the {@code visitFile} method is
     * invoked with the attributes of the link (otherwise the {@code visitFileFailed}
     * method is invoked as specified above).
     *
     * <p> If the {@code options} parameter contains the {@link
     * FileVisitOption#DETECT_CYCLES DETECT_CYCLES} or {@link
     * FileVisitOption#FOLLOW_LINKS FOLLOW_LINKS} options then this method keeps
     * track of directories visited so that cycles can be detected. A cycle
     * arises when there is an entry in a directory that is an ancestor of the
     * directory. Cycle detection is done by recording the {@link
     * java.nio.file.attribute.BasicFileAttributes#fileKey file-key} of directories,
     * or if file keys are not available, by invoking the {@link FileRef#isSameFile
     * isSameFile} method to test if a directory is the same file as an
     * ancestor. When a cycle is detected the {@link FileVisitor#visitFile
     * visitFile} is invoked with the attributes of the directory. The {@link
     * java.nio.file.attribute.BasicFileAttributes#isDirectory isDirectory}
     * method may be used to test if the file is a directory and that a cycle is
     * detected. The {@code preVisitDirectory} and {@code postVisitDirectory}
     * methods are not invoked.
     *
     * <p> The {@code maxDepth} parameter is the maximum number of levels of
     * directories to visit. A value of {@code 0} means that only the starting
     * file is visited, unless denied by the security manager. A value of
     * {@link Integer#MAX_VALUE MAX_VALUE} may be used to indicate that all
     * levels should be visited.
     *
     * <p> If a visitor returns a result of {@code null} then {@code
     * NullPointerException} is thrown.
     *
     * <p> When a security manager is installed and it denies access to a file
     * (or directory), then it is ignored and the visitor is not invoked for
     * that file (or directory).
     *
     * @param   start
     *          The starting file
     * @param   options
     *          Options to configure the traversal
     * @param   maxDepth
     *          The maximum number of directory levels to visit
     * @param   visitor
     *          The file visitor to invoke for each file
     *
     * @throws  IllegalArgumentException
     *          If the {@code maxDepth} parameter is negative
     * @throws  SecurityException
     *          If the security manager denies access to the starting file.
     *          In the case of the default provider, the {@link
     *          SecurityManager#checkRead(String) checkRead} method is invoked
     *          to check read access to the directory.
     */
    public static void walkFileTree(Path start,
                                    Set<FileVisitOption> options,
                                    int maxDepth,
                                    FileVisitor<? super Path> visitor)
    {
        if (maxDepth < 0)
            throw new IllegalArgumentException("'maxDepth' is negative");
        new FileTreeWalker(options, visitor).walk(start, maxDepth);
    }

    /**
     * Walks a file tree.
     *
     * <p> This method works as if invoking it were equivalent to evaluating the
     * expression:
     * <blockquote><pre>
     * walkFileTree(start, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, visitor)
     * </pre></blockquote>
     *
     * @param   start
     *          The starting file
     * @param   visitor
     *          The file visitor to invoke for each file
     *
     * @throws  SecurityException
     *          If the security manager denies access to the starting file.
     *          In the case of the default provider, the {@link
     *          SecurityManager#checkRead(String) checkRead} method is invoked
     *          to check read access to the directory.
     */
    public static void walkFileTree(Path start, FileVisitor<? super Path> visitor) {
        walkFileTree(start,
                     EnumSet.noneOf(FileVisitOption.class),
                     Integer.MAX_VALUE,
                     visitor);
    }
}
