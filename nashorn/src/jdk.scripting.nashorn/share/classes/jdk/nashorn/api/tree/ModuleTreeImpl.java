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
import static jdk.nashorn.api.tree.ExportEntryTreeImpl.createExportList;
import static jdk.nashorn.api.tree.ImportEntryTreeImpl.createImportList;

final class ModuleTreeImpl extends TreeImpl implements ModuleTree {

    private final Module mod;
    private final List<? extends ImportEntryTree> imports;
    private final List<? extends ExportEntryTree> localExports;
    private final List<? extends ExportEntryTree> indirectExports;
    private final List<? extends ExportEntryTree> starExports;

    private ModuleTreeImpl(final FunctionNode func,
            final List<? extends ImportEntryTree> imports,
            final List<? extends ExportEntryTree> localExports,
            final List<? extends ExportEntryTree> indirectExports,
            final List<? extends ExportEntryTree> starExports) {
        super(func);
        assert func.getKind() == FunctionNode.Kind.MODULE : "module function node expected";
        this.mod = func.getModule();
        this.imports = imports;
        this.localExports = localExports;
        this.indirectExports = indirectExports;
        this.starExports = starExports;
    }

    static ModuleTreeImpl create(final FunctionNode func) {
        final Module mod = func.getModule();
        return new ModuleTreeImpl(func,
            createImportList(mod.getImportEntries()),
            createExportList(mod.getLocalExportEntries()),
            createExportList(mod.getIndirectExportEntries()),
            createExportList(mod.getStarExportEntries()));
    }

    @Override
    public Kind getKind() {
        return Tree.Kind.MODULE;
    }

    @Override
    public List<? extends ImportEntryTree> getImportEntries() {
        return imports;
    }

    @Override
    public List<? extends ExportEntryTree> getLocalExportEntries() {
        return localExports;
    }

    @Override
    public List<? extends ExportEntryTree> getIndirectExportEntries() {
        return indirectExports;
    }

    @Override
    public List<? extends ExportEntryTree> getStarExportEntries() {
        return starExports;
    }

    @Override
    public <R,D> R accept(final TreeVisitor<R,D> visitor, final D data) {
        return visitor.visitModule(this, data);
    }

    static IdentifierTree identOrNull(final IdentNode node) {
        return node != null? new IdentifierTreeImpl(node) : null;
    }
}
