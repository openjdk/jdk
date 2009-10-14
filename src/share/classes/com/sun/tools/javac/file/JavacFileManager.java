/*
 * Copyright 2005-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.javac.file;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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

import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.file.RelativePath.RelativeFile;
import com.sun.tools.javac.file.RelativePath.RelativeDirectory;
import com.sun.tools.javac.main.JavacOption;
import com.sun.tools.javac.main.OptionName;
import com.sun.tools.javac.main.RecognizedOptions;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.SimpleDiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;

import static javax.tools.StandardLocation.*;
import static com.sun.tools.javac.main.OptionName.*;

/**
 * This class provides access to the source, class and other files
 * used by the compiler and related tools.
 *
 * <p><b>This is NOT part of any API supported by Sun Microsystems.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class JavacFileManager implements StandardJavaFileManager {

    boolean useZipFileIndex;

    public static char[] toArray(CharBuffer buffer) {
        if (buffer.hasArray())
            return ((CharBuffer)buffer.compact().flip()).array();
        else
            return buffer.toString().toCharArray();
    }

    /**
     * The log to be used for error reporting.
     */
    protected Log log;

    /** Encapsulates knowledge of paths
     */
    private Paths paths;

    private Options options;

    private FSInfo fsInfo;

    private final File uninited = new File("U N I N I T E D");

    private final Set<JavaFileObject.Kind> sourceOrClass =
        EnumSet.of(JavaFileObject.Kind.SOURCE, JavaFileObject.Kind.CLASS);

    /** The standard output directory, primarily used for classes.
     *  Initialized by the "-d" option.
     *  If classOutDir = null, files are written into same directory as the sources
     *  they were generated from.
     */
    private File classOutDir = uninited;

    /** The output directory, used when generating sources while processing annotations.
     *  Initialized by the "-s" option.
     */
    private File sourceOutDir = uninited;

    protected boolean mmappedIO;
    protected boolean ignoreSymbolFile;
    protected String classLoaderClass;

    /**
     * User provided charset (through javax.tools).
     */
    protected Charset charset;

    /**
     * Register a Context.Factory to create a JavacFileManager.
     */
    public static void preRegister(final Context context) {
        context.put(JavaFileManager.class, new Context.Factory<JavaFileManager>() {
            public JavaFileManager make() {
                return new JavacFileManager(context, true, null);
            }
        });
    }

    /**
     * Create a JavacFileManager using a given context, optionally registering
     * it as the JavaFileManager for that context.
     */
    public JavacFileManager(Context context, boolean register, Charset charset) {
        if (register)
            context.put(JavaFileManager.class, this);
        byteBufferCache = new ByteBufferCache();
        this.charset = charset;
        setContext(context);
    }

    /**
     * Set the context for JavacFileManager.
     */
    public void setContext(Context context) {
        log = Log.instance(context);
        if (paths == null) {
            paths = Paths.instance(context);
        } else {
            // Reuse the Paths object as it stores the locations that
            // have been set with setLocation, etc.
            paths.setContext(context);
        }

        options = Options.instance(context);
        fsInfo = FSInfo.instance(context);

        useZipFileIndex = System.getProperty("useJavaUtilZip") == null;// TODO: options.get("useJavaUtilZip") == null;

        mmappedIO = options.get("mmappedIO") != null;
        ignoreSymbolFile = options.get("ignore.symbol.file") != null;
        classLoaderClass = options.get("procloader");
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
        ListBuffer<File> files = new ListBuffer<File>();
        for (String name : names)
            files.append(new File(nullCheck(name)));
        return getJavaFileObjectsFromFiles(files.toList());
    }

    public Iterable<? extends JavaFileObject> getJavaFileObjects(String... names) {
        return getJavaFileObjectsFromStrings(Arrays.asList(nullCheck(names)));
    }

    protected JavaFileObject.Kind getKind(String extension) {
        if (extension.equals(JavaFileObject.Kind.CLASS.extension))
            return JavaFileObject.Kind.CLASS;
        else if (extension.equals(JavaFileObject.Kind.SOURCE.extension))
            return JavaFileObject.Kind.SOURCE;
        else if (extension.equals(JavaFileObject.Kind.HTML.extension))
            return JavaFileObject.Kind.HTML;
        else
            return JavaFileObject.Kind.OTHER;
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
     * Insert all files in subdirectory `subdirectory' of `directory' which end
     * in one of the extensions in `extensions' into packageSym.
     */
    private void listDirectory(File directory,
                               RelativeDirectory subdirectory,
                               Set<JavaFileObject.Kind> fileKinds,
                               boolean recurse,
                               ListBuffer<JavaFileObject> l) {
        Archive archive = archives.get(directory);

        boolean isFile = fsInfo.isFile(directory);

        if (archive != null || isFile) {
            if (archive == null) {
                try {
                    archive = openArchive(directory);
                } catch (IOException ex) {
                    log.error("error.reading.file",
                       directory, ex.getLocalizedMessage());
                    return;
                }
            }

            List<String> files = archive.getFiles(subdirectory);
            if (files != null) {
                for (String file; !files.isEmpty(); files = files.tail) {
                    file = files.head;
                    if (isValidFile(file, fileKinds)) {
                        l.append(archive.getFileObject(subdirectory, file));
                    }
                }
            }
            if (recurse) {
                for (RelativeDirectory s: archive.getSubdirectories()) {
                    if (subdirectory.contains(s)) {
                        // Because the archive map is a flat list of directories,
                        // the enclosing loop will pick up all child subdirectories.
                        // Therefore, there is no need to recurse deeper.
                        listDirectory(directory, s, fileKinds, false, l);
                    }
                }
            }
        } else {
            File d = subdirectory.getFile(directory);
            if (!caseMapCheck(d, subdirectory))
                return;

            File[] files = d.listFiles();
            if (files == null)
                return;

            for (File f: files) {
                String fname = f.getName();
                if (f.isDirectory()) {
                    if (recurse && SourceVersion.isIdentifier(fname)) {
                        listDirectory(directory,
                                      new RelativeDirectory(subdirectory, fname),
                                      fileKinds,
                                      recurse,
                                      l);
                    }
                } else {
                    if (isValidFile(fname, fileKinds)) {
                        JavaFileObject fe =
                            new RegularFileObject(this, fname, new File(d, fname));
                        l.append(fe);
                    }
                }
            }
        }
    }

    private boolean isValidFile(String s, Set<JavaFileObject.Kind> fileKinds) {
        int lastDot = s.lastIndexOf(".");
        String extn = (lastDot == -1 ? s : s.substring(lastDot));
        JavaFileObject.Kind kind = getKind(extn);
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
    Map<File, Archive> archives = new HashMap<File,Archive>();

    private static final String[] symbolFileLocation = { "lib", "ct.sym" };
    private static final RelativeDirectory symbolFilePrefix
            = new RelativeDirectory("META-INF/sym/rt.jar/");

    /** Open a new zip file directory.
     */
    protected Archive openArchive(File zipFileName) throws IOException {
        Archive archive = archives.get(zipFileName);
        if (archive == null) {
            File origZipFileName = zipFileName;
            if (!ignoreSymbolFile && paths.isBootClassPathRtJar(zipFileName)) {
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

            try {

                ZipFile zdir = null;

                boolean usePreindexedCache = false;
                String preindexCacheLocation = null;

                if (!useZipFileIndex) {
                    zdir = new ZipFile(zipFileName);
                }
                else {
                    usePreindexedCache = options.get("usezipindex") != null;
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
                    if (!useZipFileIndex) {
                        archive = new ZipArchive(this, zdir);
                    } else {
                        archive = new ZipFileIndexArchive(this,
                                ZipFileIndex.getZipFileIndex(zipFileName,
                                    null,
                                    usePreindexedCache,
                                    preindexCacheLocation,
                                    options.get("writezipindexfiles") != null));
                    }
                }
                else {
                    if (!useZipFileIndex) {
                        archive = new SymbolArchive(this, origZipFileName, zdir, symbolFilePrefix);
                    }
                    else {
                        archive = new ZipFileIndexArchive(this,
                                ZipFileIndex.getZipFileIndex(zipFileName,
                                    symbolFilePrefix,
                                    usePreindexedCache,
                                    preindexCacheLocation,
                                    options.get("writezipindexfiles") != null));
                    }
                }
            } catch (FileNotFoundException ex) {
                archive = new MissingArchive(zipFileName);
            } catch (IOException ex) {
                if (zipFileName.exists())
                    log.error("error.reading.file", zipFileName, ex.getLocalizedMessage());
                archive = new MissingArchive(zipFileName);
            }

            archives.put(origZipFileName, archive);
        }
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

    CharBuffer getCachedContent(JavaFileObject file) {
        SoftReference<CharBuffer> r = contentCache.get(file);
        return (r == null ? null : r.get());
    }

    void cache(JavaFileObject file, CharBuffer cb) {
        contentCache.put(file, new SoftReference<CharBuffer>(cb));
    }

    private final Map<JavaFileObject, SoftReference<CharBuffer>> contentCache
            = new HashMap<JavaFileObject, SoftReference<CharBuffer>>();

    private String defaultEncodingName;
    private String getDefaultEncodingName() {
        if (defaultEncodingName == null) {
            defaultEncodingName =
                new OutputStreamWriter(new ByteArrayOutputStream()).getEncoding();
        }
        return defaultEncodingName;
    }

    protected String getEncodingName() {
        String encName = options.get(OptionName.ENCODING);
        if (encName == null)
            return getDefaultEncodingName();
        else
            return encName;
    }

    protected Source getSource() {
        String sourceName = options.get(OptionName.SOURCE);
        Source source = null;
        if (sourceName != null)
            source = Source.lookup(sourceName);
        return (source != null ? source : Source.DEFAULT);
    }

    /**
     * Make a byte buffer from an input stream.
     */
    ByteBuffer makeByteBuffer(InputStream in)
        throws IOException {
        int limit = in.available();
        if (mmappedIO && in instanceof FileInputStream) {
            // Experimental memory mapped I/O
            FileInputStream fin = (FileInputStream)in;
            return fin.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, limit);
        }
        if (limit < 1024) limit = 1024;
        ByteBuffer result = byteBufferCache.get(limit);
        int position = 0;
        while (in.available() != 0) {
            if (position >= limit)
                // expand buffer
                result = ByteBuffer.
                    allocate(limit <<= 1).
                    put((ByteBuffer)result.flip());
            int count = in.read(result.array(),
                position,
                limit - position);
            if (count < 0) break;
            result.position(position += count);
        }
        return (ByteBuffer)result.flip();
    }

    void recycleByteBuffer(ByteBuffer bb) {
        byteBufferCache.put(bb);
    }

    /**
     * A single-element cache of direct byte buffers.
     */
    private static class ByteBufferCache {
        private ByteBuffer cached;
        ByteBuffer get(int capacity) {
            if (capacity < 20480) capacity = 20480;
            ByteBuffer result =
                (cached != null && cached.capacity() >= capacity)
                ? (ByteBuffer)cached.clear()
                : ByteBuffer.allocate(capacity + capacity>>1);
            cached = null;
            return result;
        }
        void put(ByteBuffer x) {
            cached = x;
        }
    }

    private final ByteBufferCache byteBufferCache;

    CharsetDecoder getDecoder(String encodingName, boolean ignoreEncodingErrors) {
        Charset cs = (this.charset == null)
            ? Charset.forName(encodingName)
            : this.charset;
        CharsetDecoder decoder = cs.newDecoder();

        CodingErrorAction action;
        if (ignoreEncodingErrors)
            action = CodingErrorAction.REPLACE;
        else
            action = CodingErrorAction.REPORT;

        return decoder
            .onMalformedInput(action)
            .onUnmappableCharacter(action);
    }

    /**
     * Decode a ByteBuffer into a CharBuffer.
     */
    CharBuffer decode(ByteBuffer inbuf, boolean ignoreEncodingErrors) {
        String encodingName = getEncodingName();
        CharsetDecoder decoder;
        try {
            decoder = getDecoder(encodingName, ignoreEncodingErrors);
        } catch (IllegalCharsetNameException e) {
            log.error("unsupported.encoding", encodingName);
            return (CharBuffer)CharBuffer.allocate(1).flip();
        } catch (UnsupportedCharsetException e) {
            log.error("unsupported.encoding", encodingName);
            return (CharBuffer)CharBuffer.allocate(1).flip();
        }

        // slightly overestimate the buffer size to avoid reallocation.
        float factor =
            decoder.averageCharsPerByte() * 0.8f +
            decoder.maxCharsPerByte() * 0.2f;
        CharBuffer dest = CharBuffer.
            allocate(10 + (int)(inbuf.remaining()*factor));

        while (true) {
            CoderResult result = decoder.decode(inbuf, dest, true);
            dest.flip();

            if (result.isUnderflow()) { // done reading
                // make sure there is at least one extra character
                if (dest.limit() == dest.capacity()) {
                    dest = CharBuffer.allocate(dest.capacity()+1).put(dest);
                    dest.flip();
                }
                return dest;
            } else if (result.isOverflow()) { // buffer too small; expand
                int newCapacity =
                    10 + dest.capacity() +
                    (int)(inbuf.remaining()*decoder.maxCharsPerByte());
                dest = CharBuffer.allocate(newCapacity).put(dest);
            } else if (result.isMalformed() || result.isUnmappable()) {
                // bad character in input

                // report coding error (warn only pre 1.5)
                if (!getSource().allowEncodingErrors()) {
                    log.error(new SimpleDiagnosticPosition(dest.limit()),
                              "illegal.char.for.encoding",
                              charset == null ? encodingName : charset.name());
                } else {
                    log.warning(new SimpleDiagnosticPosition(dest.limit()),
                                "illegal.char.for.encoding",
                                charset == null ? encodingName : charset.name());
                }

                // skip past the coding error
                inbuf.position(inbuf.position() + result.length());

                // undo the flip() to prepare the output buffer
                // for more translation
                dest.position(dest.limit());
                dest.limit(dest.capacity());
                dest.put((char)0xfffd); // backward compatible
            } else {
                throw new AssertionError(result);
            }
        }
        // unreached
    }

    public ClassLoader getClassLoader(Location location) {
        nullCheck(location);
        Iterable<? extends File> path = getLocation(location);
        if (path == null)
            return null;
        ListBuffer<URL> lb = new ListBuffer<URL>();
        for (File f: path) {
            try {
                lb.append(f.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new AssertionError(e);
            }
        }

        URL[] urls = lb.toArray(new URL[lb.size()]);
        ClassLoader thisClassLoader = getClass().getClassLoader();

        // Bug: 6558476
        // Ideally, ClassLoader should be Closeable, but before JDK7 it is not.
        // On older versions, try the following, to get a closeable classloader.

        // 1: Allow client to specify the class to use via hidden option
        if (classLoaderClass != null) {
            try {
                Class<? extends ClassLoader> loader =
                        Class.forName(classLoaderClass).asSubclass(ClassLoader.class);
                Class<?>[] constrArgTypes = { URL[].class, ClassLoader.class };
                Constructor<? extends ClassLoader> constr = loader.getConstructor(constrArgTypes);
                return constr.newInstance(new Object[] { urls, thisClassLoader });
            } catch (Throwable t) {
                // ignore errors loading user-provided class loader, fall through
            }
        }

        // 2: If URLClassLoader implements Closeable, use that.
        if (Closeable.class.isAssignableFrom(URLClassLoader.class))
            return new URLClassLoader(urls, thisClassLoader);

        // 3: Try using private reflection-based CloseableURLClassLoader
        try {
            return new CloseableURLClassLoader(urls, thisClassLoader);
        } catch (Throwable t) {
            // ignore errors loading workaround class loader, fall through
        }

        // 4: If all else fails, use plain old standard URLClassLoader
        return new URLClassLoader(urls, thisClassLoader);
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
        ListBuffer<JavaFileObject> results = new ListBuffer<JavaFileObject>();

        for (File directory : path)
            listDirectory(directory, subdirectory, kinds, recurse, results);

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

    public boolean handleOption(String current, Iterator<String> remaining) {
        for (JavacOption o: javacFileManagerOptions) {
            if (o.matches(current))  {
                if (o.hasArg()) {
                    if (remaining.hasNext()) {
                        if (!o.process(options, current, remaining.next()))
                            return true;
                    }
                } else {
                    if (!o.process(options, current))
                        return true;
                }
                // operand missing, or process returned false
                throw new IllegalArgumentException(current);
            }
        }

        return false;
    }
    // where
        private static JavacOption[] javacFileManagerOptions =
            RecognizedOptions.getJavacFileManagerOptions(
            new RecognizedOptions.GrumpyHelper());

    public int isSupportedOption(String option) {
        for (JavacOption o : javacFileManagerOptions) {
            if (o.matches(option))
                return o.hasArg() ? 1 : 0;
        }
        return -1;
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
            throw new IllegalArgumentException("Invalid kind " + kind);
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
            if (dir.isDirectory()) {
                File f = name.getFile(dir);
                if (f.exists())
                    return new RegularFileObject(this, f);
            } else {
                Archive a = openArchive(dir);
                if (a.contains(name)) {
                    return a.getFileObject(name.dirname(), name.basename());
                }

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
            throw new IllegalArgumentException("Invalid kind " + kind);
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
            throw new IllegalArgumentException("relativeName is invalid");
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
            Iterable<? extends File> path = paths.getPathForLocation(location);
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
            result = new ArrayList<RegularFileObject>(((Collection<?>)files).size());
        else
            result = new ArrayList<RegularFileObject>();
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
        paths.lazy();

        final File dir = location.isOutputLocation() ? getOutputDirectory(path) : null;

        if (location == CLASS_OUTPUT)
            classOutDir = getOutputLocation(dir, D);
        else if (location == SOURCE_OUTPUT)
            sourceOutDir = getOutputLocation(dir, S);
        else
            paths.setPathForLocation(location, path);
    }
    // where
        private File getOutputDirectory(Iterable<? extends File> path) throws IOException {
            if (path == null)
                return null;
            Iterator<? extends File> pathIter = path.iterator();
            if (!pathIter.hasNext())
                throw new IllegalArgumentException("empty path for directory");
            File dir = pathIter.next();
            if (pathIter.hasNext())
                throw new IllegalArgumentException("path too long for directory");
            if (!dir.exists())
                throw new FileNotFoundException(dir + ": does not exist");
            else if (!dir.isDirectory())
                throw new IOException(dir + ": not a directory");
            return dir;
        }

    private File getOutputLocation(File dir, OptionName defaultOptionName) {
        if (dir != null)
            return dir;
        String arg = options.get(defaultOptionName);
        if (arg == null)
            return null;
        return new File(arg);
    }

    public Iterable<? extends File> getLocation(Location location) {
        nullCheck(location);
        paths.lazy();
        if (location == CLASS_OUTPUT) {
            return (getClassOutDir() == null ? null : List.of(getClassOutDir()));
        } else if (location == SOURCE_OUTPUT) {
            return (getSourceOutDir() == null ? null : List.of(getSourceOutDir()));
        } else
            return paths.getPathForLocation(location);
    }

    private File getClassOutDir() {
        if (classOutDir == uninited)
            classOutDir = getOutputLocation(null, D);
        return classOutDir;
    }

    private File getSourceOutDir() {
        if (sourceOutDir == uninited)
            sourceOutDir = getOutputLocation(null, S);
        return sourceOutDir;
    }

    /**
     * Enforces the specification of a "relative" URI as used in
     * {@linkplain #getFileForInput(Location,String,URI)
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
        char first = path.charAt(0);
        return first != '.' && first != '/';
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

    private static <T> T nullCheck(T o) {
        o.getClass(); // null check
        return o;
    }

    private static <T> Iterable<T> nullCheck(Iterable<T> it) {
        for (T t : it)
            t.getClass(); // null check
        return it;
    }
}
