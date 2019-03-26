/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Provides the implementation of the Zip file system provider.
 * The Zip file system provider treats the contents of a Zip or JAR file as a file system.
 * <p>
 *
 * <h3>Accessing a Zip File System</h3>
 *
 * The {@linkplain java.nio.file.FileSystems FileSystems} {@code newFileSystem}
 * static factory methods can be used to:
 * <ul>
 *     <li>Create a Zip file system</li>
 *     <li>Open an existing file as a Zip file system</li>
 * </ul>
 *
 * <h3>URI Scheme Used to Identify the Zip File System</h3>
 *
 * The URI {@link java.net.URI#getScheme scheme} that identifies the ZIP file system is {@code jar}.
 *
 * <h3>Zip File System Properties</h3>
 *
 * The following properties may be specified when creating a Zip
 * file system:
 * <p>
 * <table class="striped">
 * <caption style="display:none">
 *     Configurable properties that may be specified when creating
 *     a new Zip file system
 * </caption>
 * <thead>
 * <tr>
 * <th scope="col">Property Name</th>
 * <th scope="col">Data Type</th>
 * <th scope="col">Default Value</th>
 * <th scope="col">Description</th>
 * </tr>
 * </thead>
 *
 * <tbody>
 * <tr>
 *   <td scope="row">create</td>
 *   <td>java.lang.String</td>
 *   <td>false</td>
 *   <td>
 *       If the value is {@code true}, the Zip file system provider
 *       creates a new Zip or JAR file if it does not exist.
 *   </td>
 * </tr>
 * <tr>
 *   <td scope="row">encoding</td>
 *   <td>java.lang.String</td>
 *   <td>UTF-8</td>
 *   <td>
 *       The value indicates the encoding scheme for the
 *       names of the entries in the Zip or JAR file.
 *   </td>
 * </tr>
 * </tbody>
 * </table>
 *
 * <h3>Examples:</h3>
 *
 * Construct a new Zip file system that is identified by a URI.  If the Zip file does not exist,
 * it will be created:
 * <pre>
 * {@code
 *
 *     URI uri = URI.create("jar:file:/home/luckydog/tennisTeam.zip");
 *     Map<String, String> env = Map.of("create", "true");
 *     FileSystem zipfs = FileSystems.newFileSystem(uri, env);
 * }
 * </pre>
 *
 * Construct a new Zip file system that is identified by specifying a path
 * and using automatic file type detection. Iterate from the root of the JAR displaying each
 * found entry:
 * <pre>
 * {@code
 *
 *     FileSystem zipfs = FileSystems.newFileSystem(Path.of("helloworld.jar"), null);
 *     Path rootDir = zipfs.getPath("/");
 *     Files.walk(rootDir)
 *            .forEach(System.out::println);
 * }
 * </pre>
 * @provides java.nio.file.spi.FileSystemProvider
 * @moduleGraph
 * @since 9
 */
module jdk.zipfs {
    provides java.nio.file.spi.FileSystemProvider with
        jdk.nio.zipfs.ZipFileSystemProvider;
}
