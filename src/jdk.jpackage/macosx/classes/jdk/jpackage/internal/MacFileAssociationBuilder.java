/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.FileAssociation;
import jdk.jpackage.internal.model.MacFileAssociation;
import jdk.jpackage.internal.model.MacFileAssociationMixin;

final class MacFileAssociationBuilder {

    MacFileAssociation create(FileAssociation fa) throws ConfigException {
        Objects.requireNonNull(fa);

        final var mixin = new MacFileAssociationMixin.Stub(
                Optional.ofNullable(cfBundleTypeName).orElse(DEFAULTS.cfBundleTypeName),
                Optional.ofNullable(cfBundleTypeRole).orElse(DEFAULTS.cfBundleTypeRole),
                Optional.ofNullable(lsHandlerRank).orElse(DEFAULTS.lsHandlerRank),
                Optional.ofNullable(lsTypeIsPackage),
                Optional.ofNullable(nsDocumentClass),
                Optional.ofNullable(nsPersistentStoreTypeKey),
                Optional.ofNullable(lsSupportsOpeningDocumentsInPlace),
                Optional.ofNullable(uiSupportsDocumentBrowser),
                Optional.ofNullable(utTypeConformsTo).orElse(DEFAULTS.utTypeConformsTo),
                Optional.ofNullable(nsExportableTypes).orElse(DEFAULTS.nsExportableTypes));

        return MacFileAssociation.create(fa, mixin);
    }

    MacFileAssociationBuilder cfBundleTypeName(String v) {
        cfBundleTypeName = v;
        return this;
    }

    MacFileAssociationBuilder cfBundleTypeRole(String v) {
        cfBundleTypeRole = v;
        return this;
    }

    MacFileAssociationBuilder lsHandlerRank(String v) {
        lsHandlerRank = v;
        return this;
    }

    MacFileAssociationBuilder lsTypeIsPackage(boolean v) {
        lsTypeIsPackage = v;
        return this;
    }

    MacFileAssociationBuilder nsDocumentClass(String v) {
        nsDocumentClass = v;
        return this;
    }

    MacFileAssociationBuilder nsPersistentStoreTypeKey(String v) {
        nsPersistentStoreTypeKey = v;
        return this;
    }

    MacFileAssociationBuilder lsSupportsOpeningDocumentsInPlace(boolean v) {
        lsSupportsOpeningDocumentsInPlace = v;
        return this;
    }

    MacFileAssociationBuilder uiSupportsDocumentBrowser(boolean v) {
        uiSupportsDocumentBrowser = v;
        return this;
    }

    MacFileAssociationBuilder utTypeConformsTo(List<String> v) {
        utTypeConformsTo = v;
        return this;
    }

    MacFileAssociationBuilder nsExportableTypes(List<String> v) {
        nsExportableTypes = v;
        return this;
    }

    private String cfBundleTypeName;
    private String cfBundleTypeRole;
    private String lsHandlerRank;
    private String nsDocumentClass;
    private String nsPersistentStoreTypeKey;
    private Boolean lsTypeIsPackage;
    private Boolean lsSupportsOpeningDocumentsInPlace;
    private Boolean uiSupportsDocumentBrowser;
    private List<String> utTypeConformsTo;
    private List<String> nsExportableTypes;

    private static final MacFileAssociationBuilder DEFAULTS = new MacFileAssociationBuilder()
            .lsHandlerRank("Owner")
            .cfBundleTypeRole("Editor")
            .cfBundleTypeName("")
            .utTypeConformsTo(List.of("public.data"))
            .nsExportableTypes(List.of());

}
