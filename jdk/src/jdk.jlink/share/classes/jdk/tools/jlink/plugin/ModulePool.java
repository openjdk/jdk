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
package jdk.tools.jlink.plugin;

import java.nio.ByteOrder;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Pool of module data.
 */
public interface ModulePool {
/**
     * Is this a read-only ModulePool?
     *
     * @return true if this is a read-only configuration.
     */
    public boolean isReadOnly();

    /**
     * Add a ModuleEntry.
     *
     * @param data The ModuleEntry to add.
     */
    public void add(ModuleEntry data);
    /**
     * Retrieves the module for the provided name.
     *
     * @param name The module name
     * @return the module of matching name, if found
     */
    public Optional<LinkModule> findModule(String name);

    /**
     * The stream of modules contained in this ModulePool.
     *
     * @return The stream of modules.
     */
    public Stream<? extends LinkModule> modules();

    /**
     * Return the number of LinkModule count in this ModulePool.
     *
     * @return the module count.
     */
    public int getModuleCount();

    /**
     * Get all ModuleEntry contained in this ModulePool instance.
     *
     * @return The stream of LinkModuleEntries.
     */
    public Stream<? extends ModuleEntry> entries();

    /**
     * Return the number of ModuleEntry count in this ModulePool.
     *
     * @return the entry count.
     */
    public int getEntryCount();

    /**
     * Get the ModuleEntry for the passed path.
     *
     * @param path A data path
     * @return A ModuleEntry instance or null if the data is not found
     */
   public Optional<ModuleEntry> findEntry(String path);

    /**
     * Check if the ModulePool contains the given ModuleEntry.
     *
     * @param data The module data to check existence for.
     * @return The module data or null if not found.
     */
    public boolean contains(ModuleEntry data);

    /**
     * Check if the ModulePool contains some content at all.
     *
     * @return True, no content, false otherwise.
     */
    public boolean isEmpty();

    /**
     * Visit each ModuleEntry in this ModulePool to transform it and copy
     * the transformed ModuleEntry to the output ModulePool.
     *
     * @param transform The function called for each ModuleEntry found in the
     * ModulePool. The transform function should return a ModuleEntry
     * instance which will be added to the output or it should return null if
     * the passed ModuleEntry is to be ignored for the output.
     *
     * @param output The ModulePool to be filled with Visitor returned
     * ModuleEntry.
     */
    public void transformAndCopy(Function<ModuleEntry, ModuleEntry> transform, ModulePool output);

    /**
     * The ByteOrder currently in use when generating the jimage file.
     *
     * @return The ByteOrder.
     */
    public ByteOrder getByteOrder();

    /**
     * Release properties such as OS, CPU name, version etc.
     *
     * @return the release properties
     */
    public Map<String, String> getReleaseProperties();
}
