/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.imageio.spi;

import java.security.PrivilegedAction;
import java.security.AccessController;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Vector;
import com.sun.imageio.spi.FileImageInputStreamSpi;
import com.sun.imageio.spi.FileImageOutputStreamSpi;
import com.sun.imageio.spi.InputStreamImageInputStreamSpi;
import com.sun.imageio.spi.OutputStreamImageOutputStreamSpi;
import com.sun.imageio.spi.RAFImageInputStreamSpi;
import com.sun.imageio.spi.RAFImageOutputStreamSpi;
import com.sun.imageio.plugins.gif.GIFImageReaderSpi;
import com.sun.imageio.plugins.gif.GIFImageWriterSpi;
import com.sun.imageio.plugins.jpeg.JPEGImageReaderSpi;
import com.sun.imageio.plugins.jpeg.JPEGImageWriterSpi;
import com.sun.imageio.plugins.png.PNGImageReaderSpi;
import com.sun.imageio.plugins.png.PNGImageWriterSpi;
import com.sun.imageio.plugins.bmp.BMPImageReaderSpi;
import com.sun.imageio.plugins.bmp.BMPImageWriterSpi;
import com.sun.imageio.plugins.wbmp.WBMPImageReaderSpi;
import com.sun.imageio.plugins.wbmp.WBMPImageWriterSpi;
import sun.awt.AppContext;
import java.util.ServiceLoader;
import java.util.ServiceConfigurationError;

/**
 * A registry for service provider instances.  Service provider
 * classes may be detected at run time by means of meta-information in
 * the JAR files containing them.  The intent is that it be relatively
 * inexpensive to load and inspect all available service provider
 * classes.  These classes may them be used to locate and instantiate
 * more heavyweight classes that will perform actual work, in this
 * case instances of <code>ImageReader</code>,
 * <code>ImageWriter</code>, <code>ImageTranscoder</code>,
 * <code>ImageInputStream</code>, and <code>ImageOutputStream</code>.
 *
 * <p> Service providers found on the system classpath (typically
 * the <code>lib/ext</code> directory in the Java
 * installation directory) are automatically loaded as soon as this class is
 * instantiated.
 *
 * <p> When the <code>registerApplicationClasspathSpis</code> method
 * is called, service provider instances declared in the
 * meta-information section of JAR files on the application class path
 * are loaded.  To declare a service provider, a <code>services</code>
 * subdirectory is placed within the <code>META-INF</code> directory
 * that is present in every JAR file.  This directory contains a file
 * for each service provider interface that has one or more
 * implementation classes present in the JAR file.  For example, if
 * the JAR file contained a class named
 * <code>com.mycompany.imageio.MyFormatReaderSpi</code> which
 * implements the <code>ImageReaderSpi</code> interface, the JAR file
 * would contain a file named:
 *
 * <pre>
 * META-INF/services/javax.imageio.spi.ImageReaderSpi
 * </pre>
 *
 * containing the line:
 *
 * <pre>
 * com.mycompany.imageio.MyFormatReaderSpi
 * </pre>
 *
 * <p> The service provider classes are intended to be lightweight
 * and quick to load.  Implementations of these interfaces
 * should avoid complex dependencies on other classes and on
 * native code.
 *
 * <p> It is also possible to manually add service providers not found
 * automatically, as well as to remove those that are using the
 * interfaces of the <code>ServiceRegistry</code> class.  Thus
 * the application may customize the contents of the registry as it
 * sees fit.
 *
 * <p> For more details on declaring service providers, and the JAR
 * format in general, see the <a
 * href="{@docRoot}/../technotes/guides/jar/jar.html">
 * JAR File Specification</a>.
 *
 */
public final class IIORegistry extends ServiceRegistry {

    /**
     * A <code>Vector</code> containing the valid IIO registry
     * categories (superinterfaces) to be used in the constructor.
     */
    private static final Vector initialCategories = new Vector(5);

    static {
        initialCategories.add(ImageReaderSpi.class);
        initialCategories.add(ImageWriterSpi.class);
        initialCategories.add(ImageTranscoderSpi.class);
        initialCategories.add(ImageInputStreamSpi.class);
        initialCategories.add(ImageOutputStreamSpi.class);
    }

    /**
     * Set up the valid service provider categories and automatically
     * register all available service providers.
     *
     * <p> The constructor is private in order to prevent creation of
     * additional instances.
     */
    private IIORegistry() {
        super(initialCategories.iterator());
        registerStandardSpis();
        registerApplicationClasspathSpis();
    }

    /**
     * Returns the default <code>IIORegistry</code> instance used by
     * the Image I/O API.  This instance should be used for all
     * registry functions.
     *
     * <p> Each <code>ThreadGroup</code> will receive its own
     * instance; this allows different <code>Applet</code>s in the
     * same browser (for example) to each have their own registry.
     *
     * @return the default registry for the current
     * <code>ThreadGroup</code>.
     */
    public static IIORegistry getDefaultInstance() {
        AppContext context = AppContext.getAppContext();
        IIORegistry registry =
            (IIORegistry)context.get(IIORegistry.class);
        if (registry == null) {
            // Create an instance for this AppContext
            registry = new IIORegistry();
            context.put(IIORegistry.class, registry);
        }
        return registry;
    }

    private void registerStandardSpis() {
        // Hardwire standard SPIs
        registerServiceProvider(new GIFImageReaderSpi());
        registerServiceProvider(new GIFImageWriterSpi());
        registerServiceProvider(new BMPImageReaderSpi());
        registerServiceProvider(new BMPImageWriterSpi());
        registerServiceProvider(new WBMPImageReaderSpi());
        registerServiceProvider(new WBMPImageWriterSpi());
        registerServiceProvider(new PNGImageReaderSpi());
        registerServiceProvider(new PNGImageWriterSpi());
        registerServiceProvider(new JPEGImageReaderSpi());
        registerServiceProvider(new JPEGImageWriterSpi());
        registerServiceProvider(new FileImageInputStreamSpi());
        registerServiceProvider(new FileImageOutputStreamSpi());
        registerServiceProvider(new InputStreamImageInputStreamSpi());
        registerServiceProvider(new OutputStreamImageOutputStreamSpi());
        registerServiceProvider(new RAFImageInputStreamSpi());
        registerServiceProvider(new RAFImageOutputStreamSpi());

        registerInstalledProviders();
    }

    /**
     * Registers all available service providers found on the
     * application class path, using the default
     * <code>ClassLoader</code>.  This method is typically invoked by
     * the <code>ImageIO.scanForPlugins</code> method.
     *
     * @see javax.imageio.ImageIO#scanForPlugins
     * @see ClassLoader#getResources
     */
    public void registerApplicationClasspathSpis() {
        // FIX: load only from application classpath

        ClassLoader loader = Thread.currentThread().getContextClassLoader();

        Iterator categories = getCategories();
        while (categories.hasNext()) {
            Class<IIOServiceProvider> c = (Class)categories.next();
            Iterator<IIOServiceProvider> riter =
                    ServiceLoader.load(c, loader).iterator();
            while (riter.hasNext()) {
                try {
                    // Note that the next() call is required to be inside
                    // the try/catch block; see 6342404.
                    IIOServiceProvider r = riter.next();
                    registerServiceProvider(r);
                } catch (ServiceConfigurationError err) {
                    if (System.getSecurityManager() != null) {
                        // In the applet case, we will catch the  error so
                        // registration of other plugins can  proceed
                        err.printStackTrace();
                    } else {
                        // In the application case, we will  throw the
                        // error to indicate app/system  misconfiguration
                        throw err;
                    }
                }
            }
        }
    }

    private void registerInstalledProviders() {
        /*
          We need to load installed providers from the
          system classpath (typically the <code>lib/ext</code>
          directory in in the Java installation directory)
          in the privileged mode in order to
          be able read corresponding jar files even if
          file read capability is restricted (like the
          applet context case).
         */
        PrivilegedAction doRegistration =
            new PrivilegedAction() {
                public Object run() {
                    Iterator categories = getCategories();
                    while (categories.hasNext()) {
                        Class<IIOServiceProvider> c = (Class)categories.next();
                        for (IIOServiceProvider p : ServiceLoader.loadInstalled(c)) {
                            registerServiceProvider(p);
                        }
                    }
                    return this;
                }
            };

        AccessController.doPrivileged(doRegistration);
    }
}
