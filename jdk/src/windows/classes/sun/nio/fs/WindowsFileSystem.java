/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.fs;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.*;
import java.util.*;
import java.util.regex.Pattern;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import sun.security.action.GetPropertyAction;

class WindowsFileSystem
    extends FileSystem
{
    private final WindowsFileSystemProvider provider;

    // default directory (is absolute), and default root
    private final String defaultDirectory;
    private final String defaultRoot;

    private final boolean supportsLinks;
    private final boolean supportsStreamEnumeration;

    // package-private
    WindowsFileSystem(WindowsFileSystemProvider provider,
                      String dir)
    {
        this.provider = provider;

        // parse default directory and check it is absolute
        WindowsPathParser.Result result = WindowsPathParser.parse(dir);

        if (result.type() != WindowsPathType.ABSOLUTE)
            throw new AssertionError("Default directory must be absolute/non-UNC");
        this.defaultDirectory = result.path();
        this.defaultRoot = result.root();

        PrivilegedAction<String> pa = new GetPropertyAction("os.version");
        String osversion = AccessController.doPrivileged(pa);
        String[] vers = Util.split(osversion, '.');
        int major = Integer.parseInt(vers[0]);
        int minor = Integer.parseInt(vers[1]);

        // symbolic links available on Vista and newer
        supportsLinks = (major >= 6);

        // enumeration of data streams available on Windows Server 2003 and newer
        supportsStreamEnumeration = (major >= 6) || (major == 5 && minor >= 2);
    }

    // package-private
    String defaultDirectory() {
        return defaultDirectory;
    }

    String defaultRoot() {
        return defaultRoot;
    }

    boolean supportsLinks() {
        return supportsLinks;
    }

    boolean supportsStreamEnumeration() {
        return supportsStreamEnumeration;
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public String getSeparator() {
        return "\\";
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        int drives = 0;
        try {
            drives = WindowsNativeDispatcher.GetLogicalDrives();
        } catch (WindowsException x) {
            // shouldn't happen
            throw new AssertionError(x.getMessage());
        }

        // iterate over roots, ignoring those that the security manager denies
        ArrayList<Path> result = new ArrayList<Path>();
        SecurityManager sm = System.getSecurityManager();
        for (int i = 0; i <= 25; i++) {  // 0->A, 1->B, 2->C...
            if ((drives & (1 << i)) != 0) {
                StringBuilder sb = new StringBuilder(3);
                sb.append((char)('A' + i));
                sb.append(":\\");
                String root = sb.toString();
                if (sm != null) {
                    try {
                        sm.checkRead(root);
                    } catch (SecurityException x) {
                        continue;
                    }
                }
                result.add(WindowsPath.createFromNormalizedPath(this, root));
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Iterator returned by getFileStores method.
     */
    private class FileStoreIterator implements Iterator<FileStore> {
        private final Iterator<Path> roots;
        private FileStore next;

        FileStoreIterator() {
            this.roots = getRootDirectories().iterator();
        }

        private FileStore readNext() {
            assert Thread.holdsLock(this);
            for (;;) {
                if (!roots.hasNext())
                    return null;
                WindowsPath root = (WindowsPath)roots.next();
                // ignore if security manager denies access
                try {
                    root.checkRead();
                } catch (SecurityException x) {
                    continue;
                }
                try {
                    FileStore fs = WindowsFileStore.create(root.toString(), true);
                    if (fs != null)
                        return fs;
                } catch (IOException ioe) {
                    // skip it
                }
            }
        }

        @Override
        public synchronized boolean hasNext() {
            if (next != null)
                return true;
            next = readNext();
            return next != null;
        }

        @Override
        public synchronized FileStore next() {
            if (next == null)
                next = readNext();
            if (next == null) {
                throw new NoSuchElementException();
            } else {
                FileStore result = next;
                next = null;
                return result;
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            try {
                sm.checkPermission(new RuntimePermission("getFileStoreAttributes"));
            } catch (SecurityException se) {
                return Collections.emptyList();
            }
        }
        return new Iterable<FileStore>() {
            public Iterator<FileStore> iterator() {
                return new FileStoreIterator();
            }
        };
    }

    // supported views
    private static final Set<String> supportedFileAttributeViews = Collections
        .unmodifiableSet(new HashSet<String>(Arrays.asList("basic", "dos", "acl", "owner", "user")));

    @Override
    public Set<String> supportedFileAttributeViews() {
        return supportedFileAttributeViews;
    }

    @Override
    public Path getPath(String path) {
        return WindowsPath.parse(this, path);
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        return theLookupService;
    }

    private static final UserPrincipalLookupService theLookupService =
        new UserPrincipalLookupService() {
            @Override
            public UserPrincipal lookupPrincipalByName(String name)
                throws IOException
            {
                return WindowsUserPrincipals.lookup(name);
            }
            @Override
            public GroupPrincipal lookupPrincipalByGroupName(String group)
                throws IOException
            {
                UserPrincipal user = WindowsUserPrincipals.lookup(group);
                if (!(user instanceof GroupPrincipal))
                    throw new UserPrincipalNotFoundException(group);
                return (GroupPrincipal)user;
            }
        };

    @Override
    public PathMatcher getPathMatcher(String syntaxAndInput) {
        int pos = syntaxAndInput.indexOf(':');
        if (pos <= 0 || pos == syntaxAndInput.length())
            throw new IllegalArgumentException();
        String syntax = syntaxAndInput.substring(0, pos);
        String input = syntaxAndInput.substring(pos+1);

        String expr;
        if (syntax.equals(GLOB_SYNTAX)) {
            expr = Globs.toWindowsRegexPattern(input);
        } else {
            if (syntax.equals(REGEX_SYNTAX)) {
                expr = input;
            } else {
                throw new UnsupportedOperationException("Syntax '" + syntax +
                    "' not recognized");
            }
        }

        // match in unicode_case_insensitive
        final Pattern pattern = Pattern.compile(expr,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

        // return matcher
        return new PathMatcher() {
            @Override
            public boolean matches(Path path) {
                return pattern.matcher(path.toString()).matches();
            }
        };
    }
    private static final String GLOB_SYNTAX = "glob";
    private static final String REGEX_SYNTAX = "regex";

    @Override
    public WatchService newWatchService()
        throws IOException
    {
        return new WindowsWatchService(this);
    }
}
