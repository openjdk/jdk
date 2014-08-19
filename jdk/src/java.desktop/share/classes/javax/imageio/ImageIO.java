/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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

package javax.imageio;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FilePermission;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.AccessController;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ImageReaderWriterSpi;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.spi.ImageInputStreamSpi;
import javax.imageio.spi.ImageOutputStreamSpi;
import javax.imageio.spi.ImageTranscoderSpi;
import javax.imageio.spi.ServiceRegistry;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import sun.awt.AppContext;
import sun.security.action.GetPropertyAction;

/**
 * A class containing static convenience methods for locating
 * <code>ImageReader</code>s and <code>ImageWriter</code>s, and
 * performing simple encoding and decoding.
 *
 */
public final class ImageIO {

    private static final IIORegistry theRegistry =
        IIORegistry.getDefaultInstance();

    /**
     * Constructor is private to prevent instantiation.
     */
    private ImageIO() {}

    /**
     * Scans for plug-ins on the application class path,
     * loads their service provider classes, and registers a service
     * provider instance for each one found with the
     * <code>IIORegistry</code>.
     *
     * <p>This method is needed because the application class path can
     * theoretically change, or additional plug-ins may become available.
     * Rather than re-scanning the classpath on every invocation of the
     * API, the class path is scanned automatically only on the first
     * invocation. Clients can call this method to prompt a re-scan.
     * Thus this method need only be invoked by sophisticated applications
     * which dynamically make new plug-ins available at runtime.
     *
     * <p> The <code>getResources</code> method of the context
     * <code>ClassLoader</code> is used locate JAR files containing
     * files named
     * <code>META-INF/services/javax.imageio.spi.</code><i>classname</i>,
     * where <i>classname</i> is one of <code>ImageReaderSpi</code>,
     * <code>ImageWriterSpi</code>, <code>ImageTranscoderSpi</code>,
     * <code>ImageInputStreamSpi</code>, or
     * <code>ImageOutputStreamSpi</code>, along the application class
     * path.
     *
     * <p> The contents of the located files indicate the names of
     * actual implementation classes which implement the
     * aforementioned service provider interfaces; the default class
     * loader is then used to load each of these classes and to
     * instantiate an instance of each class, which is then placed
     * into the registry for later retrieval.
     *
     * <p> The exact set of locations searched depends on the
     * implementation of the Java runtime environment.
     *
     * @see ClassLoader#getResources
     */
    public static void scanForPlugins() {
        theRegistry.registerApplicationClasspathSpis();
    }

    // ImageInputStreams

    /**
     * A class to hold information about caching.  Each
     * <code>ThreadGroup</code> will have its own copy
     * via the <code>AppContext</code> mechanism.
     */
    static class CacheInfo {
        boolean useCache = true;
        File cacheDirectory = null;
        Boolean hasPermission = null;

        public CacheInfo() {}

        public boolean getUseCache() {
            return useCache;
        }

        public void setUseCache(boolean useCache) {
            this.useCache = useCache;
        }

        public File getCacheDirectory() {
            return cacheDirectory;
        }

        public void setCacheDirectory(File cacheDirectory) {
            this.cacheDirectory = cacheDirectory;
        }

        public Boolean getHasPermission() {
            return hasPermission;
        }

        public void setHasPermission(Boolean hasPermission) {
            this.hasPermission = hasPermission;
        }
    }

    /**
     * Returns the <code>CacheInfo</code> object associated with this
     * <code>ThreadGroup</code>.
     */
    private static synchronized CacheInfo getCacheInfo() {
        AppContext context = AppContext.getAppContext();
        CacheInfo info = (CacheInfo)context.get(CacheInfo.class);
        if (info == null) {
            info = new CacheInfo();
            context.put(CacheInfo.class, info);
        }
        return info;
    }

    /**
     * Returns the default temporary (cache) directory as defined by the
     * java.io.tmpdir system property.
     */
    private static String getTempDir() {
        GetPropertyAction a = new GetPropertyAction("java.io.tmpdir");
        return AccessController.doPrivileged(a);
    }

    /**
     * Determines whether the caller has write access to the cache
     * directory, stores the result in the <code>CacheInfo</code> object,
     * and returns the decision.  This method helps to prevent mysterious
     * SecurityExceptions to be thrown when this convenience class is used
     * in an applet, for example.
     */
    private static boolean hasCachePermission() {
        Boolean hasPermission = getCacheInfo().getHasPermission();

        if (hasPermission != null) {
            return hasPermission.booleanValue();
        } else {
            try {
                SecurityManager security = System.getSecurityManager();
                if (security != null) {
                    File cachedir = getCacheDirectory();
                    String cachepath;

                    if (cachedir != null) {
                        cachepath = cachedir.getPath();
                    } else {
                        cachepath = getTempDir();

                        if (cachepath == null || cachepath.isEmpty()) {
                            getCacheInfo().setHasPermission(Boolean.FALSE);
                            return false;
                        }
                    }

                    // we have to check whether we can read, write,
                    // and delete cache files.
                    // So, compose cache file path and check it.
                    String filepath = cachepath;
                    if (!filepath.endsWith(File.separator)) {
                        filepath += File.separator;
                    }
                    filepath += "*";

                    security.checkPermission(new FilePermission(filepath, "read, write, delete"));
                }
            } catch (SecurityException e) {
                getCacheInfo().setHasPermission(Boolean.FALSE);
                return false;
            }

            getCacheInfo().setHasPermission(Boolean.TRUE);
            return true;
        }
    }

    /**
     * Sets a flag indicating whether a disk-based cache file should
     * be used when creating <code>ImageInputStream</code>s and
     * <code>ImageOutputStream</code>s.
     *
     * <p> When reading from a standard <code>InputStream</code>, it
     * may be necessary to save previously read information in a cache
     * since the underlying stream does not allow data to be re-read.
     * Similarly, when writing to a standard
     * <code>OutputStream</code>, a cache may be used to allow a
     * previously written value to be changed before flushing it to
     * the final destination.
     *
     * <p> The cache may reside in main memory or on disk.  Setting
     * this flag to <code>false</code> disallows the use of disk for
     * future streams, which may be advantageous when working with
     * small images, as the overhead of creating and destroying files
     * is removed.
     *
     * <p> On startup, the value is set to <code>true</code>.
     *
     * @param useCache a <code>boolean</code> indicating whether a
     * cache file should be used, in cases where it is optional.
     *
     * @see #getUseCache
     */
    public static void setUseCache(boolean useCache) {
        getCacheInfo().setUseCache(useCache);
    }

    /**
     * Returns the current value set by <code>setUseCache</code>, or
     * <code>true</code> if no explicit setting has been made.
     *
     * @return true if a disk-based cache may be used for
     * <code>ImageInputStream</code>s and
     * <code>ImageOutputStream</code>s.
     *
     * @see #setUseCache
     */
    public static boolean getUseCache() {
        return getCacheInfo().getUseCache();
    }

    /**
     * Sets the directory where cache files are to be created.  A
     * value of <code>null</code> indicates that the system-dependent
     * default temporary-file directory is to be used.  If
     * <code>getUseCache</code> returns false, this value is ignored.
     *
     * @param cacheDirectory a <code>File</code> specifying a directory.
     *
     * @see File#createTempFile(String, String, File)
     *
     * @exception SecurityException if the security manager denies
     * access to the directory.
     * @exception IllegalArgumentException if <code>cacheDir</code> is
     * non-<code>null</code> but is not a directory.
     *
     * @see #getCacheDirectory
     */
    public static void setCacheDirectory(File cacheDirectory) {
        if ((cacheDirectory != null) && !(cacheDirectory.isDirectory())) {
            throw new IllegalArgumentException("Not a directory!");
        }
        getCacheInfo().setCacheDirectory(cacheDirectory);
        getCacheInfo().setHasPermission(null);
    }

    /**
     * Returns the current value set by
     * <code>setCacheDirectory</code>, or <code>null</code> if no
     * explicit setting has been made.
     *
     * @return a <code>File</code> indicating the directory where
     * cache files will be created, or <code>null</code> to indicate
     * the system-dependent default temporary-file directory.
     *
     * @see #setCacheDirectory
     */
    public static File getCacheDirectory() {
        return getCacheInfo().getCacheDirectory();
    }

    /**
     * Returns an <code>ImageInputStream</code> that will take its
     * input from the given <code>Object</code>.  The set of
     * <code>ImageInputStreamSpi</code>s registered with the
     * <code>IIORegistry</code> class is queried and the first one
     * that is able to take input from the supplied object is used to
     * create the returned <code>ImageInputStream</code>.  If no
     * suitable <code>ImageInputStreamSpi</code> exists,
     * <code>null</code> is returned.
     *
     * <p> The current cache settings from <code>getUseCache</code>and
     * <code>getCacheDirectory</code> will be used to control caching.
     *
     * @param input an <code>Object</code> to be used as an input
     * source, such as a <code>File</code>, readable
     * <code>RandomAccessFile</code>, or <code>InputStream</code>.
     *
     * @return an <code>ImageInputStream</code>, or <code>null</code>.
     *
     * @exception IllegalArgumentException if <code>input</code>
     * is <code>null</code>.
     * @exception IOException if a cache file is needed but cannot be
     * created.
     *
     * @see javax.imageio.spi.ImageInputStreamSpi
     */
    public static ImageInputStream createImageInputStream(Object input)
        throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("input == null!");
        }

        Iterator<ImageInputStreamSpi> iter;
        // Ensure category is present
        try {
            iter = theRegistry.getServiceProviders(ImageInputStreamSpi.class,
                                                   true);
        } catch (IllegalArgumentException e) {
            return null;
        }

        boolean usecache = getUseCache() && hasCachePermission();

        while (iter.hasNext()) {
            ImageInputStreamSpi spi = iter.next();
            if (spi.getInputClass().isInstance(input)) {
                try {
                    return spi.createInputStreamInstance(input,
                                                         usecache,
                                                         getCacheDirectory());
                } catch (IOException e) {
                    throw new IIOException("Can't create cache file!", e);
                }
            }
        }

        return null;
    }

    // ImageOutputStreams

    /**
     * Returns an <code>ImageOutputStream</code> that will send its
     * output to the given <code>Object</code>.  The set of
     * <code>ImageOutputStreamSpi</code>s registered with the
     * <code>IIORegistry</code> class is queried and the first one
     * that is able to send output from the supplied object is used to
     * create the returned <code>ImageOutputStream</code>.  If no
     * suitable <code>ImageOutputStreamSpi</code> exists,
     * <code>null</code> is returned.
     *
     * <p> The current cache settings from <code>getUseCache</code>and
     * <code>getCacheDirectory</code> will be used to control caching.
     *
     * @param output an <code>Object</code> to be used as an output
     * destination, such as a <code>File</code>, writable
     * <code>RandomAccessFile</code>, or <code>OutputStream</code>.
     *
     * @return an <code>ImageOutputStream</code>, or
     * <code>null</code>.
     *
     * @exception IllegalArgumentException if <code>output</code> is
     * <code>null</code>.
     * @exception IOException if a cache file is needed but cannot be
     * created.
     *
     * @see javax.imageio.spi.ImageOutputStreamSpi
     */
    public static ImageOutputStream createImageOutputStream(Object output)
        throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("output == null!");
        }

        Iterator<ImageOutputStreamSpi> iter;
        // Ensure category is present
        try {
            iter = theRegistry.getServiceProviders(ImageOutputStreamSpi.class,
                                                   true);
        } catch (IllegalArgumentException e) {
            return null;
        }

        boolean usecache = getUseCache() && hasCachePermission();

        while (iter.hasNext()) {
            ImageOutputStreamSpi spi = iter.next();
            if (spi.getOutputClass().isInstance(output)) {
                try {
                    return spi.createOutputStreamInstance(output,
                                                          usecache,
                                                          getCacheDirectory());
                } catch (IOException e) {
                    throw new IIOException("Can't create cache file!", e);
                }
            }
        }

        return null;
    }

    private static enum SpiInfo {
        FORMAT_NAMES {
            @Override
            String[] info(ImageReaderWriterSpi spi) {
                return spi.getFormatNames();
            }
        },
        MIME_TYPES {
            @Override
            String[] info(ImageReaderWriterSpi spi) {
                return spi.getMIMETypes();
            }
        },
        FILE_SUFFIXES {
            @Override
            String[] info(ImageReaderWriterSpi spi) {
                return spi.getFileSuffixes();
            }
        };

        abstract String[] info(ImageReaderWriterSpi spi);
    }

    private static <S extends ImageReaderWriterSpi>
        String[] getReaderWriterInfo(Class<S> spiClass, SpiInfo spiInfo)
    {
        // Ensure category is present
        Iterator<S> iter;
        try {
            iter = theRegistry.getServiceProviders(spiClass, true);
        } catch (IllegalArgumentException e) {
            return new String[0];
        }

        HashSet<String> s = new HashSet<String>();
        while (iter.hasNext()) {
            ImageReaderWriterSpi spi = iter.next();
            Collections.addAll(s, spiInfo.info(spi));
        }

        return s.toArray(new String[s.size()]);
    }

    // Readers

    /**
     * Returns an array of <code>String</code>s listing all of the
     * informal format names understood by the current set of registered
     * readers.
     *
     * @return an array of <code>String</code>s.
     */
    public static String[] getReaderFormatNames() {
        return getReaderWriterInfo(ImageReaderSpi.class,
                                   SpiInfo.FORMAT_NAMES);
    }

    /**
     * Returns an array of <code>String</code>s listing all of the
     * MIME types understood by the current set of registered
     * readers.
     *
     * @return an array of <code>String</code>s.
     */
    public static String[] getReaderMIMETypes() {
        return getReaderWriterInfo(ImageReaderSpi.class,
                                   SpiInfo.MIME_TYPES);
    }

    /**
     * Returns an array of <code>String</code>s listing all of the
     * file suffixes associated with the formats understood
     * by the current set of registered readers.
     *
     * @return an array of <code>String</code>s.
     * @since 1.6
     */
    public static String[] getReaderFileSuffixes() {
        return getReaderWriterInfo(ImageReaderSpi.class,
                                   SpiInfo.FILE_SUFFIXES);
    }

    static class ImageReaderIterator implements Iterator<ImageReader> {
        // Contains ImageReaderSpis
        private Iterator<ImageReaderSpi> iter;

        public ImageReaderIterator(Iterator<ImageReaderSpi> iter) {
            this.iter = iter;
        }

        public boolean hasNext() {
            return iter.hasNext();
        }

        public ImageReader next() {
            ImageReaderSpi spi = null;
            try {
                spi = iter.next();
                return spi.createReaderInstance();
            } catch (IOException e) {
                // Deregister the spi in this case, but only as
                // an ImageReaderSpi
                theRegistry.deregisterServiceProvider(spi, ImageReaderSpi.class);
            }
            return null;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    static class CanDecodeInputFilter
        implements ServiceRegistry.Filter {

        Object input;

        public CanDecodeInputFilter(Object input) {
            this.input = input;
        }

        public boolean filter(Object elt) {
            try {
                ImageReaderSpi spi = (ImageReaderSpi)elt;
                ImageInputStream stream = null;
                if (input instanceof ImageInputStream) {
                    stream = (ImageInputStream)input;
                }

                // Perform mark/reset as a defensive measure
                // even though plug-ins are supposed to take
                // care of it.
                boolean canDecode = false;
                if (stream != null) {
                    stream.mark();
                }
                canDecode = spi.canDecodeInput(input);
                if (stream != null) {
                    stream.reset();
                }

                return canDecode;
            } catch (IOException e) {
                return false;
            }
        }
    }

    static class CanEncodeImageAndFormatFilter
        implements ServiceRegistry.Filter {

        ImageTypeSpecifier type;
        String formatName;

        public CanEncodeImageAndFormatFilter(ImageTypeSpecifier type,
                                             String formatName) {
            this.type = type;
            this.formatName = formatName;
        }

        public boolean filter(Object elt) {
            ImageWriterSpi spi = (ImageWriterSpi)elt;
            return Arrays.asList(spi.getFormatNames()).contains(formatName) &&
                spi.canEncodeImage(type);
        }
    }

    static class ContainsFilter
        implements ServiceRegistry.Filter {

        Method method;
        String name;

        // method returns an array of Strings
        public ContainsFilter(Method method,
                              String name) {
            this.method = method;
            this.name = name;
        }

        public boolean filter(Object elt) {
            try {
                return contains((String[])method.invoke(elt), name);
            } catch (Exception e) {
                return false;
            }
        }
    }

    /**
     * Returns an <code>Iterator</code> containing all currently
     * registered <code>ImageReader</code>s that claim to be able to
     * decode the supplied <code>Object</code>, typically an
     * <code>ImageInputStream</code>.
     *
     * <p> The stream position is left at its prior position upon
     * exit from this method.
     *
     * @param input an <code>ImageInputStream</code> or other
     * <code>Object</code> containing encoded image data.
     *
     * @return an <code>Iterator</code> containing <code>ImageReader</code>s.
     *
     * @exception IllegalArgumentException if <code>input</code> is
     * <code>null</code>.
     *
     * @see javax.imageio.spi.ImageReaderSpi#canDecodeInput
     */
    public static Iterator<ImageReader> getImageReaders(Object input) {
        if (input == null) {
            throw new IllegalArgumentException("input == null!");
        }
        Iterator<ImageReaderSpi> iter;
        // Ensure category is present
        try {
            iter = theRegistry.getServiceProviders(ImageReaderSpi.class,
                                              new CanDecodeInputFilter(input),
                                              true);
        } catch (IllegalArgumentException e) {
            return Collections.emptyIterator();
        }

        return new ImageReaderIterator(iter);
    }

    private static Method readerFormatNamesMethod;
    private static Method readerFileSuffixesMethod;
    private static Method readerMIMETypesMethod;
    private static Method writerFormatNamesMethod;
    private static Method writerFileSuffixesMethod;
    private static Method writerMIMETypesMethod;

    static {
        try {
            readerFormatNamesMethod =
                ImageReaderSpi.class.getMethod("getFormatNames");
            readerFileSuffixesMethod =
                ImageReaderSpi.class.getMethod("getFileSuffixes");
            readerMIMETypesMethod =
                ImageReaderSpi.class.getMethod("getMIMETypes");

            writerFormatNamesMethod =
                ImageWriterSpi.class.getMethod("getFormatNames");
            writerFileSuffixesMethod =
                ImageWriterSpi.class.getMethod("getFileSuffixes");
            writerMIMETypesMethod =
                ImageWriterSpi.class.getMethod("getMIMETypes");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns an <code>Iterator</code> containing all currently
     * registered <code>ImageReader</code>s that claim to be able to
     * decode the named format.
     *
     * @param formatName a <code>String</code> containing the informal
     * name of a format (<i>e.g.</i>, "jpeg" or "tiff".
     *
     * @return an <code>Iterator</code> containing
     * <code>ImageReader</code>s.
     *
     * @exception IllegalArgumentException if <code>formatName</code>
     * is <code>null</code>.
     *
     * @see javax.imageio.spi.ImageReaderSpi#getFormatNames
     */
    public static Iterator<ImageReader>
        getImageReadersByFormatName(String formatName)
    {
        if (formatName == null) {
            throw new IllegalArgumentException("formatName == null!");
        }
        Iterator<ImageReaderSpi> iter;
        // Ensure category is present
        try {
            iter = theRegistry.getServiceProviders(ImageReaderSpi.class,
                                    new ContainsFilter(readerFormatNamesMethod,
                                                       formatName),
                                                true);
        } catch (IllegalArgumentException e) {
            return Collections.emptyIterator();
        }
        return new ImageReaderIterator(iter);
    }

    /**
     * Returns an <code>Iterator</code> containing all currently
     * registered <code>ImageReader</code>s that claim to be able to
     * decode files with the given suffix.
     *
     * @param fileSuffix a <code>String</code> containing a file
     * suffix (<i>e.g.</i>, "jpg" or "tiff").
     *
     * @return an <code>Iterator</code> containing
     * <code>ImageReader</code>s.
     *
     * @exception IllegalArgumentException if <code>fileSuffix</code>
     * is <code>null</code>.
     *
     * @see javax.imageio.spi.ImageReaderSpi#getFileSuffixes
     */
    public static Iterator<ImageReader>
        getImageReadersBySuffix(String fileSuffix)
    {
        if (fileSuffix == null) {
            throw new IllegalArgumentException("fileSuffix == null!");
        }
        // Ensure category is present
        Iterator<ImageReaderSpi> iter;
        try {
            iter = theRegistry.getServiceProviders(ImageReaderSpi.class,
                                   new ContainsFilter(readerFileSuffixesMethod,
                                                      fileSuffix),
                                              true);
        } catch (IllegalArgumentException e) {
            return Collections.emptyIterator();
        }
        return new ImageReaderIterator(iter);
    }

    /**
     * Returns an <code>Iterator</code> containing all currently
     * registered <code>ImageReader</code>s that claim to be able to
     * decode files with the given MIME type.
     *
     * @param MIMEType a <code>String</code> containing a file
     * suffix (<i>e.g.</i>, "image/jpeg" or "image/x-bmp").
     *
     * @return an <code>Iterator</code> containing
     * <code>ImageReader</code>s.
     *
     * @exception IllegalArgumentException if <code>MIMEType</code> is
     * <code>null</code>.
     *
     * @see javax.imageio.spi.ImageReaderSpi#getMIMETypes
     */
    public static Iterator<ImageReader>
        getImageReadersByMIMEType(String MIMEType)
    {
        if (MIMEType == null) {
            throw new IllegalArgumentException("MIMEType == null!");
        }
        // Ensure category is present
        Iterator<ImageReaderSpi> iter;
        try {
            iter = theRegistry.getServiceProviders(ImageReaderSpi.class,
                                      new ContainsFilter(readerMIMETypesMethod,
                                                         MIMEType),
                                              true);
        } catch (IllegalArgumentException e) {
            return Collections.emptyIterator();
        }
        return new ImageReaderIterator(iter);
    }

    // Writers

    /**
     * Returns an array of <code>String</code>s listing all of the
     * informal format names understood by the current set of registered
     * writers.
     *
     * @return an array of <code>String</code>s.
     */
    public static String[] getWriterFormatNames() {
        return getReaderWriterInfo(ImageWriterSpi.class,
                                   SpiInfo.FORMAT_NAMES);
    }

    /**
     * Returns an array of <code>String</code>s listing all of the
     * MIME types understood by the current set of registered
     * writers.
     *
     * @return an array of <code>String</code>s.
     */
    public static String[] getWriterMIMETypes() {
        return getReaderWriterInfo(ImageWriterSpi.class,
                                   SpiInfo.MIME_TYPES);
    }

    /**
     * Returns an array of <code>String</code>s listing all of the
     * file suffixes associated with the formats understood
     * by the current set of registered writers.
     *
     * @return an array of <code>String</code>s.
     * @since 1.6
     */
    public static String[] getWriterFileSuffixes() {
        return getReaderWriterInfo(ImageWriterSpi.class,
                                   SpiInfo.FILE_SUFFIXES);
    }

    static class ImageWriterIterator implements Iterator<ImageWriter> {
        // Contains ImageWriterSpis
        private Iterator<ImageWriterSpi> iter;

        public ImageWriterIterator(Iterator<ImageWriterSpi> iter) {
            this.iter = iter;
        }

        public boolean hasNext() {
            return iter.hasNext();
        }

        public ImageWriter next() {
            ImageWriterSpi spi = null;
            try {
                spi = iter.next();
                return spi.createWriterInstance();
            } catch (IOException e) {
                // Deregister the spi in this case, but only as a writerSpi
                theRegistry.deregisterServiceProvider(spi, ImageWriterSpi.class);
            }
            return null;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static boolean contains(String[] names, String name) {
        for (int i = 0; i < names.length; i++) {
            if (name.equalsIgnoreCase(names[i])) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns an <code>Iterator</code> containing all currently
     * registered <code>ImageWriter</code>s that claim to be able to
     * encode the named format.
     *
     * @param formatName a <code>String</code> containing the informal
     * name of a format (<i>e.g.</i>, "jpeg" or "tiff".
     *
     * @return an <code>Iterator</code> containing
     * <code>ImageWriter</code>s.
     *
     * @exception IllegalArgumentException if <code>formatName</code> is
     * <code>null</code>.
     *
     * @see javax.imageio.spi.ImageWriterSpi#getFormatNames
     */
    public static Iterator<ImageWriter>
        getImageWritersByFormatName(String formatName)
    {
        if (formatName == null) {
            throw new IllegalArgumentException("formatName == null!");
        }
        Iterator<ImageWriterSpi> iter;
        // Ensure category is present
        try {
            iter = theRegistry.getServiceProviders(ImageWriterSpi.class,
                                    new ContainsFilter(writerFormatNamesMethod,
                                                       formatName),
                                            true);
        } catch (IllegalArgumentException e) {
            return Collections.emptyIterator();
        }
        return new ImageWriterIterator(iter);
    }

    /**
     * Returns an <code>Iterator</code> containing all currently
     * registered <code>ImageWriter</code>s that claim to be able to
     * encode files with the given suffix.
     *
     * @param fileSuffix a <code>String</code> containing a file
     * suffix (<i>e.g.</i>, "jpg" or "tiff").
     *
     * @return an <code>Iterator</code> containing <code>ImageWriter</code>s.
     *
     * @exception IllegalArgumentException if <code>fileSuffix</code> is
     * <code>null</code>.
     *
     * @see javax.imageio.spi.ImageWriterSpi#getFileSuffixes
     */
    public static Iterator<ImageWriter>
        getImageWritersBySuffix(String fileSuffix)
    {
        if (fileSuffix == null) {
            throw new IllegalArgumentException("fileSuffix == null!");
        }
        Iterator<ImageWriterSpi> iter;
        // Ensure category is present
        try {
            iter = theRegistry.getServiceProviders(ImageWriterSpi.class,
                                   new ContainsFilter(writerFileSuffixesMethod,
                                                      fileSuffix),
                                            true);
        } catch (IllegalArgumentException e) {
            return Collections.emptyIterator();
        }
        return new ImageWriterIterator(iter);
    }

    /**
     * Returns an <code>Iterator</code> containing all currently
     * registered <code>ImageWriter</code>s that claim to be able to
     * encode files with the given MIME type.
     *
     * @param MIMEType a <code>String</code> containing a file
     * suffix (<i>e.g.</i>, "image/jpeg" or "image/x-bmp").
     *
     * @return an <code>Iterator</code> containing <code>ImageWriter</code>s.
     *
     * @exception IllegalArgumentException if <code>MIMEType</code> is
     * <code>null</code>.
     *
     * @see javax.imageio.spi.ImageWriterSpi#getMIMETypes
     */
    public static Iterator<ImageWriter>
        getImageWritersByMIMEType(String MIMEType)
    {
        if (MIMEType == null) {
            throw new IllegalArgumentException("MIMEType == null!");
        }
        Iterator<ImageWriterSpi> iter;
        // Ensure category is present
        try {
            iter = theRegistry.getServiceProviders(ImageWriterSpi.class,
                                      new ContainsFilter(writerMIMETypesMethod,
                                                         MIMEType),
                                            true);
        } catch (IllegalArgumentException e) {
            return Collections.emptyIterator();
        }
        return new ImageWriterIterator(iter);
    }

    /**
     * Returns an <code>ImageWriter</code>corresponding to the given
     * <code>ImageReader</code>, if there is one, or <code>null</code>
     * if the plug-in for this <code>ImageReader</code> does not
     * specify a corresponding <code>ImageWriter</code>, or if the
     * given <code>ImageReader</code> is not registered.  This
     * mechanism may be used to obtain an <code>ImageWriter</code>
     * that will understand the internal structure of non-pixel
     * metadata (as encoded by <code>IIOMetadata</code> objects)
     * generated by the <code>ImageReader</code>.  By obtaining this
     * data from the <code>ImageReader</code> and passing it on to the
     * <code>ImageWriter</code> obtained with this method, a client
     * program can read an image, modify it in some way, and write it
     * back out preserving all metadata, without having to understand
     * anything about the structure of the metadata, or even about
     * the image format.  Note that this method returns the
     * "preferred" writer, which is the first in the list returned by
     * <code>javax.imageio.spi.ImageReaderSpi.getImageWriterSpiNames()</code>.
     *
     * @param reader an instance of a registered <code>ImageReader</code>.
     *
     * @return an <code>ImageWriter</code>, or null.
     *
     * @exception IllegalArgumentException if <code>reader</code> is
     * <code>null</code>.
     *
     * @see #getImageReader(ImageWriter)
     * @see javax.imageio.spi.ImageReaderSpi#getImageWriterSpiNames()
     */
    public static ImageWriter getImageWriter(ImageReader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("reader == null!");
        }

        ImageReaderSpi readerSpi = reader.getOriginatingProvider();
        if (readerSpi == null) {
            Iterator<ImageReaderSpi> readerSpiIter;
            // Ensure category is present
            try {
                readerSpiIter =
                    theRegistry.getServiceProviders(ImageReaderSpi.class,
                                                    false);
            } catch (IllegalArgumentException e) {
                return null;
            }

            while (readerSpiIter.hasNext()) {
                ImageReaderSpi temp = readerSpiIter.next();
                if (temp.isOwnReader(reader)) {
                    readerSpi = temp;
                    break;
                }
            }
            if (readerSpi == null) {
                return null;
            }
        }

        String[] writerNames = readerSpi.getImageWriterSpiNames();
        if (writerNames == null) {
            return null;
        }

        Class<?> writerSpiClass = null;
        try {
            writerSpiClass = Class.forName(writerNames[0], true,
                                           ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }

        ImageWriterSpi writerSpi = (ImageWriterSpi)
            theRegistry.getServiceProviderByClass(writerSpiClass);
        if (writerSpi == null) {
            return null;
        }

        try {
            return writerSpi.createWriterInstance();
        } catch (IOException e) {
            // Deregister the spi in this case, but only as a writerSpi
            theRegistry.deregisterServiceProvider(writerSpi,
                                                  ImageWriterSpi.class);
            return null;
        }
    }

    /**
     * Returns an <code>ImageReader</code>corresponding to the given
     * <code>ImageWriter</code>, if there is one, or <code>null</code>
     * if the plug-in for this <code>ImageWriter</code> does not
     * specify a corresponding <code>ImageReader</code>, or if the
     * given <code>ImageWriter</code> is not registered.  This method
     * is provided principally for symmetry with
     * <code>getImageWriter(ImageReader)</code>.  Note that this
     * method returns the "preferred" reader, which is the first in
     * the list returned by
     * javax.imageio.spi.ImageWriterSpi.<code>getImageReaderSpiNames()</code>.
     *
     * @param writer an instance of a registered <code>ImageWriter</code>.
     *
     * @return an <code>ImageReader</code>, or null.
     *
     * @exception IllegalArgumentException if <code>writer</code> is
     * <code>null</code>.
     *
     * @see #getImageWriter(ImageReader)
     * @see javax.imageio.spi.ImageWriterSpi#getImageReaderSpiNames()
     */
    public static ImageReader getImageReader(ImageWriter writer) {
        if (writer == null) {
            throw new IllegalArgumentException("writer == null!");
        }

        ImageWriterSpi writerSpi = writer.getOriginatingProvider();
        if (writerSpi == null) {
            Iterator<ImageWriterSpi> writerSpiIter;
            // Ensure category is present
            try {
                writerSpiIter =
                    theRegistry.getServiceProviders(ImageWriterSpi.class,
                                                    false);
            } catch (IllegalArgumentException e) {
                return null;
            }

            while (writerSpiIter.hasNext()) {
                ImageWriterSpi temp = writerSpiIter.next();
                if (temp.isOwnWriter(writer)) {
                    writerSpi = temp;
                    break;
                }
            }
            if (writerSpi == null) {
                return null;
            }
        }

        String[] readerNames = writerSpi.getImageReaderSpiNames();
        if (readerNames == null) {
            return null;
        }

        Class<?> readerSpiClass = null;
        try {
            readerSpiClass = Class.forName(readerNames[0], true,
                                           ClassLoader.getSystemClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }

        ImageReaderSpi readerSpi = (ImageReaderSpi)
            theRegistry.getServiceProviderByClass(readerSpiClass);
        if (readerSpi == null) {
            return null;
        }

        try {
            return readerSpi.createReaderInstance();
        } catch (IOException e) {
            // Deregister the spi in this case, but only as a readerSpi
            theRegistry.deregisterServiceProvider(readerSpi,
                                                  ImageReaderSpi.class);
            return null;
        }
    }

    /**
     * Returns an <code>Iterator</code> containing all currently
     * registered <code>ImageWriter</code>s that claim to be able to
     * encode images of the given layout (specified using an
     * <code>ImageTypeSpecifier</code>) in the given format.
     *
     * @param type an <code>ImageTypeSpecifier</code> indicating the
     * layout of the image to be written.
     * @param formatName the informal name of the <code>format</code>.
     *
     * @return an <code>Iterator</code> containing <code>ImageWriter</code>s.
     *
     * @exception IllegalArgumentException if any parameter is
     * <code>null</code>.
     *
     * @see javax.imageio.spi.ImageWriterSpi#canEncodeImage(ImageTypeSpecifier)
     */
    public static Iterator<ImageWriter>
        getImageWriters(ImageTypeSpecifier type, String formatName)
    {
        if (type == null) {
            throw new IllegalArgumentException("type == null!");
        }
        if (formatName == null) {
            throw new IllegalArgumentException("formatName == null!");
        }

        Iterator<ImageWriterSpi> iter;
        // Ensure category is present
        try {
            iter = theRegistry.getServiceProviders(ImageWriterSpi.class,
                                 new CanEncodeImageAndFormatFilter(type,
                                                                   formatName),
                                            true);
        } catch (IllegalArgumentException e) {
            return Collections.emptyIterator();
        }

        return new ImageWriterIterator(iter);
    }

    static class ImageTranscoderIterator
        implements Iterator<ImageTranscoder>
    {
        // Contains ImageTranscoderSpis
        public Iterator<ImageTranscoderSpi> iter;

        public ImageTranscoderIterator(Iterator<ImageTranscoderSpi> iter) {
            this.iter = iter;
        }

        public boolean hasNext() {
            return iter.hasNext();
        }

        public ImageTranscoder next() {
            ImageTranscoderSpi spi = null;
            spi = iter.next();
            return spi.createTranscoderInstance();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    static class TranscoderFilter
        implements ServiceRegistry.Filter {

        String readerSpiName;
        String writerSpiName;

        public TranscoderFilter(ImageReaderSpi readerSpi,
                                ImageWriterSpi writerSpi) {
            this.readerSpiName = readerSpi.getClass().getName();
            this.writerSpiName = writerSpi.getClass().getName();
        }

        public boolean filter(Object elt) {
            ImageTranscoderSpi spi = (ImageTranscoderSpi)elt;
            String readerName = spi.getReaderServiceProviderName();
            String writerName = spi.getWriterServiceProviderName();
            return (readerName.equals(readerSpiName) &&
                    writerName.equals(writerSpiName));
        }
    }

    /**
     * Returns an <code>Iterator</code> containing all currently
     * registered <code>ImageTranscoder</code>s that claim to be
     * able to transcode between the metadata of the given
     * <code>ImageReader</code> and <code>ImageWriter</code>.
     *
     * @param reader an <code>ImageReader</code>.
     * @param writer an <code>ImageWriter</code>.
     *
     * @return an <code>Iterator</code> containing
     * <code>ImageTranscoder</code>s.
     *
     * @exception IllegalArgumentException if <code>reader</code> or
     * <code>writer</code> is <code>null</code>.
     */
    public static Iterator<ImageTranscoder>
        getImageTranscoders(ImageReader reader, ImageWriter writer)
    {
        if (reader == null) {
            throw new IllegalArgumentException("reader == null!");
        }
        if (writer == null) {
            throw new IllegalArgumentException("writer == null!");
        }
        ImageReaderSpi readerSpi = reader.getOriginatingProvider();
        ImageWriterSpi writerSpi = writer.getOriginatingProvider();
        ServiceRegistry.Filter filter =
            new TranscoderFilter(readerSpi, writerSpi);

        Iterator<ImageTranscoderSpi> iter;
        // Ensure category is present
        try {
            iter = theRegistry.getServiceProviders(ImageTranscoderSpi.class,
                                            filter, true);
        } catch (IllegalArgumentException e) {
            return Collections.emptyIterator();
        }
        return new ImageTranscoderIterator(iter);
    }

    // All-in-one methods

    /**
     * Returns a <code>BufferedImage</code> as the result of decoding
     * a supplied <code>File</code> with an <code>ImageReader</code>
     * chosen automatically from among those currently registered.
     * The <code>File</code> is wrapped in an
     * <code>ImageInputStream</code>.  If no registered
     * <code>ImageReader</code> claims to be able to read the
     * resulting stream, <code>null</code> is returned.
     *
     * <p> The current cache settings from <code>getUseCache</code>and
     * <code>getCacheDirectory</code> will be used to control caching in the
     * <code>ImageInputStream</code> that is created.
     *
     * <p> Note that there is no <code>read</code> method that takes a
     * filename as a <code>String</code>; use this method instead after
     * creating a <code>File</code> from the filename.
     *
     * <p> This method does not attempt to locate
     * <code>ImageReader</code>s that can read directly from a
     * <code>File</code>; that may be accomplished using
     * <code>IIORegistry</code> and <code>ImageReaderSpi</code>.
     *
     * @param input a <code>File</code> to read from.
     *
     * @return a <code>BufferedImage</code> containing the decoded
     * contents of the input, or <code>null</code>.
     *
     * @exception IllegalArgumentException if <code>input</code> is
     * <code>null</code>.
     * @exception IOException if an error occurs during reading.
     */
    public static BufferedImage read(File input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("input == null!");
        }
        if (!input.canRead()) {
            throw new IIOException("Can't read input file!");
        }

        ImageInputStream stream = createImageInputStream(input);
        if (stream == null) {
            throw new IIOException("Can't create an ImageInputStream!");
        }
        BufferedImage bi = read(stream);
        if (bi == null) {
            stream.close();
        }
        return bi;
    }

    /**
     * Returns a <code>BufferedImage</code> as the result of decoding
     * a supplied <code>InputStream</code> with an <code>ImageReader</code>
     * chosen automatically from among those currently registered.
     * The <code>InputStream</code> is wrapped in an
     * <code>ImageInputStream</code>.  If no registered
     * <code>ImageReader</code> claims to be able to read the
     * resulting stream, <code>null</code> is returned.
     *
     * <p> The current cache settings from <code>getUseCache</code>and
     * <code>getCacheDirectory</code> will be used to control caching in the
     * <code>ImageInputStream</code> that is created.
     *
     * <p> This method does not attempt to locate
     * <code>ImageReader</code>s that can read directly from an
     * <code>InputStream</code>; that may be accomplished using
     * <code>IIORegistry</code> and <code>ImageReaderSpi</code>.
     *
     * <p> This method <em>does not</em> close the provided
     * <code>InputStream</code> after the read operation has completed;
     * it is the responsibility of the caller to close the stream, if desired.
     *
     * @param input an <code>InputStream</code> to read from.
     *
     * @return a <code>BufferedImage</code> containing the decoded
     * contents of the input, or <code>null</code>.
     *
     * @exception IllegalArgumentException if <code>input</code> is
     * <code>null</code>.
     * @exception IOException if an error occurs during reading.
     */
    public static BufferedImage read(InputStream input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("input == null!");
        }

        ImageInputStream stream = createImageInputStream(input);
        BufferedImage bi = read(stream);
        if (bi == null) {
            stream.close();
        }
        return bi;
    }

    /**
     * Returns a <code>BufferedImage</code> as the result of decoding
     * a supplied <code>URL</code> with an <code>ImageReader</code>
     * chosen automatically from among those currently registered.  An
     * <code>InputStream</code> is obtained from the <code>URL</code>,
     * which is wrapped in an <code>ImageInputStream</code>.  If no
     * registered <code>ImageReader</code> claims to be able to read
     * the resulting stream, <code>null</code> is returned.
     *
     * <p> The current cache settings from <code>getUseCache</code>and
     * <code>getCacheDirectory</code> will be used to control caching in the
     * <code>ImageInputStream</code> that is created.
     *
     * <p> This method does not attempt to locate
     * <code>ImageReader</code>s that can read directly from a
     * <code>URL</code>; that may be accomplished using
     * <code>IIORegistry</code> and <code>ImageReaderSpi</code>.
     *
     * @param input a <code>URL</code> to read from.
     *
     * @return a <code>BufferedImage</code> containing the decoded
     * contents of the input, or <code>null</code>.
     *
     * @exception IllegalArgumentException if <code>input</code> is
     * <code>null</code>.
     * @exception IOException if an error occurs during reading.
     */
    public static BufferedImage read(URL input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("input == null!");
        }

        InputStream istream = null;
        try {
            istream = input.openStream();
        } catch (IOException e) {
            throw new IIOException("Can't get input stream from URL!", e);
        }
        ImageInputStream stream = createImageInputStream(istream);
        BufferedImage bi;
        try {
            bi = read(stream);
            if (bi == null) {
                stream.close();
            }
        } finally {
            istream.close();
        }
        return bi;
    }

    /**
     * Returns a <code>BufferedImage</code> as the result of decoding
     * a supplied <code>ImageInputStream</code> with an
     * <code>ImageReader</code> chosen automatically from among those
     * currently registered.  If no registered
     * <code>ImageReader</code> claims to be able to read the stream,
     * <code>null</code> is returned.
     *
     * <p> Unlike most other methods in this class, this method <em>does</em>
     * close the provided <code>ImageInputStream</code> after the read
     * operation has completed, unless <code>null</code> is returned,
     * in which case this method <em>does not</em> close the stream.
     *
     * @param stream an <code>ImageInputStream</code> to read from.
     *
     * @return a <code>BufferedImage</code> containing the decoded
     * contents of the input, or <code>null</code>.
     *
     * @exception IllegalArgumentException if <code>stream</code> is
     * <code>null</code>.
     * @exception IOException if an error occurs during reading.
     */
    public static BufferedImage read(ImageInputStream stream)
        throws IOException {
        if (stream == null) {
            throw new IllegalArgumentException("stream == null!");
        }

        Iterator<ImageReader> iter = getImageReaders(stream);
        if (!iter.hasNext()) {
            return null;
        }

        ImageReader reader = iter.next();
        ImageReadParam param = reader.getDefaultReadParam();
        reader.setInput(stream, true, true);
        BufferedImage bi;
        try {
            bi = reader.read(0, param);
        } finally {
            reader.dispose();
            stream.close();
        }
        return bi;
    }

    /**
     * Writes an image using the an arbitrary <code>ImageWriter</code>
     * that supports the given format to an
     * <code>ImageOutputStream</code>.  The image is written to the
     * <code>ImageOutputStream</code> starting at the current stream
     * pointer, overwriting existing stream data from that point
     * forward, if present.
     *
     * <p> This method <em>does not</em> close the provided
     * <code>ImageOutputStream</code> after the write operation has completed;
     * it is the responsibility of the caller to close the stream, if desired.
     *
     * @param im a <code>RenderedImage</code> to be written.
     * @param formatName a <code>String</code> containing the informal
     * name of the format.
     * @param output an <code>ImageOutputStream</code> to be written to.
     *
     * @return <code>false</code> if no appropriate writer is found.
     *
     * @exception IllegalArgumentException if any parameter is
     * <code>null</code>.
     * @exception IOException if an error occurs during writing.
     */
    public static boolean write(RenderedImage im,
                                String formatName,
                                ImageOutputStream output) throws IOException {
        if (im == null) {
            throw new IllegalArgumentException("im == null!");
        }
        if (formatName == null) {
            throw new IllegalArgumentException("formatName == null!");
        }
        if (output == null) {
            throw new IllegalArgumentException("output == null!");
        }

        return doWrite(im, getWriter(im, formatName), output);
    }

    /**
     * Writes an image using an arbitrary <code>ImageWriter</code>
     * that supports the given format to a <code>File</code>.  If
     * there is already a <code>File</code> present, its contents are
     * discarded.
     *
     * @param im a <code>RenderedImage</code> to be written.
     * @param formatName a <code>String</code> containing the informal
     * name of the format.
     * @param output a <code>File</code> to be written to.
     *
     * @return <code>false</code> if no appropriate writer is found.
     *
     * @exception IllegalArgumentException if any parameter is
     * <code>null</code>.
     * @exception IOException if an error occurs during writing.
     */
    public static boolean write(RenderedImage im,
                                String formatName,
                                File output) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("output == null!");
        }
        ImageOutputStream stream = null;

        ImageWriter writer = getWriter(im, formatName);
        if (writer == null) {
            /* Do not make changes in the file system if we have
             * no appropriate writer.
             */
            return false;
        }

        try {
            output.delete();
            stream = createImageOutputStream(output);
        } catch (IOException e) {
            throw new IIOException("Can't create output stream!", e);
        }

        try {
            return doWrite(im, writer, stream);
        } finally {
            stream.close();
        }
    }

    /**
     * Writes an image using an arbitrary <code>ImageWriter</code>
     * that supports the given format to an <code>OutputStream</code>.
     *
     * <p> This method <em>does not</em> close the provided
     * <code>OutputStream</code> after the write operation has completed;
     * it is the responsibility of the caller to close the stream, if desired.
     *
     * <p> The current cache settings from <code>getUseCache</code>and
     * <code>getCacheDirectory</code> will be used to control caching.
     *
     * @param im a <code>RenderedImage</code> to be written.
     * @param formatName a <code>String</code> containing the informal
     * name of the format.
     * @param output an <code>OutputStream</code> to be written to.
     *
     * @return <code>false</code> if no appropriate writer is found.
     *
     * @exception IllegalArgumentException if any parameter is
     * <code>null</code>.
     * @exception IOException if an error occurs during writing.
     */
    public static boolean write(RenderedImage im,
                                String formatName,
                                OutputStream output) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("output == null!");
        }
        ImageOutputStream stream = null;
        try {
            stream = createImageOutputStream(output);
        } catch (IOException e) {
            throw new IIOException("Can't create output stream!", e);
        }

        try {
            return doWrite(im, getWriter(im, formatName), stream);
        } finally {
            stream.close();
        }
    }

    /**
     * Returns <code>ImageWriter</code> instance according to given
     * rendered image and image format or <code>null</code> if there
     * is no appropriate writer.
     */
    private static ImageWriter getWriter(RenderedImage im,
                                         String formatName) {
        ImageTypeSpecifier type =
            ImageTypeSpecifier.createFromRenderedImage(im);
        Iterator<ImageWriter> iter = getImageWriters(type, formatName);

        if (iter.hasNext()) {
            return iter.next();
        } else {
            return null;
        }
    }

    /**
     * Writes image to output stream  using given image writer.
     */
    private static boolean doWrite(RenderedImage im, ImageWriter writer,
                                 ImageOutputStream output) throws IOException {
        if (writer == null) {
            return false;
        }
        writer.setOutput(output);
        try {
            writer.write(im);
        } finally {
            writer.dispose();
            output.flush();
        }
        return true;
    }
}
