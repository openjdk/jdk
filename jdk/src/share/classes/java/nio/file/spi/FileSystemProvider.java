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

package java.nio.file.spi;

import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.channels.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.io.IOException;

/**
 * Service-provider class for file systems.
 *
 * <p> A file system provider is a concrete implementation of this class that
 * implements the abstract methods defined by this class. A provider is
 * identified by a {@code URI} {@link #getScheme() scheme}. The default provider
 * is identified by the URI scheme "file". It creates the {@link FileSystem} that
 * provides access to the file systems accessible to the Java virtual machine.
 * The {@link FileSystems} class defines how file system providers are located
 * and loaded. The default provider is typically a system-default provider but
 * may be overridden if the system property {@code
 * java.nio.file.spi.DefaultFileSystemProvider} is set. In that case, the
 * provider has a one argument constructor whose formal parameter type is {@code
 * FileSystemProvider}. All other providers have a zero argument constructor
 * that initializes the provider.
 *
 * <p> A provider is a factory for one or more {@link FileSystem} instances. Each
 * file system is identified by a {@code URI} where the URI's scheme matches
 * the provider's {@link #getScheme scheme}. The default file system, for example,
 * is identified by the URI {@code "file:///"}. A memory-based file system,
 * for example, may be identified by a URI such as {@code "memory:///?name=logfs"}.
 * The {@link #newFileSystem newFileSystem} method may be used to create a file
 * system, and the {@link #getFileSystem getFileSystem} method may be used to
 * obtain a reference to an existing file system created by the provider. Where
 * a provider is the factory for a single file system then it is provider dependent
 * if the file system is created when the provider is initialized, or later when
 * the {@code newFileSystem} method is invoked. In the case of the default
 * provider, the {@code FileSystem} is created when the provider is initialized.
 *
 * <p> In addition to file systems, a provider is also a factory for {@link
 * FileChannel} and {@link AsynchronousFileChannel} channels. The {@link
 * #newFileChannel newFileChannel} and {@link #newAsynchronousFileChannel
 * AsynchronousFileChannel} methods are defined to open or create files, returning
 * a channel to access the file. These methods are invoked by static factory
 * methods defined in the {@link java.nio.channels} package.
 *
 * <p> All of the methods in this class are safe for use by multiple concurrent
 * threads.
 *
 * @since 1.7
 */

public abstract class FileSystemProvider {
    // lock using when loading providers
    private static final Object lock = new Object();

    // installed providers
    private static volatile List<FileSystemProvider> installedProviders;

    // used to avoid recursive loading of instaled providers
    private static boolean loadingProviders  = false;

    private static Void checkPermission() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkPermission(new RuntimePermission("fileSystemProvider"));
        return null;
    }
    private FileSystemProvider(Void ignore) { }

    /**
     * Initializes a new instance of this class.
     *
     * <p> During construction a provider may safely access files associated
     * with the default provider but care needs to be taken to avoid circular
     * loading of other installed providers. If circular loading of installed
     * providers is detected then an unspecified error is thrown.
     *
     * @throws  SecurityException
     *          If a security manager has been installed and it denies
     *          {@link RuntimePermission}<tt>("fileSystemProvider")</tt>
     */
    protected FileSystemProvider() {
        this(checkPermission());
    }

    // loads all installed providers
    private static List<FileSystemProvider> loadInstalledProviders() {
        List<FileSystemProvider> list = new ArrayList<FileSystemProvider>();

        ServiceLoader<FileSystemProvider> sl = ServiceLoader
            .load(FileSystemProvider.class, ClassLoader.getSystemClassLoader());

        // ServiceConfigurationError may be throw here
        for (FileSystemProvider provider: sl) {
            String scheme = provider.getScheme();

            // add to list if the provider is not "file" and isn't a duplicate
            if (!scheme.equalsIgnoreCase("file")) {
                boolean found = false;
                for (FileSystemProvider p: list) {
                    if (p.getScheme().equalsIgnoreCase(scheme)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    list.add(provider);
                }
            }
        }
        return list;
    }

    /**
     * Returns a list of the installed file system providers.
     *
     * <p> The first invocation of this method causes the default provider to be
     * initialized (if not already initialized) and loads any other installed
     * providers as described by the {@link FileSystems} class.
     *
     * @return  An unmodifiable list of the installed file system providers. The
     *          list contains at least one element, that is the default file
     *          system provider
     *
     * @throws  ServiceConfigurationError
     *          When an error occurs while loading a service provider
     */
    public static List<FileSystemProvider> installedProviders() {
        if (installedProviders == null) {
            // ensure default provider is initialized
            FileSystemProvider defaultProvider = FileSystems.getDefault().provider();

            synchronized (lock) {
                if (installedProviders == null) {
                    if (loadingProviders) {
                        throw new Error("Circular loading of installed providers detected");
                    }
                    loadingProviders = true;

                    List<FileSystemProvider> list = AccessController
                        .doPrivileged(new PrivilegedAction<List<FileSystemProvider>>() {
                            @Override
                            public List<FileSystemProvider> run() {
                                return loadInstalledProviders();
                        }});

                    // insert the default provider at the start of the list
                    list.add(0, defaultProvider);

                    installedProviders = Collections.unmodifiableList(list);
                }
            }
        }
        return installedProviders;
    }

    /**
     * Returns the URI scheme that identifies this provider.
     *
     * @return  The URI scheme
     */
    public abstract String getScheme();

    /**
     * Constructs a new {@code FileSystem} object identified by a URI. This
     * method is invoked by the {@link FileSystems#newFileSystem(URI,Map)}
     * method to open a new file system identified by a URI.
     *
     * <p> The {@code uri} parameter is an absolute, hierarchical URI, with a
     * scheme equal (without regard to case) to the scheme supported by this
     * provider. The exact form of the URI is highly provider dependent. The
     * {@code env} parameter is a map of provider specific properties to configure
     * the file system.
     *
     * <p> This method throws {@link FileSystemAlreadyExistsException} if the
     * file system already exists because it was previously created by an
     * invocation of this method. Once a file system is {@link FileSystem#close
     * closed} it is provider-dependent if the provider allows a new file system
     * to be created with the same URI as a file system it previously created.
     *
     * @param   uri
     *          URI reference
     * @param   env
     *          A map of provider specific properties to configure the file system;
     *          may be empty
     *
     * @return  A new file system
     *
     * @throws  IllegalArgumentException
     *          If the pre-conditions for the {@code uri} parameter aren't met,
     *          or the {@code env} parameter does not contain properties required
     *          by the provider, or a property value is invalid
     * @throws  IOException
     *          An I/O error occurs creating the file system
     * @throws  SecurityException
     *          If a security manager is installed and it denies an unspecified
     *          permission required by the file system provider implementation
     * @throws  FileSystemAlreadyExistsException
     *          If the file system has already been created
     */
    public abstract FileSystem newFileSystem(URI uri, Map<String,?> env)
        throws IOException;

    /**
     * Returns an existing {@code FileSystem} created by this provider.
     *
     * <p> This method returns a reference to a {@code FileSystem} that was
     * created by invoking the {@link #newFileSystem(URI,Map) newFileSystem(URI,Map)}
     * method. File systems created the {@link #newFileSystem(FileRef,Map)
     * newFileSystem(FileRef,Map)} method are not returned by this method.
     * The file system is identified by its {@code URI}. Its exact form
     * is highly provider dependent. In the case of the default provider the URI's
     * path component is {@code "/"} and the authority, query and fragment components
     * are undefined (Undefined components are represented by {@code null}).
     *
     * <p> Once a file system created by this provider is {@link FileSystem#close
     * closed} it is provider-dependent if this method returns a reference to
     * the closed file system or throws {@link FileSystemNotFoundException}.
     * If the provider allows a new file system to be created with the same URI
     * as a file system it previously created then this method throws the
     * exception if invoked after the file system is closed (and before a new
     * instance is created by the {@link #newFileSystem newFileSystem} method).
     *
     * <p> If a security manager is installed then a provider implementation
     * may require to check a permission before returning a reference to an
     * existing file system. In the case of the {@link FileSystems#getDefault
     * default} file system, no permission check is required.
     *
     * @param   uri
     *          URI reference
     *
     * @return  The file system
     *
     * @throws  IllegalArgumentException
     *          If the pre-conditions for the {@code uri} parameter aren't met
     * @throws  FileSystemNotFoundException
     *          If the file system does not exist
     * @throws  SecurityException
     *          If a security manager is installed and it denies an unspecified
     *          permission.
     */
    public abstract FileSystem getFileSystem(URI uri);

    /**
     * Return a {@code Path} object by converting the given {@link URI}. The
     * resulting {@code Path} is associated with a {@link FileSystem} that
     * already exists or is constructed automatically.
     *
     * <p> The exact form of the URI is file system provider dependent. In the
     * case of the default provider, the URI scheme is {@code "file"} and the
     * given URI has a non-empty path component, and undefined query, and
     * fragment components. The resulting {@code Path} is associated with the
     * default {@link FileSystems#getDefault default} {@code FileSystem}.
     *
     * <p> If a security manager is installed then a provider implementation
     * may require to check a permission. In the case of the {@link
     * FileSystems#getDefault default} file system, no permission check is
     * required.
     *
     * @param   uri
     *          The URI to convert
     *
     * @throws  IllegalArgumentException
     *          If the URI scheme does not identify this provider or other
     *          preconditions on the uri parameter do not hold
     * @throws  FileSystemNotFoundException
     *          The file system, identified by the URI, does not exist and
     *          cannot be created automatically
     * @throws  SecurityException
     *          If a security manager is installed and it denies an unspecified
     *          permission.
     */
    public abstract Path getPath(URI uri);

    /**
     * Constructs a new {@code FileSystem} to access the contents of a file as a
     * file system.
     *
     * <p> This method is intended for specialized providers of pseudo file
     * systems where the contents of one or more files is treated as a file
     * system. The {@code file} parameter is a reference to an existing file
     * and the {@code env} parameter is a map of provider specific properties to
     * configure the file system.
     *
     * <p> If this provider does not support the creation of such file systems
     * or if the provider does not recognize the file type of the given file then
     * it throws {@code UnsupportedOperationException}. The default implementation
     * of this method throws {@code UnsupportedOperationException}.
     *
     * @param   file
     *          The file
     * @param   env
     *          A map of provider specific properties to configure the file system;
     *          may be empty
     *
     * @return  A new file system
     *
     * @throws  UnsupportedOperationException
     *          If this provider does not support access to the contents as a
     *          file system or it does not recognize the file type of the
     *          given file
     * @throws  IllegalArgumentException
     *          If the {@code env} parameter does not contain properties required
     *          by the provider, or a property value is invalid
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          If a security manager is installed and it denies an unspecified
     *          permission.
     */
    public FileSystem newFileSystem(FileRef file, Map<String,?> env)
        throws IOException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Opens or creates a file for reading and/or writing, returning a file
     * channel to access the file.
     *
     * <p> This method is invoked by the {@link FileChannel#open(Path,Set,FileAttribute[])
     * FileChannel.open} method to open a file channel. A provider that does not
     * support all the features required to construct a file channel throws
     * {@code UnsupportedOperationException}. The default provider is required
     * to support the creation of file channels. When not overridden, the
     * default implementation throws {@code UnsupportedOperationException}.
     *
     * @param   path
     *          The path of the file to open or create
     * @param   options
     *          Options specifying how the file is opened
     * @param   attrs
     *          An optional list of file attributes to set atomically when
     *          creating the file
     *
     * @return  A new file channel
     *
     * @throws  IllegalArgumentException
     *          If the set contains an invalid combination of options
     * @throws  UnsupportedOperationException
     *          If this provider that does not support creating file channels,
     *          or an unsupported open option or file attribute is specified
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default file system, the {@link
     *          SecurityManager#checkRead(String)} method is invoked to check
     *          read access if the file is opened for reading. The {@link
     *          SecurityManager#checkWrite(String)} method is invoked to check
     *          write access if the file is opened for writing
     */
    public FileChannel newFileChannel(Path path,
                                      Set<? extends OpenOption> options,
                                      FileAttribute<?>... attrs)
        throws IOException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Opens or creates a file for reading and/or writing, returning an
     * asynchronous file channel to access the file.
     *
     * <p> This method is invoked by the {@link
     * AsynchronousFileChannel#open(Path,Set,ExecutorService,FileAttribute[])
     * AsynchronousFileChannel.open} method to open an asynchronous file channel.
     * A provider that does not support all the features required to construct
     * an asynchronous file channel throws {@code UnsupportedOperationException}.
     * The default provider is required to support the creation of asynchronous
     * file channels. When not overridden, the default implementation of this
     * method throws {@code UnsupportedOperationException}.
     *
     * @param   path
     *          The path of the file to open or create
     * @param   options
     *          Options specifying how the file is opened
     * @param   executor
     *          The thread pool or {@code null} to associate the channel with
     *          the default thread pool
     * @param   attrs
     *          An optional list of file attributes to set atomically when
     *          creating the file
     *
     * @return  A new asynchronous file channel
     *
     * @throws  IllegalArgumentException
     *          If the set contains an invalid combination of options
     * @throws  UnsupportedOperationException
     *          If this provider that does not support creating asynchronous file
     *          channels, or an unsupported open option or file attribute is
     *          specified
     * @throws  IOException
     *          If an I/O error occurs
     * @throws  SecurityException
     *          In the case of the default file system, the {@link
     *          SecurityManager#checkRead(String)} method is invoked to check
     *          read access if the file is opened for reading. The {@link
     *          SecurityManager#checkWrite(String)} method is invoked to check
     *          write access if the file is opened for writing
     */
    public AsynchronousFileChannel newAsynchronousFileChannel(Path path,
                                                              Set<? extends OpenOption> options,
                                                              ExecutorService executor,
                                                              FileAttribute<?>... attrs)
        throws IOException
    {
        throw new UnsupportedOperationException();
    }
}
