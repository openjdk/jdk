/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.internal.jobjc.generator.classes;

import java.io.PrintStream;

import com.apple.internal.jobjc.generator.model.Category;
import com.apple.jobjc.JObjCRuntime;

public class CategoryClassFile extends JObjCClassFile {
    private final Category category;

    public CategoryClassFile(final Category category) {
        super(category.category);
        this.category = category;
    }

    @Override public void writeBeginning(final PrintStream out) {
        String targetCls = category.category.superClass.getFullPath();
        out.format("\tpublic %1$s(final %2$s obj, final %3$s runtime) {\n" +
                "\t\tsuper(obj, runtime);\n" +
                "\t}\n",
            className, targetCls, JObjCRuntime.class.getCanonicalName());
        super.writeBeginning(out);
    }
}
