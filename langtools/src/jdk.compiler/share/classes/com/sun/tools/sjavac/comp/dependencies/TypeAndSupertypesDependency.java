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

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Type;

import static com.sun.tools.javac.code.Kinds.Kind.*;

public class TypeAndSupertypesDependency implements Dependency {

    protected TypeSymbol type;

    public TypeAndSupertypesDependency(TypeSymbol type) {
        this.type = Objects.requireNonNull(type);
    }

    private Set<TypeSymbol> allSupertypes(TypeSymbol t) {
        if (t == null)
            return Collections.emptySet();
        Set<TypeSymbol> result = new HashSet<>();
        result.add(t);
        if (t instanceof ClassSymbol) {
            ClassSymbol cs = (ClassSymbol) t;
            result.addAll(allSupertypes(cs.getSuperclass().tsym));
            for (Type it : cs.getInterfaces())
                result.addAll(allSupertypes(it.tsym));
        }
        return result;
    }

    @Override
    public Set<PackageSymbol> getPackages() {
        if (type.kind == ERR)
            return Collections.emptySet();
        if (type instanceof ClassSymbol) {
            return allSupertypes(type).stream()
                                      .map(TypeSymbol::packge)
                                      .collect(Collectors.toSet());
        }
        throw new AssertionError("Could not get package name for " + type);
    }
}

