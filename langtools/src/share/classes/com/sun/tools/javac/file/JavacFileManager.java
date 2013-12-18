/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import javax.lang.model.SourceVersion;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

import com.sun.tools.javac.file.RelativePath.RelativeFile;
import com.sun.tools.javac.file.RelativePath.RelativeDirectory;
import com.sun.tools.javac.util.BaseFileManager;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

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

    public static char[] toArray(CharBuffer buffer) {
        if (buffer.hasArray())
            return ((CharBuffer)buffer.compact().flip()).array();
        else
            return buffer.toString().toCharArray();
    }

    private FSInfo fsInfo;

    private boolean contextUseOptimizedZip;
    private ZipFileIndexCache zipFileIndexCache;

    private final Set<JavaFileObject.Kind> sourceOrClass =
        EnumSet.of(JavaFileObject.Kind.SOURCE, JavaFileObject.Kind.CLASS);

    protected boolean mmappedIO;
    protected boolean symbolFileEnabled;

    protected enum SortFiles implements Comparator<File> {
        FORWARD {
            public int compare(File f1, File f2) {
                return f1.getName().compareTo(f2.getName());
            }
        },
        REVERSE {
            public int compare(File f1, File f2) {
                return -f1.getName().compareTo(f2.getName());
            }
        }
    }

    protected SortFiles sortFiles;

    /**
     * Register a Context.Factory to create a JavacFileManager.
     */
    public static void preRegister(Context context) {
        context.put(JavaFileManager.class, new Context.Factory<JavaFileManager>() {
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

        contextUseOptimizedZip = options.getBoolean("useOptimizedZip", true);
        if (contextUseOptimizedZip)
            zipFileIndexCache = ZipFileIndexCache.getSharedInstance();

        mmappedIO = options.isSet("mmappedIO");
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

    @Override
    public boolean isDefaultBootClassPath() {
        return locations.isDefaultBootClassPath();
    }

    public JavaFileObject getFileForInput(String name) {
        return getRegularFile(new File(name));
    }

    public JavaFileObject getRegularFile(File file) {
        return new RegularFileObject(this, file);
    }

    public JavaFileObject getFileForOutput(String classname,
                                           JavaFileObject.Kind kind,
                                           JavaFileObject sibling)
        throws IOException
    {
        return getJavaFileForOutput(CLASS_OUTPUT, classname, kind, sibling);
    }

    public Iterable<? extends JavaFileObject> getJavaFileObjectsFromStrings(Iterable<String> names) {
        ListBuffer<File> files = new ListBuffer<>();
        for (String name : names)
            files.append(new File(nullCheck(name)));
        return getJavaFileObjectsFromFiles(files.toList());
    }

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
     * Insert all files in subdirectory subdirectory of directory directory
     * which match fileKinds into resultList
     */
    private void listDirectory(File directory,
                               RelativeDirectory subdirectory,
                               Set<JavaFileObject.Kind> fileKinds,
                               boolean recurse,
                               ListBuffer<JavaFileObject> resultList) {
        File d = subdirectory.getFile(directory);
        if (!caseMapCheck(d, subdirectory))
            return;

        File[] files = d.listFiles();
        if (files == null)
            return;

        if (sortFiles != null)
            Arrays.sort(files, sortFiles);

        for (File f: files) {
            String fname = f.getName();
            if (f.isDirectory()) {
                if (recurse && SourceVersion.isIdentifier(fname)) {
                    listDirectory(directory,
                                  new RelativeDirectory(subdirectory, fname),
                                  fileKinds,
                                  recurse,
                                  resultList);
                }
            } else {
                if (isValidFile(fname, fileKinds)) {
                    JavaFileObject fe =
                        new RegularFileObject(this, fname, new File(d, fname));
                    resultList.append(fe);
                }
            }
        }
    }

    /**
     * Insert all files in subdirectory subdirectory of archive archive
     * which match fileKinds into resultList
     */
    private void listArchive(Archive archive,
                               RelativeDirectory subdirectory,
                               Set<JavaFileObject.Kind> fileKinds,
                               boolean recurse,
                               ListBuffer<JavaFileObject> resultList) {
        // Get the files directly in the subdir
        List<String> files = archive.getFiles(subdirectory);
        if (files != null) {
            for (; !files.isEmpty(); files = files.tail) {
                String file = files.head;
                if (isValidFile(file, fileKinds)) {
                    resultList.append(archive.getFileObject(subdirectory, file));
                }
            }
        }
        if (recurse) {
            for (RelativeDirectory s: archive.getSubdirectories()) {
                if (subdirectory.contains(s)) {
                    // Because the archive map is a flat list of directories,
                    // the enclosing loop will pick up all child subdirectories.
                    // Therefore, there is no need to recurse deeper.
                    listArchive(archive, s, fileKinds, false, resultList);
                }
            }
        }
    }

    /**
     * container is a directory, a zip file, or a non-existant path.
     * Insert all files in subdirectory subdirectory of container which
     * match fileKinds into resultList
     */
    private void listContainer(File container,
                               RelativeDirectory subdirectory,
                               Set<JavaFileObject.Kind> fileKinds,
                               boolean recurse,
                               ListBuffer<JavaFileObject> resultList) {
        Archive archive = archives.get(container);
        if (archive == null) {
            // archives are not created for directories.
            if  (fsInfo.isDirectory(container)) {
                listDirectory(container,
                              subdirectory,
                              fileKinds,
                              recurse,
                              resultList);
                return;
            }

            // Not a directory; either a file or non-existant, create the archive
            try {
                archive = openArchive(container);
            } catch (IOException ex) {
                log.error("error.reading.file",
                          container, getMessage(ex));
                return;
            }
        }
        listArchive(archive,
                    subdirectory,
                    fileKinds,
                    recurse,
                    resultList);
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
    private boolean caseMapCheck(File f, RelativePath name) {
        if (fileSystemIsCaseSensitive) return true;
        // Note that getCanonicalPath() returns the case-sensitive
        // spelled file name.
        String path;
        try {
            path = f.getCanonicalPath();
        } catch (IOException ex) {
            return false;
        }
        char[] pcs = path.toCharArray();
        char[] ncs = name.path.toCharArray();
        int i = pcs.length - 1;
        int j = ncs.length - 1;
        while (i >= 0 && j >= 0) {
            while (i >= 0 && pcs[i] == File.separatorChar) i--;
            while (j >= 0 && ncs[j] == '/') j--;
            if (i >= 0 && j >= 0) {
                if (pcs[i] != ncs[j]) return false;
                i--;
                j--;
            }
        }
        return j < 0;
    }

    /**
     * An archive provides a flat directory structure of a ZipFile by
     * mapping directory names to lists of files (basenames).
     */
    public interface Archive {
        void close() throws IOException;

        boolean contains(RelativePath name);

        JavaFileObject getFileObject(RelativeDirectory subdirectory, String file);

        List<String> getFiles(RelativeDirectory subdirectory);

        Set<RelativeDirectory> getSubdirectories();
    }

    public class MissingArchive implements Archive {
        final File zipFileName;
        public MissingArchive(File name) {
            zipFileName = name;
        }
        public boolean contains(RelativePath name) {
            return false;
        }

        public void close() {
        }

        public JavaFileObject getFileObject(RelativeDirectory subdirectory, String file) {
            return null;
        }

        public List<String> getFiles(RelativeDirectory subdirectory) {
            return List.nil();
        }

        public Set<RelativeDirectory> getSubdirectories() {
            return Collections.emptySet();
        }

        @Override
        public String toString() {
            return "MissingArchive[" + zipFileName + "]";
        }
    }

    /** A directory of zip files already opened.
     */
    Map<File, Archive> archives = new HashMap<>();

    private static final String[] symbolFileLocation = { "lib", "ct.sym" };
    private static final RelativeDirectory symbolFilePrefix
            = new RelativeDirectory("META-INF/sym/rt.jar/");

    /*
     * This method looks for a ZipFormatException and takes appropriate
     * evasive action. If there is a failure in the fast mode then we
     * fail over to the platform zip, and allow it to deal with a potentially
     * non compliant zip file.
     */
    protected Archive openArchive(File zipFilename) throws IOException {
        try {
            return openArchive(zipFilename, contextUseOptimizedZip);
        } catch (IOException ioe) {
            if (ioe instanceof ZipFileIndex.ZipFormatException) {
                return openArchive(zipFilename, false);
            } else {
                throw ioe;
            }
        }
    }

    /** Open a new zip file directory, and cache it.
     */
    private Archive openArchive(File zipFileName, boolean useOptimizedZip) throws IOException {
        File origZipFileName = zipFileName;
        if (symbolFileEnabled && locations.isDefaultBootClassPathRtJar(zipFileName)) {
            File file = zipFileName.getParentFile().getParentFile(); // ${java.home}
            if (new File(file.getName()).equals(new File("jre")))
                file = file.getParentFile();
            // file == ${jdk.home}
            for (String name : symbolFileLocation)
                file = new File(file, name);
            // file == ${jdk.home}/lib/ct.sym
            if (file.exists())
                zipFileName = file;
        }

        Archive archive;
        try {

            ZipFile zdir = null;

            boolean usePreindexedCache = false;
            String preindexCacheLocation = null;

            if (!useOptimizedZip) {
                zdir = new ZipFile(zipFileName);
            } else {
                usePreindexedCache = options.isSet("usezipindex");
                preindexCacheLocation = options.get("java.io.tmpdir");
                String optCacheLoc = options.get("cachezipindexdir");

                if (optCacheLoc != null && optCacheLoc.length() != 0) {
                    if (optCacheLoc.startsWith("\"")) {
                        if (optCacheLoc.endsWith("\"")) {
                            optCacheLoc = optCacheLoc.substring(1, optCacheLoc.length() - 1);
                        }
                        else {
                            optCacheLoc = optCacheLoc.substring(1);
                        }
                    }

                    File cacheDir = new File(optCacheLoc);
                    if (cacheDir.exists() && cacheDir.canWrite()) {
                        preindexCacheLocation = optCacheLoc;
                        if (!preindexCacheLocation.endsWith("/") &&
                            !preindexCacheLocation.endsWith(File.separator)) {
                            preindexCacheLocation += File.separator;
                        }
                    }
                }
            }

            if (origZipFileName == zipFileName) {
                if (!useOptimizedZip) {
                    archive = new ZipArchive(this, zdir);
                } else {
                    archive = new ZipFileIndexArchive(this,
                                    zipFileIndexCache.getZipFileIndex(zipFileName,
                                    null,
                                    usePreindexedCache,
                                    preindexCacheLocation,
                                    options.isSet("writezipindexfiles")));
                }
            } else {
                if (!useOptimizedZip) {
                    archive = new SymbolArchive(this, origZipFileName, zdir, symbolFilePrefix);
                } else {
                    archive = new ZipFileIndexArchive(this,
                                    zipFileIndexCache.getZipFileIndex(zipFileName,
                                    symbolFilePrefix,
                                    usePreindexedCache,
                                    preindexCacheLocation,
                                    options.isSet("writezipindexfiles")));
                }
            }
        } catch (FileNotFoundException ex) {
            archive = new MissingArchive(zipFileName);
        } catch (ZipFileIndex.ZipFormatException zfe) {
            throw zfe;
        } catch (IOException ex) {
            if (zipFileName.exists())
                log.error("error.reading.file", zipFileName, getMessage(ex));
            archive = new MissingArchive(zipFileName);
        }

        archives.put(origZipFileName, archive);
        return archive;
    }

    /** Flush any output resources.
     */
    public void flush() {
        contentCache.clear();
    }

    /**
     * Close the JavaFileManager, releasing resources.
     */
    public void close() {
        for (Iterator<Archive> i = archives.values().iterator(); i.hasNext(); ) {
            Archive a = i.next();
            i.remove();
            try {
                a.close();
            } catch (IOException e) {
            }
        }
    }

    private String defaultEncodingName;
    private String getDefaultEncodingName() {
        if (defaultEncodingName == null) {
            defaultEncodingName =
                new OutputStreamWriter(new ByteArrayOutputStream()).getEncoding();
        }
        return defaultEncodingName;
    }

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

    public Iterable<JavaFileObject> list(Location location,
                                         String packageName,
                                         Set<JavaFileObject.Kind> kinds,
                                         boolean recurse)
        throws IOException
    {
        // validatePackageName(packageName);
        nullCheck(packageName);
        nullCheck(kinds);

        Iterable<? extends File> path = getLocation(location);
        if (path == null)
            return List.nil();
        RelativeDirectory subdirectory = RelativeDirectory.forPackage(packageName);
        ListBuffer<JavaFileObject> results = new ListBuffer<>();

        for (File directory : path)
            listContainer(directory, subdirectory, kinds, recurse, results);
        return results.toList();
    }

    public String inferBinaryName(Location location, JavaFileObject file) {
        file.getClass(); // null check
        location.getClass(); // null check
        // Need to match the path semantics of list(location, ...)
        Iterable<? extends File> path = getLocation(location);
        if (path == null) {
            return null;
        }

        if (file instanceof BaseFileObject) {
            return ((BaseFileObject) file).inferBinaryName(path);
        } else
            throw new IllegalArgumentException(file.getClass().getName());
    }

    public boolean isSameFile(FileObject a, FileObject b) {
        nullCheck(a);
        nullCheck(b);
        if (!(a instanceof BaseFileObject))
            throw new IllegalArgumentException("Not supported: " + a);
        if (!(b instanceof BaseFileObject))
            throw new IllegalArgumentException("Not supported: " + b);
        return a.equals(b);
    }

    public boolean hasLocation(Location location) {
        return getLocation(location) != null;
    }

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
        Iterable<? extends File> path = getLocation(location);
        if (path == null)
            return null;

        for (File dir: path) {
            Archive a = archives.get(dir);
            if (a == null) {
                if (fsInfo.isDirectory(dir)) {
                    File f = name.getFile(dir);
                    if (f.exists())
                        return new RegularFileObject(this, f);
                    continue;
                }
                // Not a directory, create the archive
                a = openArchive(dir);
            }
            // Process the archive
            if (a.contains(name)) {
                return a.getFileObject(name.dirname(), name.basename());
            }
        }
        return null;
    }

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
        File dir;
        if (location == CLASS_OUTPUT) {
            if (getClassOutDir() != null) {
                dir = getClassOutDir();
            } else {
                File siblingDir = null;
                if (sibling != null && sibling instanceof RegularFileObject) {
                    siblingDir = ((RegularFileObject)sibling).file.getParentFile();
                }
                return new RegularFileObject(this, new File(siblingDir, fileName.basename()));
            }
        } else if (location == SOURCE_OUTPUT) {
            dir = (getSourceOutDir() != null ? getSourceOutDir() : getClassOutDir());
        } else {
            Iterable<? extends File> path = locations.getLocation(location);
            dir = null;
            for (File f: path) {
                dir = f;
                break;
            }
        }

        File file = fileName.getFile(dir); // null-safe
        return new RegularFileObject(this, file);

    }

    public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(
        Iterable<? extends File> files)
    {
        ArrayList<RegularFileObject> result;
        if (files instanceof Collection<?>)
            result = new ArrayList<>(((Collection<?>)files).size());
        else
            result = new ArrayList<>();
        for (File f: files)
            result.add(new RegularFileObject(this, nullCheck(f)));
        return result;
    }

    public Iterable<? extends JavaFileObject> getJavaFileObjects(File... files) {
        return getJavaFileObjectsFromFiles(Arrays.asList(nullCheck(files)));
    }

    public void setLocation(Location location,
                            Iterable<? extends File> path)
        throws IOException
    {
        nullCheck(location);
        locations.setLocation(location, path);
    }

    public Iterable<? extends File> getLocation(Location location) {
        nullCheck(location);
        return locations.getLocation(location);
    }

    private File getClassOutDir() {
        return locations.getOutputLocation(CLASS_OUTPUT);
    }

    private File getSourceOutDir() {
        return locations.getOutputLocation(SOURCE_OUTPUT);
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
}
