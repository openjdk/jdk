/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.nashorn.api.tree;

import java.util.List;
import java.util.stream.Collectors;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.Module;
import static jdk.nashorn.api.tree.ModuleTreeImpl.identOrNull;

final class ExportEntryTreeImpl extends TreeImpl implements ExportEntryTree {
    private final long startPos, endPos;
    private final IdentifierTree exportName;
    private final IdentifierTree moduleRequest;
    private final IdentifierTree importName;
    private final IdentifierTree localName;

    private ExportEntryTreeImpl(final long startPos, final long endPos,
            IdentifierTree exportName,
            IdentifierTree moduleRequest,
            IdentifierTree importName,
            IdentifierTree localName) {
        super(null); // no underlying Node!
        this.startPos = startPos;
        this.endPos = endPos;
        this.exportName = exportName;
        this.moduleRequest = moduleRequest;
        this.importName = importName;
        this.localName = localName;
    }

    private static ExportEntryTreeImpl createExportEntry(Module.ExportEntry entry) {
        return new ExportEntryTreeImpl(entry.getStartPosition(),
                entry.getEndPosition(),
                identOrNull(entry.getExportName()),
                identOrNull(entry.getModuleRequest()),
                identOrNull(entry.getImportName()),
                identOrNull(entry.getLocalName()));
    }

    static List<ExportEntryTreeImpl> createExportList(List<Module.ExportEntry> exportList) {
        return exportList.stream().
            map(ExportEntryTreeImpl::createExportEntry).
            collect(Collectors.toList());
    }

    @Override
    public Kind getKind() {
        return Tree.Kind.EXPORT_ENTRY;
    }

    @Override
    public <R,D> R accept(final TreeVisitor<R,D> visitor, final D data) {
        return visitor.visitExportEntry(this, data);
    }

    @Override
    public long getStartPosition() {
        return startPos;
    }

    @Override
    public long getEndPosition() {
        return endPos;
    }

    @Override
    public IdentifierTree getExportName() {
        return exportName;
    }

    @Override
    public IdentifierTree getModuleRequest() {
        return moduleRequest;
    }

    @Override
    public IdentifierTree getImportName() {
        return importName;
    }

    @Override
    public IdentifierTree getLocalName() {
        return localName;
    }
}
