/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 * <h1>Schema to Java compiler</h1>.
 *
 * <p>
 * This module contains the code that implements the schema compiler 'XJC'.
 *
 *
 * <h2>XJC Architecture Diagram</h2>
 * {@DotDiagram
     digraph G {
         rankdir=TB;

         // data
         node [shape=box]; // style=filled,color=lightpink];
         schema -> "DOM forest" [label="DOMForest.parse()"];
         "DOM forest" -> "schema OM" [label="SOM specific parser"];
         "schema OM" -> model [label="language specific builder"];

         model -> codeModel [label="BeanGenerator.generate()"];
         codeModel -> "Java source files" [label="JCodeModel.build()"];
         model -> outline [label="BeanGenerator.generate()"];

         edge [style=dotted,label="associate"]
         outline -> codeModel;
         outline -> model;
       }
 * }
 *
 * <h2>Overview</h2>
 * <p>
 * XJC consists of the following major components.
 * <dl>
 *  <dt>{@link com.sun.tools.internal.xjc.reader Schema reader}
 *  <dd>
 *   Schema readers read XML Schema documents (or DTD, RELAX NG, ...)
 *   and builds a model.
 *
 *  <dt>{@link com.sun.tools.internal.xjc.model Model}
 *  <dd>
 *   Model represents the 'blueprint' of the code to be generated.
 *   Model talks in terms of higher level constructs like 'class' and 'property'
 *   without getting too much into the details of the Java source code.
 *
 *  <dt>{@link com.sun.tools.internal.xjc.generator Code generator}
 *  <dd>
 *   Code generators use a model as an input and builds Java code AST
 *   into CodeModel. It also produces an {@link com.sun.tools.internal.xjc.outline.Outline} which captures
 *   this work.
 *
 *  <dt>{@link com.sun.tools.internal.xjc.outline.Outline Outline}
 *  <dd>
 *   Outline can be thought as a series of links between a model
 *   and CodeModel.
 * </dl>
 *
 * {@DotDiagram
 *   digraph G {
 *      rankdir = LR;
 *      schema -> reader -> model -> backend -> outline;
 *   }
 * }
 *
 * @ArchitectureDocument
 */
package com.sun.tools.internal.xjc;
