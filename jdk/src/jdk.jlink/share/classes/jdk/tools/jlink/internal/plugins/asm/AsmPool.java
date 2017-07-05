/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal.plugins.asm;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.tools.jlink.plugin.Pool;

/**
 * A pool of ClassReader and other resource files.
 * This class allows to transform and sort classes and resource files.
 * <p>
 * Classes in the class pool are named following java binary name specification.
 * For example, java.lang.Object class is named java/lang/Object
 * <p>
 * Module information has been stripped out from class and other resource files
 * (.properties, binary files, ...).</p>
 */
public interface AsmPool {

    /**
     * A resource that is not a class file.
     * <p>
     * The path of a resource is a /-separated path name that identifies the
     * resource. For example com.foo.bar.Bundle.properties resource name is
     * com/foo/bar/Bundle.properties </p>
     * <p>
     */
    public class ResourceFile {

        private final String path;
        private final byte[] content;

        public ResourceFile(String path, byte[] content) {
            this.path = path;
            this.content = content;
        }

        public String getPath() {
            return path;
        }

        public byte[] getContent() {
            return content;
        }
    }

    /**
     * To visit each Class contained in the pool
     */
    public interface ClassReaderVisitor {

        /**
         * Called for each ClassReader located in the pool.
         *
         * @param reader A class reader.
         * @return A writer or null if the class has not been transformed.
         */
        public ClassWriter visit(ClassReader reader);
    }

    /**
     * To visit each Resource contained in the pool
     */
    public interface ResourceFileVisitor {

        /**
         * Called for each Resource file located in the pool.
         *
         * @param reader A resource file.
         * @return A resource file or null if the resource has not been
         * transformed.
         */
        public ResourceFile visit(ResourceFile reader);
    }

    /**
     * Contains the transformed classes. When the jimage file is generated,
     * transformed classes take precedence on unmodified ones.
     */
    public interface WritableClassPool {

        /**
         * Add a class to the pool, if a class already exists, it is replaced.
         *
         * @param writer The class writer.
         * @throws jdk.tools.jlink.plugin.PluginException
         */
        public void addClass(ClassWriter writer);

        /**
         * The class will be not added to the jimage file.
         *
         * @param className The class name to forget.
         * @throws jdk.tools.jlink.plugin.PluginException
         */
        public void forgetClass(String className);

        /**
         * Get a transformed class.
         *
         * @param binaryName The java class binary name
         * @return The ClassReader or null if the class is not found.
         * @throws jdk.tools.jlink.plugin.PluginException
         */
        public ClassReader getClassReader(String binaryName);

        /**
         * Get a transformed class.
         *
         * @param res A class resource.
         * @return The ClassReader or null if the class is not found.
         * @throws jdk.tools.jlink.plugin.PluginException
         */
        public ClassReader getClassReader(Pool.ModuleData res);

        /**
         * Returns all the classes contained in the writable pool.
         *
         * @return The collection of classes.
         */
        public Collection<Pool.ModuleData> getClasses();
    }

    /**
     * Contains the transformed resources. When the jimage file is generated,
     * transformed resources take precedence on unmodified ones.
     */
    public interface WritableResourcePool {

        /**
         * Add a resource, if the resource exists, it is replaced.
         *
         * @param resFile The resource file to add.
         * @throws jdk.tools.jlink.plugin.PluginException
         */
        public void addResourceFile(ResourceFile resFile);

        /**
         * The resource will be not added to the jimage file.
         *
         * @param resourceName
         * @throws jdk.tools.jlink.plugin.PluginException If the resource to
         * forget doesn't exist or is null.
         */
        public void forgetResourceFile(String resourceName);

        /**
         * Get a transformed resource.
         *
         * @param name The java resource name
         * @return The Resource or null if the resource is not found.
         */
        public ResourceFile getResourceFile(String name);

        /**
         * Get a transformed resource.
         *
         * @param res The java resource
         * @return The Resource or null if the resource is not found.
         */
        public ResourceFile getResourceFile(Pool.ModuleData res);

        /**
         * Returns all the resources contained in the writable pool.
         *
         * @return The array of resources.
         */
        public Collection<Pool.ModuleData> getResourceFiles();
    }

    /**
     * To order the classes and resources within a jimage file.
     */
    public interface Sorter {

        /**
         * @param resources The resources will be added to the jimage following
         * the order of this ResourcePool.
         * @return The resource paths ordered in the way to use for storage in the jimage.
         * @throws jdk.tools.jlink.plugin.PluginException
         */
        public List<String> sort(Pool resources);
    }

    /**
     * The writable pool used to store transformed resources.
     *
     * @return The writable pool.
     */
    public WritableClassPool getTransformedClasses();

    /**
     * The writable pool used to store transformed resource files.
     *
     * @return The writable pool.
     */
    public WritableResourcePool getTransformedResourceFiles();

    /**
     * Set a sorter instance to sort all files. If no sorter is set, then input
     * Resources will be added in the order they have been received followed by
     * newly added resources.
     *
     * @param sorter
     */
    public void setSorter(Sorter sorter);

    /**
     * Returns the classes contained in the pool.
     *
     * @return The classes.
     */
    public Collection<Pool.ModuleData> getClasses();

    /**
     * Returns the resources contained in the pool. Resources are all the file
     * that are not classes (eg: properties file, binary files, ...)
     *
     * @return The array of resource files.
     */
    public Collection<Pool.ModuleData> getResourceFiles();

    /**
     * Retrieves a resource based on the binary name. This name doesn't contain
     * the module name.
     * <b>NB:</b> When dealing with resources that have the same name in various
     * modules (eg: META-INFO/*), you should use the <code>ResourcePool</code>
     * referenced from this <code>AsmClassPool</code>.
     *
     * @param binaryName Name of a Java resource or null if the resource doesn't
     * exist.
     * @return
     */
    public ResourceFile getResourceFile(String binaryName);

    /**
     * Retrieves a resource for the passed resource.
     *
     * @param res The resource
     * @return The resource file or null if it doesn't exist.
     */
    public ResourceFile getResourceFile(Pool.ModuleData res);

    /**
     * Retrieve a ClassReader from the pool.
     *
     * @param binaryName Class binary name
     * @return A reader or null if the class is unknown
     * @throws jdk.tools.jlink.plugin.PluginException
     */
    public ClassReader getClassReader(String binaryName);

    /**
     * Retrieve a ClassReader from the pool.
     *
     * @param res A resource.
     * @return A reader or null if the class is unknown
     * @throws jdk.tools.jlink.plugin.PluginException
     */
    public ClassReader getClassReader(Pool.ModuleData res);

    /**
     * To visit the set of ClassReaders.
     *
     * @param visitor The visitor.
     * @throws jdk.tools.jlink.plugin.PluginException
     */
    public void visitClassReaders(ClassReaderVisitor visitor);

    /**
     * To visit the set of ClassReaders.
     *
     * @param visitor The visitor.
     * @throws jdk.tools.jlink.plugin.PluginException
     */
    public void visitResourceFiles(ResourceFileVisitor visitor);

    /**
     * Returns the pool of all the resources (transformed and unmodified).
     * The input resources are replaced by the transformed ones.
     * If a sorter has been set, it is used to sort the returned resources.
     *
     * @param output The pool used to fill the jimage.
     * @throws jdk.tools.jlink.plugin.PluginException
     */
    public void fillOutputResources(Pool output);

}
