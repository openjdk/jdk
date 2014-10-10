/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.sjavac.comp.dependencies;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import com.sun.tools.sjavac.Util;

public class DependencyCollector implements TaskListener {

    Map<PackageSymbol, Set<PackageSymbol>> collectedDependencies = new HashMap<>();

    @Override
    @DefinedBy(Api.COMPILER_TREE)
    public void started(TaskEvent e) {
    }

    @Override
    @DefinedBy(Api.COMPILER_TREE)
    public void finished(TaskEvent e) {
        if (e.getKind() == TaskEvent.Kind.ANALYZE) {
            JCCompilationUnit cu = (JCCompilationUnit) e.getCompilationUnit();
            PackageSymbol thisPkg = cu.packge;
            if (thisPkg == null) {
                // Compilation unit in default package. See JDK-8048144.
                return;
            }
            DependencyScanner ds = new DependencyScanner();
            cu.accept(ds);
            Set<PackageSymbol> pkgDeps = ds.getResult()
                                           .stream()
                                           .flatMap(dep -> dep.getPackages().stream())
                                           .collect(Collectors.toSet());
            collectedDependencies.merge(thisPkg, pkgDeps, Util::union);
        }
    }

    public Set<PackageSymbol> getSourcePackages() {
        return collectedDependencies.keySet();
    }

    public Set<PackageSymbol> getDependenciesForPkg(PackageSymbol ps) {
        return collectedDependencies.get(ps);
    }
}
