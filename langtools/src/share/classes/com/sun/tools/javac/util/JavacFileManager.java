/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.javac.util;

import com.sun.tools.javac.main.JavacOption;
import com.sun.tools.javac.main.OptionName;
import com.sun.tools.javac.main.RecognizedOptions;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.ref.SoftReference;
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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.lang.model.SourceVersion;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.util.JCDiagnostic.SimpleDiagnosticPosition;
import java.util.concurrent.ConcurrentHashMap;
import javax.tools.StandardJavaFileManager;

import com.sun.tools.javac.zip.*;
import java.io.ByteArrayInputStream;

import static com.sun.tools.javac.main.OptionName.*;
import static javax.tools.StandardLocation.*;

/**
 * This class provides access to the source, class and other files
 * used by the compiler and related tools.
 */
public class JavacFileManager implements StandardJavaFileManager {

    private static final String[] symbolFileLocation = { "lib", "ct.sym" };
    private static final String symbolFilePrefix = "META-INF/sym/rt.jar/";

    boolean useZipFileIndex;

    private static int symbolFilePrefixLength = 0;
    static {
        try {
            symbolFilePrefixLength = symbolFilePrefix.getBytes("UTF-8").length;
        } catch (java.io.UnsupportedEncodingException uee) {
            // Can't happen...UTF-8 is always supported.
        }
    }

    private static boolean CHECK_ZIP_TIMESTAMP = false;
    private static Map<File, Boolean> isDirectory = new ConcurrentHashMap<File, Boolean>();


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

        useZipFileIndex = System.getProperty("useJavaUtilZip") == null;// TODO: options.get("useJavaUtilZip") == null;
        CHECK_ZIP_TIMESTAMP = System.getProperty("checkZipIndexTimestamp") != null;// TODO: options.get("checkZipIndexTimestamp") != null;

        mmappedIO = options.get("mmappedIO") != null;
        ignoreSymbolFile = options.get("ignore.symbol.file") != null;
    }

    public JavaFileObject getFileForInput(String name) {
        return getRegularFile(new File(name));
    }

    public JavaFileObject getRegularFile(File file) {
        return new RegularFileObject(file);
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

    /** Return external representation of name,
     *  converting '.' to File.separatorChar.
     */
    private static String externalizeFileName(CharSequence name) {
        return name.toString().replace('.', File.separatorChar);
    }

    private static String externalizeFileName(CharSequence n, JavaFileObject.Kind kind) {
        return externalizeFileName(n) + kind.extension;
    }

    private static String baseName(String fileName) {
        return fileName.substring(fileName.lastIndexOf(File.separatorChar) + 1);
    }

    /**
     * Insert all files in subdirectory `subdirectory' of `directory' which end
     * in one of the extensions in `extensions' into packageSym.
     */
    private void listDirectory(File directory,
                               String subdirectory,
                               Set<JavaFileObject.Kind> fileKinds,
                               boolean recurse,
                               ListBuffer<JavaFileObject> l) {
        Archive archive = archives.get(directory);

        boolean isFile = false;
        if (CHECK_ZIP_TIMESTAMP) {
            Boolean isf = isDirectory.get(directory);
            if (isf == null) {
                isFile = directory.isFile();
                isDirectory.put(directory, isFile);
            }
            else {
                isFile = directory.isFile();
            }
        }
        else {
            isFile = directory.isFile();
        }

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
            if (subdirectory.length() != 0) {
                if (!useZipFileIndex) {
                    subdirectory = subdirectory.replace('\\', '/');
                    if (!subdirectory.endsWith("/")) subdirectory = subdirectory + "/";
                }
                else {
                    if (File.separatorChar == '/') {
                        subdirectory = subdirectory.replace('\\', '/');
                    }
                    else {
                        subdirectory = subdirectory.replace('/', '\\');
                    }

                    if (!subdirectory.endsWith(File.separator)) subdirectory = subdirectory + File.separator;
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
                for (String s: archive.getSubdirectories()) {
                    if (s.startsWith(subdirectory) && !s.equals(subdirectory)) {
                        // Because the archive map is a flat list of directories,
                        // the enclosing loop will pick up all child subdirectories.
                        // Therefore, there is no need to recurse deeper.
                        listDirectory(directory, s, fileKinds, false, l);
                    }
                }
            }
        } else {
            File d = subdirectory.length() != 0
                ? new File(directory, subdirectory)
                : directory;
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
                                      subdirectory + File.separator + fname,
                                      fileKinds,
                                      recurse,
                                      l);
                    }
                } else {
                    if (isValidFile(fname, fileKinds)) {
                        JavaFileObject fe =
                        new RegularFileObject(fname, new File(d, fname));
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
    private boolean caseMapCheck(File f, String name) {
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
        char[] ncs = name.toCharArray();
        int i = pcs.length - 1;
        int j = ncs.length - 1;
        while (i >= 0 && j >= 0) {
            while (i >= 0 && pcs[i] == File.separatorChar) i--;
            while (j >= 0 && ncs[j] == File.separatorChar) j--;
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

        boolean contains(String name);

        JavaFileObject getFileObject(String subdirectory, String file);

        List<String> getFiles(String subdirectory);

        Set<String> getSubdirectories();
    }

    public class ZipArchive implements Archive {
        protected final Map<String,List<String>> map;
        protected final ZipFile zdir;
        public ZipArchive(ZipFile zdir) throws IOException {
            this.zdir = zdir;
            this.map = new HashMap<String,List<String>>();
            for (Enumeration<? extends ZipEntry> e = zdir.entries(); e.hasMoreElements(); ) {
                ZipEntry entry;
                try {
                    entry = e.nextElement();
                } catch (InternalError ex) {
                    IOException io = new IOException();
                    io.initCause(ex); // convenience constructors added in Mustang :-(
                    throw io;
                }
                addZipEntry(entry);
            }
        }

        void addZipEntry(ZipEntry entry) {
            String name = entry.getName();
            int i = name.lastIndexOf('/');
            String dirname = name.substring(0, i+1);
            String basename = name.substring(i+1);
            if (basename.length() == 0)
                return;
            List<String> list = map.get(dirname);
            if (list == null)
                list = List.nil();
            list = list.prepend(basename);
            map.put(dirname, list);
        }

        public boolean contains(String name) {
            int i = name.lastIndexOf('/');
            String dirname = name.substring(0, i+1);
            String basename = name.substring(i+1);
            if (basename.length() == 0)
                return false;
            List<String> list = map.get(dirname);
            return (list != null && list.contains(basename));
        }

        public List<String> getFiles(String subdirectory) {
            return map.get(subdirectory);
        }

        public JavaFileObject getFileObject(String subdirectory, String file) {
            ZipEntry ze = zdir.getEntry(subdirectory + file);
            return new ZipFileObject(file, zdir, ze);
        }

        public Set<String> getSubdirectories() {
            return map.keySet();
        }

        public void close() throws IOException {
            zdir.close();
        }
    }

    public class SymbolArchive extends ZipArchive {
        final File origFile;
        public SymbolArchive(File orig, ZipFile zdir) throws IOException {
            super(zdir);
            this.origFile = orig;
        }

        @Override
        void addZipEntry(ZipEntry entry) {
            // called from super constructor, may not refer to origFile.
            String name = entry.getName();
            if (!name.startsWith(symbolFilePrefix))
                return;
            name = name.substring(symbolFilePrefix.length());
            int i = name.lastIndexOf('/');
            String dirname = name.substring(0, i+1);
            String basename = name.substring(i+1);
            if (basename.length() == 0)
                return;
            List<String> list = map.get(dirname);
            if (list == null)
                list = List.nil();
            list = list.prepend(basename);
            map.put(dirname, list);
        }

        @Override
        public JavaFileObject getFileObject(String subdirectory, String file) {
            return super.getFileObject(symbolFilePrefix + subdirectory, file);
        }
    }

    public class MissingArchive implements Archive {
        final File zipFileName;
        public MissingArchive(File name) {
            zipFileName = name;
        }
        public boolean contains(String name) {
              return false;
        }

        public void close() {
        }

        public JavaFileObject getFileObject(String subdirectory, String file) {
            return null;
        }

        public List<String> getFiles(String subdirectory) {
            return List.nil();
        }

        public Set<String> getSubdirectories() {
            return Collections.emptySet();
        }
    }

    /** A directory of zip files already opened.
     */
    Map<File, Archive> archives = new HashMap<File,Archive>();

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
                        archive = new ZipArchive(zdir);
                    } else {
                        archive = new ZipFileIndexArchive(this, ZipFileIndex.getZipFileIndex(zipFileName, 0,
                                usePreindexedCache, preindexCacheLocation, options.get("writezipindexfiles") != null));
                    }
                }
                else {
                    if (!useZipFileIndex) {
                        archive = new SymbolArchive(origZipFileName, zdir);
                    }
                    else {
                        archive = new ZipFileIndexArchive(this, ZipFileIndex.getZipFileIndex(zipFileName, symbolFilePrefixLength,
                                usePreindexedCache, preindexCacheLocation, options.get("writezipindexfiles") != null));
                    }
                }
            } catch (FileNotFoundException ex) {
                archive = new MissingArchive(zipFileName);
            } catch (IOException ex) {
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

    private Map<JavaFileObject, SoftReference<CharBuffer>> contentCache = new HashMap<JavaFileObject, SoftReference<CharBuffer>>();

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
    private ByteBuffer makeByteBuffer(InputStream in)
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

    private CharsetDecoder getDecoder(String encodingName, boolean ignoreEncodingErrors) {
        Charset charset = (this.charset == null)
            ? Charset.forName(encodingName)
            : this.charset;
        CharsetDecoder decoder = charset.newDecoder();

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
    private CharBuffer decode(ByteBuffer inbuf, boolean ignoreEncodingErrors) {
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
        return new URLClassLoader(lb.toArray(new URL[lb.size()]),
            getClass().getClassLoader());
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
        String subdirectory = externalizeFileName(packageName);
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
            //System.err.println("Path for " + location + " is null");
            return null;
        }
        //System.err.println("Path for " + location + " is " + path);

        if (file instanceof RegularFileObject) {
            RegularFileObject r = (RegularFileObject) file;
            String rPath = r.getPath();
            //System.err.println("RegularFileObject " + file + " " +r.getPath());
            for (File dir: path) {
                //System.err.println("dir: " + dir);
                String dPath = dir.getPath();
                if (!dPath.endsWith(File.separator))
                    dPath += File.separator;
                if (rPath.regionMatches(true, 0, dPath, 0, dPath.length())
                    && new File(rPath.substring(0, dPath.length())).equals(new File(dPath))) {
                    String relativeName = rPath.substring(dPath.length());
                    return removeExtension(relativeName).replace(File.separatorChar, '.');
                }
            }
        } else if (file instanceof ZipFileObject) {
            ZipFileObject z = (ZipFileObject) file;
            String entryName = z.getZipEntryName();
            if (entryName.startsWith(symbolFilePrefix))
                entryName = entryName.substring(symbolFilePrefix.length());
            return removeExtension(entryName).replace('/', '.');
        } else if (file instanceof ZipFileIndexFileObject) {
            ZipFileIndexFileObject z = (ZipFileIndexFileObject) file;
            String entryName = z.getZipEntryName();
            if (entryName.startsWith(symbolFilePrefix))
                entryName = entryName.substring(symbolFilePrefix.length());
            return removeExtension(entryName).replace(File.separatorChar, '.');
        } else
            throw new IllegalArgumentException(file.getClass().getName());
        // System.err.println("inferBinaryName failed for " + file);
        return null;
    }
    // where
        private static String removeExtension(String fileName) {
            int lastDot = fileName.lastIndexOf(".");
            return (lastDot == -1 ? fileName : fileName.substring(0, lastDot));
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
        return getFileForInput(location, externalizeFileName(className, kind));
    }

    public FileObject getFileForInput(Location location,
                                      String packageName,
                                      String relativeName)
        throws IOException
    {
        nullCheck(location);
        // validatePackageName(packageName);
        nullCheck(packageName);
        if (!isRelativeUri(URI.create(relativeName))) // FIXME 6419701
            throw new IllegalArgumentException("Invalid relative name: " + relativeName);
        String name = packageName.length() == 0
            ? relativeName
            : new File(externalizeFileName(packageName), relativeName).getPath();
        return getFileForInput(location, name);
    }

    private JavaFileObject getFileForInput(Location location, String name) throws IOException {
        Iterable<? extends File> path = getLocation(location);
        if (path == null)
            return null;

        for (File dir: path) {
            if (dir.isDirectory()) {
                File f = new File(dir, name.replace('/', File.separatorChar));
                if (f.exists())
                    return new RegularFileObject(f);
            } else {
                Archive a = openArchive(dir);
                if (a.contains(name)) {
                    int i = name.lastIndexOf('/');
                    String dirname = name.substring(0, i+1);
                    String basename = name.substring(i+1);
                    return a.getFileObject(dirname, basename);
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
        return getFileForOutput(location, externalizeFileName(className, kind), sibling);
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
        if (!isRelativeUri(URI.create(relativeName))) // FIXME 6419701
            throw new IllegalArgumentException("relativeName is invalid");
        String name = packageName.length() == 0
            ? relativeName
            : new File(externalizeFileName(packageName), relativeName).getPath();
        return getFileForOutput(location, name, sibling);
    }

    private JavaFileObject getFileForOutput(Location location,
                                            String fileName,
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
                    siblingDir = ((RegularFileObject)sibling).f.getParentFile();
                }
                return new RegularFileObject(new File(siblingDir, baseName(fileName)));
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

        File file = (dir == null ? new File(fileName) : new File(dir, fileName));
        return new RegularFileObject(file);

    }

    public Iterable<? extends JavaFileObject> getJavaFileObjectsFromFiles(
        Iterable<? extends File> files)
    {
        ArrayList<RegularFileObject> result;
        if (files instanceof Collection)
            result = new ArrayList<RegularFileObject>(((Collection)files).size());
        else
            result = new ArrayList<RegularFileObject>();
        for (File f: files)
            result.add(new RegularFileObject(nullCheck(f)));
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
            if (JavacFileManager.isRelativeUri(URI.create(result))) // FIXME 6419701
                return result;
        }
        throw new IllegalArgumentException("Invalid relative path: " + file);
    }

    @SuppressWarnings("deprecation") // bug 6410637
    protected static String getJavacFileName(FileObject file) {
        if (file instanceof BaseFileObject)
            return ((BaseFileObject)file).getPath();
        URI uri = file.toUri();
        String scheme = uri.getScheme();
        if (scheme == null || scheme.equals("file") || scheme.equals("jar"))
            return uri.getPath();
        else
            return uri.toString();
    }

    @SuppressWarnings("deprecation") // bug 6410637
    protected static String getJavacBaseFileName(FileObject file) {
        if (file instanceof BaseFileObject)
            return ((BaseFileObject)file).getName();
        URI uri = file.toUri();
        String scheme = uri.getScheme();
        if (scheme == null || scheme.equals("file") || scheme.equals("jar")) {
            String path = uri.getPath();
            if (path == null)
                return null;
            if (scheme != null && scheme.equals("jar"))
                path = path.substring(path.lastIndexOf('!') + 1);
            return path.substring(path.lastIndexOf('/') + 1);
        } else {
            return uri.toString();
        }
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

    /**
     * A subclass of JavaFileObject representing regular files.
     */
    private class RegularFileObject extends BaseFileObject {
        /** Have the parent directories been created?
         */
        private boolean hasParents=false;

        /** The file's name.
         */
        private String name;

        /** The underlying file.
         */
        final File f;

        public RegularFileObject(File f) {
            this(f.getName(), f);
        }

        public RegularFileObject(String name, File f) {
            if (f.isDirectory())
                throw new IllegalArgumentException("directories not supported");
            this.name = name;
            this.f = f;
        }

        public InputStream openInputStream() throws IOException {
            return new FileInputStream(f);
        }

        protected CharsetDecoder getDecoder(boolean ignoreEncodingErrors) {
            return JavacFileManager.this.getDecoder(getEncodingName(), ignoreEncodingErrors);
        }

        public OutputStream openOutputStream() throws IOException {
            ensureParentDirectoriesExist();
            return new FileOutputStream(f);
        }

        public Writer openWriter() throws IOException {
            ensureParentDirectoriesExist();
            return new OutputStreamWriter(new FileOutputStream(f), getEncodingName());
        }

        private void ensureParentDirectoriesExist() throws IOException {
            if (!hasParents) {
                File parent = f.getParentFile();
                if (parent != null && !parent.exists()) {
                    if (!parent.mkdirs()) {
                        // if the mkdirs failed, it may be because another process concurrently
                        // created the directory, so check if the directory got created
                        // anyway before throwing an exception
                        if (!parent.exists() || !parent.isDirectory())
                            throw new IOException("could not create parent directories");
                    }
                }
                hasParents = true;
            }
        }

        /** @deprecated see bug 6410637 */
        @Deprecated
        public String getName() {
            return name;
        }

        public boolean isNameCompatible(String cn, JavaFileObject.Kind kind) {
            cn.getClass(); // null check
            if (kind == Kind.OTHER && getKind() != kind)
                return false;
            String n = cn + kind.extension;
            if (name.equals(n))
                return true;
            if (name.equalsIgnoreCase(n)) {
                try {
                    // allow for Windows
                    return (f.getCanonicalFile().getName().equals(n));
                } catch (IOException e) {
                }
            }
            return false;
        }

        /** @deprecated see bug 6410637 */
        @Deprecated
        public String getPath() {
            return f.getPath();
        }

        public long getLastModified() {
            return f.lastModified();
        }

        public boolean delete() {
            return f.delete();
        }

        public CharBuffer getCharContent(boolean ignoreEncodingErrors) throws IOException {
            SoftReference<CharBuffer> r = contentCache.get(this);
            CharBuffer cb = (r == null ? null : r.get());
            if (cb == null) {
                InputStream in = new FileInputStream(f);
                try {
                    ByteBuffer bb = makeByteBuffer(in);
                    JavaFileObject prev = log.useSource(this);
                    try {
                        cb = decode(bb, ignoreEncodingErrors);
                    } finally {
                        log.useSource(prev);
                    }
                    byteBufferCache.put(bb); // save for next time
                    if (!ignoreEncodingErrors)
                        contentCache.put(this, new SoftReference<CharBuffer>(cb));
                } finally {
                    in.close();
                }
            }
            return cb;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof RegularFileObject))
                return false;
            RegularFileObject o = (RegularFileObject) other;
            try {
                return f.equals(o.f)
                    || f.getCanonicalFile().equals(o.f.getCanonicalFile());
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return f.hashCode();
        }

        public URI toUri() {
            try {
                // Do no use File.toURI to avoid file system access
                String path = f.getAbsolutePath().replace(File.separatorChar, '/');
                return new URI("file://" + path).normalize();
            } catch (URISyntaxException ex) {
                return f.toURI();
            }
        }

    }

    /**
     * A subclass of JavaFileObject representing zip entries.
     */
    public class ZipFileObject extends BaseFileObject {

        /** The entry's name.
         */
        private String name;

        /** The zipfile containing the entry.
         */
        ZipFile zdir;

        /** The underlying zip entry object.
         */
        ZipEntry entry;

        public ZipFileObject(String name, ZipFile zdir, ZipEntry entry) {
            this.name = name;
            this.zdir = zdir;
            this.entry = entry;
        }

        public InputStream openInputStream() throws IOException {
            return zdir.getInputStream(entry);
        }

        public OutputStream openOutputStream() throws IOException {
            throw new UnsupportedOperationException();
        }

        protected CharsetDecoder getDecoder(boolean ignoreEncodingErrors) {
            return JavacFileManager.this.getDecoder(getEncodingName(), ignoreEncodingErrors);
        }

        public Writer openWriter() throws IOException {
            throw new UnsupportedOperationException();
        }

        /** @deprecated see bug 6410637 */
        @Deprecated
        public String getName() {
            return name;
        }

        public boolean isNameCompatible(String cn, JavaFileObject.Kind k) {
            cn.getClass(); // null check
            if (k == Kind.OTHER && getKind() != k)
                return false;
            return name.equals(cn + k.extension);
        }

        /** @deprecated see bug 6410637 */
        @Deprecated
        public String getPath() {
            return zdir.getName() + "(" + entry + ")";
        }

        public long getLastModified() {
            return entry.getTime();
        }

        public boolean delete() {
            throw new UnsupportedOperationException();
        }

        public CharBuffer getCharContent(boolean ignoreEncodingErrors) throws IOException {
            SoftReference<CharBuffer> r = contentCache.get(this);
            CharBuffer cb = (r == null ? null : r.get());
            if (cb == null) {
                InputStream in = zdir.getInputStream(entry);
                try {
                    ByteBuffer bb = makeByteBuffer(in);
                    JavaFileObject prev = log.useSource(this);
                    try {
                        cb = decode(bb, ignoreEncodingErrors);
                    } finally {
                        log.useSource(prev);
                    }
                    byteBufferCache.put(bb); // save for next time
                    if (!ignoreEncodingErrors)
                        contentCache.put(this, new SoftReference<CharBuffer>(cb));
                } finally {
                    in.close();
                }
            }
            return cb;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ZipFileObject))
                return false;
            ZipFileObject o = (ZipFileObject) other;
            return zdir.equals(o.zdir) || name.equals(o.name);
        }

        @Override
        public int hashCode() {
            return zdir.hashCode() + name.hashCode();
        }

        public String getZipName() {
            return zdir.getName();
        }

        public String getZipEntryName() {
            return entry.getName();
        }

        public URI toUri() {
            String zipName = new File(getZipName()).toURI().normalize().getPath();
            String entryName = getZipEntryName();
            return URI.create("jar:" + zipName + "!" + entryName);
        }

    }

    /**
     * A subclass of JavaFileObject representing zip entries using the com.sun.tools.javac.zip.ZipFileIndex implementation.
     */
    public class ZipFileIndexFileObject extends BaseFileObject {

            /** The entry's name.
         */
        private String name;

        /** The zipfile containing the entry.
         */
        ZipFileIndex zfIndex;

        /** The underlying zip entry object.
         */
        ZipFileIndexEntry entry;

        /** The InputStream for this zip entry (file.)
         */
        InputStream inputStream = null;

        /** The name of the zip file where this entry resides.
         */
        String zipName;

        JavacFileManager defFileManager = null;

        public ZipFileIndexFileObject(JavacFileManager fileManager, ZipFileIndex zfIndex, ZipFileIndexEntry entry, String zipFileName) {
            super();
            this.name = entry.getFileName();
            this.zfIndex = zfIndex;
            this.entry = entry;
            this.zipName = zipFileName;
            defFileManager = fileManager;
        }

        public InputStream openInputStream() throws IOException {

            if (inputStream == null) {
                inputStream = new ByteArrayInputStream(read());
            }
            return inputStream;
        }

        protected CharsetDecoder getDecoder(boolean ignoreEncodingErrors) {
            return JavacFileManager.this.getDecoder(getEncodingName(), ignoreEncodingErrors);
        }

        public OutputStream openOutputStream() throws IOException {
            throw new UnsupportedOperationException();
        }

        public Writer openWriter() throws IOException {
            throw new UnsupportedOperationException();
        }

        /** @deprecated see bug 6410637 */
        @Deprecated
        public String getName() {
            return name;
        }

        public boolean isNameCompatible(String cn, JavaFileObject.Kind k) {
            cn.getClass(); // null check
            if (k == Kind.OTHER && getKind() != k)
                return false;
            return name.equals(cn + k.extension);
        }

        /** @deprecated see bug 6410637 */
        @Deprecated
        public String getPath() {
            return entry.getName() + "(" + entry + ")";
        }

        public long getLastModified() {
            return entry.getLastModified();
        }

        public boolean delete() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ZipFileIndexFileObject))
                return false;
            ZipFileIndexFileObject o = (ZipFileIndexFileObject) other;
            return entry.equals(o.entry);
        }

        @Override
        public int hashCode() {
            return zipName.hashCode() + (name.hashCode() << 10);
        }

        public String getZipName() {
            return zipName;
        }

        public String getZipEntryName() {
            return entry.getName();
        }

        public URI toUri() {
            String zipName = new File(getZipName()).toURI().normalize().getPath();
            String entryName = getZipEntryName();
            if (File.separatorChar != '/') {
                entryName = entryName.replace(File.separatorChar, '/');
            }
            return URI.create("jar:" + zipName + "!" + entryName);
        }

        private byte[] read() throws IOException {
            if (entry == null) {
                entry = zfIndex.getZipIndexEntry(name);
                if (entry == null)
                  throw new FileNotFoundException();
            }
            return zfIndex.read(entry);
        }

        public CharBuffer getCharContent(boolean ignoreEncodingErrors) throws IOException {
            SoftReference<CharBuffer> r = defFileManager.contentCache.get(this);
            CharBuffer cb = (r == null ? null : r.get());
            if (cb == null) {
                InputStream in = new ByteArrayInputStream(zfIndex.read(entry));
                try {
                    ByteBuffer bb = makeByteBuffer(in);
                    JavaFileObject prev = log.useSource(this);
                    try {
                        cb = decode(bb, ignoreEncodingErrors);
                    } finally {
                        log.useSource(prev);
                    }
                    byteBufferCache.put(bb); // save for next time
                    if (!ignoreEncodingErrors)
                        defFileManager.contentCache.put(this, new SoftReference<CharBuffer>(cb));
                } finally {
                    in.close();
                }
            }
            return cb;
        }
    }

    public class ZipFileIndexArchive implements Archive {
        private final ZipFileIndex zfIndex;
        private JavacFileManager fileManager;

        public ZipFileIndexArchive(JavacFileManager fileManager, ZipFileIndex zdir) throws IOException {
            this.fileManager = fileManager;
            this.zfIndex = zdir;
        }

        public boolean contains(String name) {
            return zfIndex.contains(name);
        }

        public com.sun.tools.javac.util.List<String> getFiles(String subdirectory) {
              return zfIndex.getFiles(((subdirectory.endsWith("/") || subdirectory.endsWith("\\"))? subdirectory.substring(0, subdirectory.length() - 1) : subdirectory));
        }

        public JavaFileObject getFileObject(String subdirectory, String file) {
            String fullZipFileName = subdirectory + file;
            ZipFileIndexEntry entry = zfIndex.getZipIndexEntry(fullZipFileName);
            JavaFileObject ret = new ZipFileIndexFileObject(fileManager, zfIndex, entry, zfIndex.getZipFile().getPath());
            return ret;
        }

        public Set<String> getSubdirectories() {
            return zfIndex.getAllDirectories();
        }

        public void close() throws IOException {
            zfIndex.close();
        }
    }
}
