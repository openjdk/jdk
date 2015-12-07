/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.file;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.SourceVersion;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

import com.sun.tools.javac.file.RelativePath.RelativeDirectory;
import com.sun.tools.javac.file.RelativePath.RelativeFile;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

import static java.nio.file.FileVisitOption.FOLLOW_LINKS;

import static javax.tools.StandardLocation.*;

/**
 * This class provides access to the source, class and other files
 * used by the compiler and related tools.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class JavacFileManager extends BaseFileManager implements StandardJavaFileManager {

    @SuppressWarnings("cast")
    public static char[] toArray(CharBuffer buffer) {
        if (buffer.hasArray())
            return ((CharBuffer)buffer.compact().flip()).array();
        else
            return buffer.toString().toCharArray();
    }

    private FSInfo fsInfo;

    private final Set<JavaFileObject.Kind> sourceOrClass =
        EnumSet.of(JavaFileObject.Kind.SOURCE, JavaFileObject.Kind.CLASS);

    protected boolean symbolFileEnabled;

    protected enum SortFiles implements Comparator<Path> {
        FORWARD {
            @Override
            public int compare(Path f1, Path f2) {
                return f1.getFileName().compareTo(f2.getFileName());
            }
        },
        REVERSE {
            @Override
            public int compare(Path f1, Path f2) {
                return -f1.getFileName().compareTo(f2.getFileName());
            }
        }
    }

    protected SortFiles sortFiles;

    /**
     * Register a Context.Factory to create a JavacFileManager.
     */
    public static void preRegister(Context context) {
        context.put(JavaFileManager.class, new Context.Factory<JavaFileManager>() {
            @Override
            public JavaFileManager make(Context c) {
                return new JavacFileManager(c, true, null);
            }
        });
    }

    /**
     * Create a JavacFileManager using a given context, optionally registering
     * it as the JavaFileManager for that context.
     */
    public JavacFileManager(Context context, boolean register, Charset charset) {
        super(charset);
        if (register)
            context.put(JavaFileManager.class, this);
        setContext(context);
    }

    /**
     * Set the context for JavacFileManager.
     */
    @Override
    public void setContext(Context context) {
        super.setContext(context);

        fsInfo = FSInfo.instance(context);

        symbolFileEnabled = !options.isSet("ignore.symbol.file");

        String sf = options.get("sortFiles");
        if (sf != null) {
            sortFiles = (sf.equals("reverse") ? SortFiles.REVERSE : SortFiles.FORWARD);
        }
    }

    /**
     * Set whether or not to use ct.sym as an alternate to rt.jar.
     */
    public void setSymbolFileEnabled(boolean b) {
        symbolFileEnabled = b;
    }

    public boolean isSymbolFileEnabled() {
        return symbolFileEnabled;
    }

    // used by tests
    public JavaFileObject getJavaFileObject(String name) {
        return getJavaFileObjects(name).iterator().next();
    }

    // used by tests
    public JavaFileObject getJavaFileObject(Path file) {
        return getJavaFileObjects(file).iterator().next();
    }

    public JavaFileObject getFileForOutput(String classname,
                                           JavaFileObject.Kind kind,
                                           JavaFileObject sibling)
        throws IOException
    {
        return getJavaFileForOutput(CLASS_OUTPUT, classname, kind, sibling);
    }

    @Override @DefinedBy(Api.COMPILER)
    public Iterable<? extends JavaFileObject> getJavaFileObjectsFromStrings(Iterable<String> names) {
        ListBuffer<Path> paths = new ListBuffer<>();
        for (String name : names)
            paths.append(Paths.get(nullCheck(name)));
        return getJavaFileObjectsFromPaths(paths.toList());
    }

    @Override @DefinedBy(Api.COMPILER)
    public Iterable<? extends JavaFileObject> getJavaFileObjects(String... names) {
        return getJavaFileObjectsFromStrings(Arrays.asList(nullCheck(names)));
    }

    private static boolean isValidName(String name) {
        // Arguably, isValidName should reject keywords (such as in SourceVersion.isName() ),
        // but the set of keywords depends on the source level, and we don't want
        // impls of JavaFileManager to have to be dependent on the source level.
        // Therefore we simply check that the argument is a sequence of identifiers
        // separated by ".".
        for (String s : name.split("\\.", -1)) {
            if (!SourceVersion.isIdentifier(s))
                return false;
        }
        return true;
    }

    private static void validateClassName(String className) {
        if (!isValidName(className))
            throw new IllegalArgumentException("Invalid class name: " + className);
    }

    private static void validatePackageName(String packageName) {
        if (packageName.length() > 0 && !isValidName(packageName))
            throw new IllegalArgumentException("Invalid packageName name: " + packageName);
    }

    public static void testName(String name,
                                boolean isValidPackageName,
                                boolean isValidClassName)
    {
        try {
            validatePackageName(name);
            if (!isValidPackageName)
                throw new AssertionError("Invalid package name accepted: " + name);
            printAscii("Valid package name: \"%s\"", name);
        } catch (IllegalArgumentException e) {
            if (isValidPackageName)
                throw new AssertionError("Valid package name rejected: " + name);
            printAscii("Invalid package name: \"%s\"", name);
        }
        try {
            validateClassName(name);
            if (!isValidClassName)
                throw new AssertionError("Invalid class name accepted: " + name);
            printAscii("Valid class name: \"%s\"", name);
        } catch (IllegalArgumentException e) {
            if (isValidClassName)
                throw new AssertionError("Valid class name rejected: " + name);
            printAscii("Invalid class name: \"%s\"", name);
        }
    }

    private static void printAscii(String format, Object... args) {
        String message;
        try {
            final String ascii = "US-ASCII";
            message = new String(String.format(null, format, args).getBytes(ascii), ascii);
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new AssertionError(ex);
        }
        System.out.println(message);
    }

    /**
     * Insert all files in a subdirectory of the platform image
     * which match fileKinds into resultList.
     */
    private void listJRTImage(RelativeDirectory subdirectory,
                               Set<JavaFileObject.Kind> fileKinds,
                               boolean recurse,
                               ListBuffer<JavaFileObject> resultList) throws IOException {
        JRTIndex.Entry e = getJRTIndex().getEntry(subdirectory);
        if (symbolFileEnabled && e.ctSym.hidden)
            return;
        for (Path file: e.files.values()) {
            if (fileKinds.contains(getKind(file))) {
                JavaFileObject fe
                        = PathFileObject.forJRTPath(JavacFileManager.this, file);
                resultList.append(fe);
            }
        }

        if (recurse) {
            for (RelativeDirectory rd: e.subdirs) {
                listJRTImage(rd, fileKinds, recurse, resultList);
            }
        }
    }

    private synchronized JRTIndex getJRTIndex() {
        if (jrtIndex == null)
            jrtIndex = JRTIndex.getSharedInstance();
        return jrtIndex;
    }

    private JRTIndex jrtIndex;


    /**
     * Insert all files in subdirectory subdirectory of directory directory
     * which match fileKinds into resultList
     */
    private void listDirectory(Path directory, Path realDirectory,
                               RelativeDirectory subdirectory,
                               Set<JavaFileObject.Kind> fileKinds,
                               boolean recurse,
                               ListBuffer<JavaFileObject> resultList) {
        Path d;
        try {
            d = subdirectory.resolveAgainst(directory);
        } catch (InvalidPathException ignore) {
            return;
        }

        if (!Files.exists(d)) {
           return;
        }

        if (!caseMapCheck(d, subdirectory)) {
            return;
        }

        java.util.List<Path> files;
        try (Stream<Path> s = Files.list(d)) {
            files = (sortFiles == null ? s : s.sorted(sortFiles)).collect(Collectors.toList());
        } catch (IOException ignore) {
            return;
        }

        if (realDirectory == null)
            realDirectory = fsInfo.getCanonicalFile(directory);

        for (Path f: files) {
            String fname = f.getFileName().toString();
            if (fname.endsWith("/"))
                fname = fname.substring(0, fname.length() - 1);
            if (Files.isDirectory(f)) {
                if (recurse && SourceVersion.isIdentifier(fname)) {
                    listDirectory(directory, realDirectory,
                                  new RelativeDirectory(subdirectory, fname),
                                  fileKinds,
                                  recurse,
                                  resultList);
                }
            } else {
                if (isValidFile(fname, fileKinds)) {
                    RelativeFile file = new RelativeFile(subdirectory, fname);
                    JavaFileObject fe = PathFileObject.forDirectoryPath(this,
                            file.resolveAgainst(realDirectory), directory, file);
                    resultList.append(fe);
                }
            }
        }
    }

    /**
     * Insert all files in subdirectory subdirectory of archive archivePath
     * which match fileKinds into resultList
     */
    private void listArchive(Path archivePath,
            RelativeDirectory subdirectory,
            Set<JavaFileObject.Kind> fileKinds,
            boolean recurse,
            ListBuffer<JavaFileObject> resultList)
                throws IOException {
        FileSystem fs = getFileSystem(archivePath);
        if (fs == null) {
            return;
        }

        Path containerSubdir = subdirectory.resolveAgainst(fs);
        if (!Files.exists(containerSubdir)) {
            return;
        }

        int maxDepth = (recurse ? Integer.MAX_VALUE : 1);
        Set<FileVisitOption> opts = EnumSet.of(FOLLOW_LINKS);
        Files.walkFileTree(containerSubdir, opts, maxDepth,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (isValid(dir.getFileName())) {
                            return FileVisitResult.CONTINUE;
                        } else {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }

                    boolean isValid(Path fileName) {
                        if (fileName == null) {
                            return true;
                        } else {
                            String name = fileName.toString();
                            if (name.endsWith("/")) {
                                name = name.substring(0, name.length() - 1);
                            }
                            return SourceVersion.isIdentifier(name);
                        }
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isRegularFile() && fileKinds.contains(getKind(file.getFileName().toString()))) {
                            JavaFileObject fe = PathFileObject.forJarPath(
                                    JavacFileManager.this, file, archivePath);
                            resultList.append(fe);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });

    }

    /**
     * container is a directory, a zip file, or a non-existant path.
     * Insert all files in subdirectory subdirectory of container which
     * match fileKinds into resultList
     */
    private void listContainer(Path container,
                               RelativeDirectory subdirectory,
                               Set<JavaFileObject.Kind> fileKinds,
                               boolean recurse,
                               ListBuffer<JavaFileObject> resultList)
            throws IOException {
        // Very temporary and obnoxious interim hack
        if (container.endsWith("bootmodules.jimage")) {
            System.err.println("Warning: reference to bootmodules.jimage replaced by jrt:");
            container = Locations.JRT_MARKER_FILE;
        } else if (container.getFileName().toString().endsWith(".jimage")) {
            System.err.println("Warning: reference to " + container + " ignored");
            return;
        }

        if (container == Locations.JRT_MARKER_FILE) {
            try {
                listJRTImage(subdirectory,
                        fileKinds,
                        recurse,
                        resultList);
            } catch (IOException ex) {
                ex.printStackTrace(System.err);
                log.error("error.reading.file", container, getMessage(ex));
            }
            return;
        }

        if  (fsInfo.isDirectory(container)) {
            listDirectory(container, null,
                          subdirectory,
                          fileKinds,
                          recurse,
                          resultList);
            return;
        }

        if (Files.exists(container)) {
            listArchive(container,
                    subdirectory,
                    fileKinds,
                    recurse,
                    resultList);
        }
    }

    private boolean isValidFile(String s, Set<JavaFileObject.Kind> fileKinds) {
        JavaFileObject.Kind kind = getKind(s);
        return fileKinds.contains(kind);
    }

    private static final boolean fileSystemIsCaseSensitive =
        File.separatorChar == '/';

    /** Hack to make Windows case sensitive. Test whether given path
     *  ends in a string of characters with the same case as given name.
     *  Ignore file separators in both path and name.
     */
    private boolean caseMapCheck(Path f, RelativePath name) {
        if (fileSystemIsCaseSensitive) return true;
        // Note that toRealPath() returns the case-sensitive
        // spelled file name.
        String path;
        char sep;
        try {
            path = f.toRealPath(LinkOption.NOFOLLOW_LINKS).toString();
            sep = f.getFileSystem().getSeparator().charAt(0);
        } catch (IOException ex) {
            return false;
        }
        char[] pcs = path.toCharArray();
        char[] ncs = name.path.toCharArray();
        int i = pcs.length - 1;
        int j = ncs.length - 1;
        while (i >= 0 && j >= 0) {
            while (i >= 0 && pcs[i] == sep) i--;
            while (j >= 0 && ncs[j] == '/') j--;
            if (i >= 0 && j >= 0) {
                if (pcs[i] != ncs[j]) return false;
                i--;
                j--;
            }
        }
        return j < 0;
    }

    private FileSystem getFileSystem(Path path) throws IOException {
        Path realPath = fsInfo.getCanonicalFile(path);
        FileSystem fs = fileSystems.get(realPath);
        if (fs == null) {
            fileSystems.put(realPath, fs = FileSystems.newFileSystem(realPath, null));
        }
        return fs;
    }

    private final Map<Path,FileSystem> fileSystems = new HashMap<>();


    /** Flush any output resources.
     */
    @Override @DefinedBy(Api.COMPILER)
    public void flush() {
        contentCache.clear();
    }

    /**
     * Close the JavaFileManager, releasing resources.
     */
    @Override @DefinedBy(Api.COMPILER)
    public void close() throws IOException {
        for (FileSystem fs: fileSystems.values())
            fs.close();
    }

    @Override @DefinedBy(Api.COMPILER)
    public ClassLoader getClassLoader(Location location) {
        nullCheck(location);
        Iterable<? extends File> path = getLocation(location);
        if (path == null)
            return null;
        ListBuffer<URL> lb = new ListBuffer<>();
        for (File f: path) {
            try {
                lb.append(f.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new AssertionError(e);
            }
        }

        return getClassLoader(lb.toArray(new URL[lb.size()]));
    }

    @Override @DefinedBy(Api.COMPILER)
    public Iterable<JavaFileObject> list(Location location,
                                         String packageName,
                                         Set<JavaFileObject.Kind> kinds,
                                         boolean recurse)
        throws IOException
    {
        // validatePackageName(packageName);
        nullCheck(packageName);
        nullCheck(kinds);

        Iterable<? extends Path> path = getLocationAsPaths(location);
        if (path == null)
            return List.nil();
        RelativeDirectory subdirectory = RelativeDirectory.forPackage(packageName);
        ListBuffer<JavaFileObject> results = new ListBuffer<>();

        for (Path directory : path)
            listContainer(directory, subdirectory, kinds, recurse, results);
        return results.toList();
    }

    @Override @DefinedBy(Api.COMPILER)
    public String inferBinaryName(Location location, JavaFileObject file) {
        Objects.requireNonNull(file);
        Objects.requireNonNull(location);
        // Need to match the path semantics of list(location, ...)
        Iterable<? extends Path> path = getLocationAsPaths(location);
        if (path == null) {
            return null;
        }

        if (file instanceof PathFileObject) {
            return ((PathFileObject) file).inferBinaryName(path);
        } else
            throw new IllegalArgumentException(file.getClass().getName());
    }

    @Override @DefinedBy(Api.COMPILER)
    public boolean isSameFile(FileObject a, FileObject b) {
        nullCheck(a);
        nullCheck(b);
        if (a instanceof PathFileObject && b instanceof PathFileObject)
            return ((PathFileObject) a).isSameFile((PathFileObject) b);
        return a.equals(b);
    }

    @Override @DefinedBy(Api.COMPILER)
    public boolean hasLocation(Location location) {
        return getLocation(location) != null;
    }

    @Override @DefinedBy(Api.COMPILER)
    public JavaFileObject getJavaFileForInput(Location location,
                                              String className,
                                              JavaFileObject.Kind kind)
        throws IOException
    {
        nullCheck(location);
        // validateClassName(className);
        nullCheck(className);
        nullCheck(kind);
        if (!sourceOrClass.contains(kind))
            throw new IllegalArgumentException("Invalid kind: " + kind);
        return getFileForInput(location, RelativeFile.forClass(className, kind));
    }

    @Override @DefinedBy(Api.COMPILER)
    public FileObject getFileForInput(Location location,
                                      String packageName,
                                      String relativeName)
        throws IOException
    {
        nullCheck(location);
        // validatePackageName(packageName);
        nullCheck(packageName);
        if (!isRelativeUri(relativeName))
            throw new IllegalArgumentException("Invalid relative name: " + relativeName);
        RelativeFile name = packageName.length() == 0
            ? new RelativeFile(relativeName)
            : new RelativeFile(RelativeDirectory.forPackage(packageName), relativeName);
        return getFileForInput(location, name);
    }

    private JavaFileObject getFileForInput(Location location, RelativeFile name) throws IOException {
        Iterable<? extends Path> path = getLocationAsPaths(location);
        if (path == null)
            return null;

        for (Path file: path) {
            if (file == Locations.JRT_MARKER_FILE) {
                JRTIndex.Entry e = getJRTIndex().getEntry(name.dirname());
                if (symbolFileEnabled && e.ctSym.hidden)
                    continue;
                Path p = e.files.get(name.basename());
                if (p != null)
                    return PathFileObject.forJRTPath(this, p);
            } else if (fsInfo.isDirectory(file)) {
                try {
                    Path f = name.resolveAgainst(file);
                    if (Files.exists(f))
                        return PathFileObject.forSimplePath(this,
                                fsInfo.getCanonicalFile(f), f);
                } catch (InvalidPathException ignore) {
                }
            } else if (Files.exists(file)) {
                FileSystem fs = getFileSystem(file);
                if (fs != null) {
                    Path fsRoot = fs.getRootDirectories().iterator().next();
                    Path f = name.resolveAgainst(fsRoot);
                    if (Files.exists(f))
                        return PathFileObject.forJarPath(this, f, file);
                }
            }
        }
        return null;
    }

    @Override @DefinedBy(Api.COMPILER)
    public JavaFileObject getJavaFileForOutput(Location location,
                                               String className,
                                               JavaFileObject.Kind kind,
                                               FileObject sibling)
        throws IOException
    {
        nullCheck(location);
        // validateClassName(className);
        nullCheck(className);
        nullCheck(kind);
        if (!sourceOrClass.contains(kind))
            throw new IllegalArgumentException("Invalid kind: " + kind);
        return getFileForOutput(location, RelativeFile.forClass(className, kind), sibling);
    }

    @Override @DefinedBy(Api.COMPILER)
    public FileObject getFileForOutput(Location location,
                                       String packageName,
                                       String relativeName,
                                       FileObject sibling)
        throws IOException
    {
        nullCheck(location);
        // validatePackageName(packageName);
        nullCheck(packageName);
        if (!isRelativeUri(relativeName))
            throw new IllegalArgumentException("Invalid relative name: " + relativeName);
        RelativeFile name = packageName.length() == 0
            ? new RelativeFile(relativeName)
            : new RelativeFile(RelativeDirectory.forPackage(packageName), relativeName);
        return getFileForOutput(location, name, sibling);
    }

    private JavaFileObject getFileForOutput(Location location,
                                            RelativeFile fileName,
                                            FileObject sibling)
        throws IOException
    {
        Path dir;
        if (location == CLASS_OUTPUT) {
            if (getClassOutDir() != null) {
                dir = getClassOutDir();
            } else {
                String baseName = fileName.basename();
                if (sibling != null && sibling instanceof PathFileObject) {
                    return ((PathFileObject) sibling).getSibling(baseName);
                } else {
                    Path p = Paths.get(baseName);
                    Path real = fsInfo.getCanonicalFile(p);
                    return PathFileObject.forSimplePath(this, real, p);
                }
            }
        } else if (location == SOURCE_OUTPUT) {
            dir = (getSourceOutDir() != null ? getSourceOutDir() : getClassOutDir());
        } else {
            Iterable<? extends Path> path = locations.getLocation(location);
            dir = null;
            for (Path f: path) {
                dir = f;
                break;
            }
        }

        try {
            if (dir == null) {
                dir = Paths.get(System.getProperty("user.dir"));
            }
            Path path = fileName.resolveAgainst(fsInfo.getCanonicalFile(dir));
            return PathFileObject.forDirectoryPath(this, path, dir, fileName);
        } catch (InvalidPathException e) {
            throw new IOException("bad filename " + fileName, e);
        }
    }

    @Override @DefinedBy(Api.COMPILER)
    public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(
        Iterable<? extends File> files)
    {
        ArrayList<PathFileObject> result;
        if (files instanceof Collection<?>)
            result = new ArrayList<>(((Collection<?>)files).size());
        else
            result = new ArrayList<>();
        for (File f: files) {
            Objects.requireNonNull(f);
            Path p = f.toPath();
            result.add(PathFileObject.forSimplePath(this,
                    fsInfo.getCanonicalFile(p), p));
        }
        return result;
    }

    @Override @DefinedBy(Api.COMPILER)
    public Iterable<? extends JavaFileObject> getJavaFileObjectsFromPaths(
        Iterable<? extends Path> paths)
    {
        ArrayList<PathFileObject> result;
        if (paths instanceof Collection<?>)
            result = new ArrayList<>(((Collection<?>)paths).size());
        else
            result = new ArrayList<>();
        for (Path p: paths)
            result.add(PathFileObject.forSimplePath(this,
                    fsInfo.getCanonicalFile(p), p));
        return result;
    }

    @Override @DefinedBy(Api.COMPILER)
    public Iterable<? extends JavaFileObject> getJavaFileObjects(File... files) {
        return getJavaFileObjectsFromFiles(Arrays.asList(nullCheck(files)));
    }

    @Override @DefinedBy(Api.COMPILER)
    public Iterable<? extends JavaFileObject> getJavaFileObjects(Path... paths) {
        return getJavaFileObjectsFromPaths(Arrays.asList(nullCheck(paths)));
    }

    @Override @DefinedBy(Api.COMPILER)
    public void setLocation(Location location,
                            Iterable<? extends File> searchpath)
        throws IOException
    {
        nullCheck(location);
        locations.setLocation(location, asPaths(searchpath));
    }

    @Override @DefinedBy(Api.COMPILER)
    public void setLocationFromPaths(Location location,
                            Iterable<? extends Path> searchpath)
        throws IOException
    {
        nullCheck(location);
        locations.setLocation(location, nullCheck(searchpath));
    }

    @Override @DefinedBy(Api.COMPILER)
    public Iterable<? extends File> getLocation(Location location) {
        nullCheck(location);
        return asFiles(locations.getLocation(location));
    }

    @Override @DefinedBy(Api.COMPILER)
    public Iterable<? extends Path> getLocationAsPaths(Location location) {
        nullCheck(location);
        return locations.getLocation(location);
    }

    private Path getClassOutDir() {
        return locations.getOutputLocation(CLASS_OUTPUT);
    }

    private Path getSourceOutDir() {
        return locations.getOutputLocation(SOURCE_OUTPUT);
    }

    @Override @DefinedBy(Api.COMPILER)
    public Path asPath(FileObject file) {
        if (file instanceof PathFileObject) {
            return ((PathFileObject) file).path;
        } else
            throw new IllegalArgumentException(file.getName());
    }

    /**
     * Enforces the specification of a "relative" name as used in
     * {@linkplain #getFileForInput(Location,String,String)
     * getFileForInput}.  This method must follow the rules defined in
     * that method, do not make any changes without consulting the
     * specification.
     */
    protected static boolean isRelativeUri(URI uri) {
        if (uri.isAbsolute())
            return false;
        String path = uri.normalize().getPath();
        if (path.length() == 0 /* isEmpty() is mustang API */)
            return false;
        if (!path.equals(uri.getPath())) // implicitly checks for embedded . and ..
            return false;
        if (path.startsWith("/") || path.startsWith("./") || path.startsWith("../"))
            return false;
        return true;
    }

    // Convenience method
    protected static boolean isRelativeUri(String u) {
        try {
            return isRelativeUri(new URI(u));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Converts a relative file name to a relative URI.  This is
     * different from File.toURI as this method does not canonicalize
     * the file before creating the URI.  Furthermore, no schema is
     * used.
     * @param file a relative file name
     * @return a relative URI
     * @throws IllegalArgumentException if the file name is not
     * relative according to the definition given in {@link
     * javax.tools.JavaFileManager#getFileForInput}
     */
    public static String getRelativeName(File file) {
        if (!file.isAbsolute()) {
            String result = file.getPath().replace(File.separatorChar, '/');
            if (isRelativeUri(result))
                return result;
        }
        throw new IllegalArgumentException("Invalid relative path: " + file);
    }

    /**
     * Get a detail message from an IOException.
     * Most, but not all, instances of IOException provide a non-null result
     * for getLocalizedMessage().  But some instances return null: in these
     * cases, fallover to getMessage(), and if even that is null, return the
     * name of the exception itself.
     * @param e an IOException
     * @return a string to include in a compiler diagnostic
     */
    public static String getMessage(IOException e) {
        String s = e.getLocalizedMessage();
        if (s != null)
            return s;
        s = e.getMessage();
        if (s != null)
            return s;
        return e.toString();
    }

    /* Converters between files and paths.
     * These are temporary until we can update the StandardJavaFileManager API.
     */

    private static Iterable<Path> asPaths(final Iterable<? extends File> files) {
        if (files == null)
            return null;

        return () -> new Iterator<Path>() {
            Iterator<? extends File> iter = files.iterator();

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public Path next() {
                return iter.next().toPath();
            }
        };
    }

    private static Iterable<File> asFiles(final Iterable<? extends Path> paths) {
        if (paths == null)
            return null;

        return () -> new Iterator<File>() {
            Iterator<? extends Path> iter = paths.iterator();

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public File next() {
                try {
                    return iter.next().toFile();
                } catch (UnsupportedOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }
}
