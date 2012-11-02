/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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

package java.nio.file;

import java.nio.file.attribute.*;
import java.io.IOException;
import java.util.*;
import sun.nio.fs.BasicFileAttributesHolder;

/**
 * Simple file tree walker that works in a similar manner to nftw(3C).
 *
 * @see Files#walkFileTree
 */

class FileTreeWalker {
    private final boolean followLinks;
    private final LinkOption[] linkOptions;
    private final FileVisitor<? super Path> visitor;
    private final int maxDepth;

    FileTreeWalker(Set<FileVisitOption> options,
                   FileVisitor<? super Path> visitor,
                   int maxDepth)
    {
        boolean fl = false;
        for (FileVisitOption option: options) {
            // will throw NPE if options contains null
            switch (option) {
                case FOLLOW_LINKS : fl = true; break;
                default:
                    throw new AssertionError("Should not get here");
            }
        }
        this.followLinks = fl;
        this.linkOptions = (fl) ? new LinkOption[0] :
            new LinkOption[] { LinkOption.NOFOLLOW_LINKS };
        this.visitor = visitor;
        this.maxDepth = maxDepth;
    }

    /**
     * Walk file tree starting at the given file
     */
    void walk(Path start) throws IOException {
        FileVisitResult result = walk(start,
                                      0,
                                      new ArrayList<AncestorDirectory>());
        Objects.requireNonNull(result, "FileVisitor returned null");
    }

    /**
     * @param   file
     *          the directory to visit
     * @param   depth
     *          depth remaining
     * @param   ancestors
     *          use when cycle detection is enabled
     */
    private FileVisitResult walk(Path file,
                                 int depth,
                                 List<AncestorDirectory> ancestors)
        throws IOException
    {
        // if attributes are cached then use them if possible
        BasicFileAttributes attrs = null;
        if ((depth > 0) &&
            (file instanceof BasicFileAttributesHolder) &&
            (System.getSecurityManager() == null))
        {
            BasicFileAttributes cached = ((BasicFileAttributesHolder)file).get();
            if (cached != null && (!followLinks || !cached.isSymbolicLink()))
                attrs = cached;
        }
        IOException exc = null;

        // attempt to get attributes of file. If fails and we are following
        // links then a link target might not exist so get attributes of link
        if (attrs == null) {
            try {
                try {
                    attrs = Files.readAttributes(file, BasicFileAttributes.class, linkOptions);
                } catch (IOException x1) {
                    if (followLinks) {
                        try {
                            attrs = Files.readAttributes(file,
                                                         BasicFileAttributes.class,
                                                         LinkOption.NOFOLLOW_LINKS);
                        } catch (IOException x2) {
                            exc = x2;
                        }
                    } else {
                        exc = x1;
                    }
                }
            } catch (SecurityException x) {
                // If access to starting file is denied then SecurityException
                // is thrown, otherwise the file is ignored.
                if (depth == 0)
                    throw x;
                return FileVisitResult.CONTINUE;
            }
        }

        // unable to get attributes of file
        if (exc != null) {
            return visitor.visitFileFailed(file, exc);
        }

        // at maximum depth or file is not a directory
        if (depth >= maxDepth || !attrs.isDirectory()) {
            return visitor.visitFile(file, attrs);
        }

        // check for cycles when following links
        if (followLinks) {
            Object key = attrs.fileKey();

            // if this directory and ancestor has a file key then we compare
            // them; otherwise we use less efficient isSameFile test.
            for (AncestorDirectory ancestor: ancestors) {
                Object ancestorKey = ancestor.fileKey();
                if (key != null && ancestorKey != null) {
                    if (key.equals(ancestorKey)) {
                        // cycle detected
                        return visitor.visitFileFailed(file,
                            new FileSystemLoopException(file.toString()));
                    }
                } else {
                    boolean isSameFile = false;
                    try {
                        isSameFile = Files.isSameFile(file, ancestor.file());
                    } catch (IOException x) {
                        // ignore
                    } catch (SecurityException x) {
                        // ignore
                    }
                    if (isSameFile) {
                        // cycle detected
                        return visitor.visitFileFailed(file,
                            new FileSystemLoopException(file.toString()));
                    }
                }
            }

            ancestors.add(new AncestorDirectory(file, key));
        }

        // visit directory
        try {
            DirectoryStream<Path> stream = null;
            FileVisitResult result;

            // open the directory
            try {
                stream = Files.newDirectoryStream(file);
            } catch (IOException x) {
                return visitor.visitFileFailed(file, x);
            } catch (SecurityException x) {
                // ignore, as per spec
                return FileVisitResult.CONTINUE;
            }

            // the exception notified to the postVisitDirectory method
            IOException ioe = null;

            // invoke preVisitDirectory and then visit each entry
            try {
                result = visitor.preVisitDirectory(file, attrs);
                if (result != FileVisitResult.CONTINUE) {
                    return result;
                }

                try {
                    for (Path entry: stream) {
                        result = walk(entry, depth+1, ancestors);

                        // returning null will cause NPE to be thrown
                        if (result == null || result == FileVisitResult.TERMINATE)
                            return result;

                        // skip remaining siblings in this directory
                        if (result == FileVisitResult.SKIP_SIBLINGS)
                            break;
                    }
                } catch (DirectoryIteratorException e) {
                    // IOException will be notified to postVisitDirectory
                    ioe = e.getCause();
                }
            } finally {
                try {
                    stream.close();
                } catch (IOException e) {
                    // IOException will be notified to postVisitDirectory
                    if (ioe == null)
                        ioe = e;
                }
            }

            // invoke postVisitDirectory last
            return visitor.postVisitDirectory(file, ioe);

        } finally {
            // remove key from trail if doing cycle detection
            if (followLinks) {
                ancestors.remove(ancestors.size()-1);
            }
        }
    }

    private static class AncestorDirectory {
        private final Path dir;
        private final Object key;
        AncestorDirectory(Path dir, Object key) {
            this.dir = dir;
            this.key = key;
        }
        Path file() {
            return dir;
        }
        Object fileKey() {
            return key;
        }
    }
}
