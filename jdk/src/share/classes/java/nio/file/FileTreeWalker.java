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
    private final boolean detectCycles;
    private final LinkOption[] linkOptions;
    private final FileVisitor<? super Path> visitor;

    FileTreeWalker(Set<FileVisitOption> options, FileVisitor<? super Path> visitor) {
        boolean fl = false;
        boolean dc = false;
        for (FileVisitOption option: options) {
            switch (option) {
                case FOLLOW_LINKS  : fl = true; break;
                case DETECT_CYCLES : dc = true; break;
                default:
                    throw new AssertionError("Should not get here");
            }
        }
        this.followLinks = fl;
        this.detectCycles = fl | dc;
        this.linkOptions = (fl) ? new LinkOption[0] :
            new LinkOption[] { LinkOption.NOFOLLOW_LINKS };
        this.visitor = visitor;
    }

    /**
     * Walk file tree starting at the given file
     */
    void walk(Path start, int maxDepth) {
        // don't use attributes of starting file as they may be stale
        if (start instanceof BasicFileAttributesHolder) {
            ((BasicFileAttributesHolder)start).invalidate();
        }
        FileVisitResult result = walk(start,
                                      maxDepth,
                                      new ArrayList<AncestorDirectory>());
        if (result == null) {
            throw new NullPointerException("Visitor returned 'null'");
        }
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
    {
        // depth check
        if (depth-- < 0)
            return FileVisitResult.CONTINUE;

        // if attributes are cached then use them if possible
        BasicFileAttributes attrs = null;
        if (file instanceof BasicFileAttributesHolder) {
            BasicFileAttributes cached = ((BasicFileAttributesHolder)file).get();
            if (!followLinks || !cached.isSymbolicLink())
                attrs = cached;
        }
        IOException exc = null;

        // attempt to get attributes of file. If fails and we are following
        // links then a link target might not exist so get attributes of link
        if (attrs == null) {
            try {
                try {
                    attrs = Attributes.readBasicFileAttributes(file, linkOptions);
                } catch (IOException x1) {
                    if (followLinks) {
                        try {
                            attrs = Attributes
                                .readBasicFileAttributes(file, LinkOption.NOFOLLOW_LINKS);
                        } catch (IOException x2) {
                            exc = x2;
                        }
                    } else {
                        exc = x1;
                    }
                }
            } catch (SecurityException x) {
                return FileVisitResult.CONTINUE;
            }
        }

        // unable to get attributes of file
        if (exc != null) {
            return visitor.visitFileFailed(file, exc);
        }

        // file is not a directory so invoke visitFile method
        if (!attrs.isDirectory()) {
            return visitor.visitFile(file, attrs);
        }

        // check for cycles
        if (detectCycles) {
            Object key = attrs.fileKey();

            // if this directory and ancestor has a file key then we compare
            // them; otherwise we use less efficient isSameFile test.
            for (AncestorDirectory ancestor: ancestors) {
                Object ancestorKey = ancestor.fileKey();
                if (key != null && ancestorKey != null) {
                    if (key.equals(ancestorKey)) {
                        // cycle detected
                        return visitor.visitFile(file, attrs);
                    }
                } else {
                    try {
                        if (file.isSameFile(ancestor.file())) {
                            // cycle detected
                            return visitor.visitFile(file, attrs);
                        }
                    } catch (IOException x) {
                        // ignore
                    } catch (SecurityException x) {
                        // ignore
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
                stream = file.newDirectoryStream();
            } catch (IOException x) {
                return visitor.preVisitDirectoryFailed(file, x);
            } catch (SecurityException x) {
                // ignore, as per spec
                return FileVisitResult.CONTINUE;
            }

            // the exception notified to the postVisitDirectory method
            IOException ioe = null;

            // invoke preVisitDirectory and then visit each entry
            try {
                result = visitor.preVisitDirectory(file);
                if (result != FileVisitResult.CONTINUE) {
                    return result;
                }

                // if an I/O occurs during iteration then a CME is thrown. We
                // need to distinguish this from a CME thrown by the visitor.
                boolean inAction = false;

                try {
                    for (Path entry: stream) {
                        inAction = true;
                        result = walk(entry, depth, ancestors);
                        inAction = false;

                        // returning null will cause NPE to be thrown
                        if (result == null || result == FileVisitResult.TERMINATE)
                            return result;

                        // skip remaining siblings in this directory
                        if (result == FileVisitResult.SKIP_SIBLINGS)
                            break;
                    }
                } catch (ConcurrentModificationException x) {
                    // if CME thrown because the iteration failed then remember
                    // the IOException so that it is notified to postVisitDirectory
                    if (!inAction) {
                        // iteration failed
                        Throwable t = x.getCause();
                        if (t instanceof IOException)
                            ioe = (IOException)t;
                    }
                    if (ioe == null)
                        throw x;
                }
            } finally {
                try {
                    stream.close();
                } catch (IOException x) { }
            }

            // invoke postVisitDirectory last
            return visitor.postVisitDirectory(file, ioe);

        } finally {
            // remove key from trail if doing cycle detection
            if (detectCycles) {
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
