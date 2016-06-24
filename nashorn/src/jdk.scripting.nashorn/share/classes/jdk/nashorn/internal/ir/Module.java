/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.ir;

import java.util.List;

/**
 * ES6 Module information.
 */
public final class Module {

    /** The synthetic binding name assigned to export default declarations with unnamed expressions. */
    public static final String DEFAULT_EXPORT_BINDING_NAME = "*default*";

    /** The {@code export default} name. */
    public static final String DEFAULT_NAME = "default";

    /** The {@code export *} name. */
    public static final String STAR_NAME = "*";

    /**
     * A module ExportEntry record.
     *
     * @see <a href="http://www.ecma-international.org/ecma-262/6.0/#sec-source-text-module-records">es6 modules</a>
     */
    public static final class ExportEntry {
        private final IdentNode exportName;
        private final IdentNode moduleRequest;
        private final IdentNode importName;
        private final IdentNode localName;

        private final int startPosition;
        private final int endPosition;

        private ExportEntry(final IdentNode exportName, final IdentNode moduleRequest, final IdentNode importName,
                            final IdentNode localName, final int startPosition, final int endPosition) {
            this.exportName = exportName;
            this.moduleRequest = moduleRequest;
            this.importName = importName;
            this.localName = localName;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
        }

        /**
         * Creates a {@code export *} export entry.
         *
         * @param starName the star name
         * @param moduleRequest the module request
         * @param startPosition the start position
         * @param endPosition the end position
         * @return the export entry
         */
        public static ExportEntry exportStarFrom(final IdentNode starName, final IdentNode moduleRequest, final int startPosition, final int endPosition) {
            return new ExportEntry(null, moduleRequest, starName, null, startPosition, endPosition);
        }

        /**
         * Creates a {@code export default} export entry with a local name.
         *
         * @param defaultName the default name
         * @param localName the local name
         * @param startPosition the start position
         * @param endPosition the end position
         * @return the export entry
         */
        public static ExportEntry exportDefault(final IdentNode defaultName, final IdentNode localName, final int startPosition, final int endPosition) {
            return new ExportEntry(defaultName, null, null, localName, startPosition, endPosition);
        }

        /**
         * Creates a export entry with a local name and export name.
         *
         * @param exportName the export name
         * @param localName the local name
         * @param startPosition the start position
         * @param endPosition the end position
         * @return the export entry
         */
        public static ExportEntry exportSpecifier(final IdentNode exportName, final IdentNode localName, final int startPosition, final int endPosition) {
            return new ExportEntry(exportName, null, null, localName, startPosition, endPosition);
        }


        /**
         * Creates a export entry with an export name.
         *
         * @param exportName the export name
         * @param startPosition the start position
         * @param endPosition the end position
         * @return the export entry
         */
        public static ExportEntry exportSpecifier(final IdentNode exportName, final int startPosition, final int endPosition) {
            return exportSpecifier(exportName, exportName, startPosition, endPosition);
        }

        /**
         * Create a copy of this entry with the specified {@code module request} string.
         *
         * @param moduleRequest the module request
         * @param endPosition the new endPosition
         * @return the new export entry
         */
        public ExportEntry withFrom(@SuppressWarnings("hiding") final IdentNode moduleRequest, final int endPosition) {
            // Note that "from" moves localName to inputName, and localName becomes null
            return new ExportEntry(exportName, moduleRequest, localName, null, startPosition, endPosition);
        }

        /**
         * Returns the entry's export name.
         *
         * @return the export name
         */
        public IdentNode getExportName() {
            return exportName;
        }

        /**
         * Returns the entry's module request.
         *
         * @return the module request
         */
        public IdentNode getModuleRequest() {
            return moduleRequest;
        }

        /**
         * Returns the entry's import name.
         *
         * @return the import name
         */
        public IdentNode getImportName() {
            return importName;
        }

        /**
         * Returns the entry's local name.
         *
         * @return the local name
         */
        public IdentNode getLocalName() {
            return localName;
        }

        /**
         * Returns the entry's start position.
         *
         * @return the start position
         */
        public int getStartPosition() {
            return startPosition;
        }

        /**
         * Returns the entry's end position.
         *
         * @return the end position
         */
        public int getEndPosition() {
            return endPosition;
        }

        @Override
        public String toString() {
            return "ExportEntry [exportName=" + exportName + ", moduleRequest=" + moduleRequest + ", importName=" + importName + ", localName=" + localName + "]";
        }
    }

    /**
     * An ImportEntry record.
     *
     * @see <a href="http://www.ecma-international.org/ecma-262/6.0/#sec-source-text-module-records">es6 modules</a>
     */
    public static final class ImportEntry {
        private final IdentNode moduleRequest;
        private final IdentNode importName;
        private final IdentNode localName;

        private final int startPosition;
        private final int endPosition;

        private ImportEntry(final IdentNode moduleRequest, final IdentNode importName, final IdentNode localName,
                            final int startPosition, final int endPosition) {
            this.moduleRequest = moduleRequest;
            this.importName = importName;
            this.localName = localName;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
        }

        /**
         * Creates an import entry with the given import and local names.
         *
         * @param importName the import name
         * @param localName the local name
         * @param startPosition the start position
         * @param endPosition the end position
         * @return the import entry
         */
        public static ImportEntry importSpecifier(final IdentNode importName, final IdentNode localName, final int startPosition, final int endPosition) {
            return new ImportEntry(null, importName, localName, startPosition, endPosition);
        }

        /**
         * Creates a new import entry with the given import name.
         *
         * @param importName the import name
         * @param startPosition the start position
         * @param endPosition the end position
         * @return the import entry
         */
        public static ImportEntry importSpecifier(final IdentNode importName, final int startPosition, final int endPosition) {
            return importSpecifier(importName, importName, startPosition, endPosition);
        }

        /**
         * Returns a copy of this import entry with the given module request and end position.
         *
         * @param moduleRequest the module request
         * @param endPosition the new end position
         * @return the new import entry
         */
        public ImportEntry withFrom(@SuppressWarnings("hiding") final IdentNode moduleRequest, final int endPosition) {
            return new ImportEntry(moduleRequest, importName, localName, startPosition, endPosition);
        }

        /**
         * Returns the entry's module request.
         *
         * @return the module request
         */
        public IdentNode getModuleRequest() {
            return moduleRequest;
        }

        /**
         * Returns the entry's import name.
         *
         * @return the import name
         */
        public IdentNode getImportName() {
            return importName;
        }

        /**
         * Returns the entry's local name.
         *
         * @return the local name
         */
        public IdentNode getLocalName() {
            return localName;
        }

        /**
         * Returns the entry's start position.
         *
         * @return the start position
         */
        public int getStartPosition() {
            return startPosition;
        }

        /**
         * Returns the entry's end position.
         *
         * @return the end position
         */
        public int getEndPosition() {
            return endPosition;
        }

        @Override
        public String toString() {
            return "ImportEntry [moduleRequest=" + moduleRequest + ", importName=" + importName + ", localName=" + localName + "]";
        }
    }

    private final List<String> requestedModules;
    private final List<ImportEntry> importEntries;
    private final List<ExportEntry> localExportEntries;
    private final List<ExportEntry> indirectExportEntries;
    private final List<ExportEntry> starExportEntries;

    /**
     * Creates a module with the specified requested modules and import and export entries.
     *
     * @param requestedModules the requested modules
     * @param importEntries the import entries
     * @param localExportEntries local export entries
     * @param indirectExportEntries indirect export entries
     * @param starExportEntries star export entries
     */
    public Module(final List<String> requestedModules, final List<ImportEntry> importEntries, final List<ExportEntry> localExportEntries,
                  final List<ExportEntry> indirectExportEntries, final List<ExportEntry> starExportEntries) {
        this.requestedModules = requestedModules;
        this.importEntries = importEntries;
        this.localExportEntries = localExportEntries;
        this.indirectExportEntries = indirectExportEntries;
        this.starExportEntries = starExportEntries;
    }

    /**
     * Returns the list of requested modules.
     *
     * @return the requested modules
     */
    public List<String> getRequestedModules() {
        return requestedModules;
    }

    /**
     * Returns the list of import entries.
     *
     * @return the import entries
     */
    public List<ImportEntry> getImportEntries() {
        return importEntries;
    }

    /**
     * Returns the list of local export entries.
     *
     * @return the local export entries
     */
    public List<ExportEntry> getLocalExportEntries() {
        return localExportEntries;
    }

    /**
     * Returns the list of indirect export entries.
     *
     * @return the indirect export entries
     */
    public List<ExportEntry> getIndirectExportEntries() {
        return indirectExportEntries;
    }

    /**
     * Returns the list of star export entries.
     *
     * @return the star export entries
     */
    public List<ExportEntry> getStarExportEntries() {
        return starExportEntries;
    }

    @Override
    public String toString() {
        return "Module [requestedModules=" + requestedModules + ", importEntries=" + importEntries + ", localExportEntries=" + localExportEntries + ", indirectExportEntries=" +
                indirectExportEntries + ", starExportEntries=" + starExportEntries + "]";
    }
}
