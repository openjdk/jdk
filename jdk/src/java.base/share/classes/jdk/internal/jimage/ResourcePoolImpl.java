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
package jdk.internal.jimage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pool of resources. This class contain the content of a jimage file in the
 * matter of Resource.
 */
public class ResourcePoolImpl implements ResourcePool {

    private final Map<String, Resource> resources = new LinkedHashMap<>();

    private final ByteOrder order;
    private boolean isReadOnly;

    public ResourcePoolImpl(ByteOrder order) {
        Objects.requireNonNull(order);
        this.order = order;
    }

    /**
     * Make this Resources instance read-only. No resource can be added.
     */
    public void setReadOnly() {
        isReadOnly = true;
    }

    /**
     * Read only state.
     *
     * @return true if readonly false otherwise.
     */
    @Override
    public boolean isReadOnly() {
        return isReadOnly;
    }

    /**
     * The byte order
     *
     * @return
     */
    @Override
    public ByteOrder getByteOrder() {
        return order;
    }

    /**
     * Add a resource.
     *
     * @param resource The Resource to add.
     * @throws java.lang.Exception If the pool is read only.
     */
    @Override
    public void addResource(Resource resource) throws Exception {
        if (isReadOnly()) {
            throw new Exception("pool is readonly");
        }
        Objects.requireNonNull(resource);
        if (resources.get(resource.getPath()) != null) {
            throw new Exception("Resource" + resource.getPath() +
                    " already present");
        }
        resources.put(resource.getPath(), resource);
    }

    /**
     * Check if a resource is contained in the pool.
     *
     * @param res The resource to check.
     * @return true if res is contained, false otherwise.
     */
    @Override
    public boolean contains(Resource res) {
        Objects.requireNonNull(res);
        try {
            getResource(res.getPath());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Get all resources contained in this pool instance.
     *
     * @return The collection of resources;
     */
    @Override
    public Collection<Resource> getResources() {
        return Collections.unmodifiableCollection(resources.values());
    }

/**
     * Get the resource for the passed path.
     *
     * @param path A resource path
     * @return A Resource instance or null if the resource is not found
     */
    @Override
    public Resource getResource(String path) {
        Objects.requireNonNull(path);
        return resources.get(path);
    }

    /**
     * The Image modules. It is computed based on the resources contained by
     * this ResourcePool instance.
     *
     * @return The Image Modules.
     */
    @Override
    public Map<String, Set<String>> getModulePackages() {
        Map<String, Set<String>> moduleToPackage = new LinkedHashMap<>();
        retrieveModulesPackages(moduleToPackage);
        return moduleToPackage;
    }

    /**
     * Check if this pool contains some resources.
     *
     * @return True if contains some resources.
     */
    @Override
    public boolean isEmpty() {
        return resources.isEmpty();
    }

    /**
     * Visit the resources contained in this ResourcePool.
     *
     * @param visitor The visitor
     * @param strings
     * @throws Exception
     */
    @Override
    public void visit(Visitor visitor, ResourcePool output, StringTable strings)
            throws Exception {
        for (Resource resource : getResources()) {
            Resource res = visitor.visit(resource, order, strings);
            if (res != null) {
                output.addResource(res);
            }
        }
    }

    @Override
    public void addTransformedResource(Resource original, ByteBuffer transformed)
            throws Exception {
        if (isReadOnly()) {
            throw new Exception("Pool is readonly");
        }
        Objects.requireNonNull(original);
        Objects.requireNonNull(transformed);
        if (resources.get(original.getPath()) != null) {
            throw new Exception("Resource already present");
        }
        Resource res = new Resource(original.getPath(), transformed);
        addResource(res);
    }

    private void retrieveModulesPackages(Map<String, Set<String>> moduleToPackage) {
        for (Resource res : resources.values()) {
            Set<String> pkgs = moduleToPackage.get(res.getModule());
            if (pkgs == null) {
                pkgs = new HashSet<>();
                moduleToPackage.put(res.getModule(), pkgs);
            }
            // Module metadata only contains packages with resource files
            if (ImageFileCreator.isResourcePackage(res.getPath())) {
                String[] split = ImageFileCreator.splitPath(res.getPath());
                String pkg = split[1];
                if (pkg != null && !pkg.isEmpty()) {
                    pkgs.add(pkg);
                }
            }
        }
    }
}
