/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

/**
 * <h1>Library for generating Java source code</h1>.
 *
 * <p>
 * CodeModel is a library that allows you to generate Java source
 * code in a type-safe fashion.
 *
 * <p>
 * With CodeModel, you build the java source code by first building AST,
 * then writing it out as text files that is Java source files.
 * The AST looks like this:
 *
 * {@DotDiagram
    digraph G {
        cls1 [label="JDefinedClass"];
        cls2 [label="JDefinedClass"];
        JCodeModel -> cls1 [label="generated class"];
        JCodeModel -> cls2 [label="generated class"];

        m1 [label="JMethod"];
        m2 [label="JMethod"];

        cls1 -> m1;
        cls1 -> m2;
        cls1 -> JField;

        m1 -> JVar [label="method parameter"];
        m1 -> JBlock [label="code"];
    }
 * }
 *
 * <p>
 * You bulid this tree mostly from top-down. So, you first create
 * a new {@link JDefinedClass} from {@link JCodeModel}, then you
 * create a {@link JMethod} from {@link JDefinedClass}, and so on.
 *
 * <p>
 * This design brings the following beneefits:
 *
 * <ul>
 *  <li>source code can be written in random order
 *  <li>generated source code nicely imports other classes
 *  <li>generated source code is lexically always correct
 *      (no unbalanced parenthesis, etc.)
 *  <li>code generation becomes relatively type-safe
 * </ul>
 *
 * The price you pay for that is
 * increased memory footprint and the generation speed.
 * See <a href="#performance">performance section</a> for
 * more discussions about the performance and possible improvements.
 *
 *
 * <h2>Using CodeModel</h2>
 * <p>
 * {@link com.sun.codemodel.internal.JCodeModel} is the entry point to
 * the library. See its javadoc for more details about how to use
 * CodeModel.
 *
 *
 *
 * <h2>Performance</h2>
 * <p>
 * Generally speaking, CodeModel is expected to be used in
 * an environment where the resource constraint is not severe.
 * Therefore, we haven't spent much effort in trying to make
 * this library lean and mean.
 *
 * <p>
 * That said, we did some benchmark and performance analysis.
 * In case anyone is interested in making this library
 * better performance wise, here's the findings.
 *
 * <p>
 * {@link List}s {@link Map}s, and other collections take up
 * a lot of space. Allocating those things lazily is generally
 * a good idea.
 *
 * <p>
 * Compared to template-based code generator, the writing operation
 * is slow, as it needs to traverse each AST node. Consider
 * pre-encoding tokens (like 'public') to the target encoding,
 * and consider exploting the subtree equivalence.
 *
 * @ArchitectureDocument
 */
package com.sun.codemodel.internal;

import java.util.List;
import java.util.Map;
