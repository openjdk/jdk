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

/**
 * <p>
 * Nashorn parser API provides interfaces to represent ECMAScript source code
 * as abstract syntax trees (AST) and Parser to parse ECMAScript source scripts.
 * </p>
 * <p>
 * Using parser API user can write Java code to access parse tree
 * representation of ECMAScript source. Script source may be a file,
 * a URL or a String. Unless stated otherwise null argument in methods of this
 * package result in NullPointerException being thrown.
 * </p>
 *
 * <pre>
 * <code>
 * import jdk.nashorn.api.tree.*;
 * import java.io.File;
 *
 * // Simple example that prints warning on 'with' statements
 * public class Main {
 *     public static void main(String[] args) throws Exception {
 *         // Create a new parser instance
 *         Parser parser = Parser.create();
 *         File sourceFile = new File(args[0]);
 *
 *         // Parse given source File using parse method.
 *         // Pass a diagnostic listener to print error messages.
 *         CompilationUnitTree cut = parser.parse(sourceFile,
 *             (d) -&gt; { System.out.println(d); });
 *
 *         if (cut != null) {
 *             // call Tree.accept method passing a SimpleTreeVisitor
 *             cut.accept(new SimpleTreeVisitor&lt;Void, Void&gt;() {
 *                 // visit method for 'with' statement
 *                 public Void visitWith(WithTree wt, Void v) {
 *                     // print warning on 'with' statement
 *                     System.out.println("Warning: using 'with' statement!");
 *                     return null;
 *                 }
 *             }, null);
 *         }
 *     }
 * }
 * </code>
 * </pre>
 *
 * @since 9
 */
package jdk.nashorn.api.tree;

