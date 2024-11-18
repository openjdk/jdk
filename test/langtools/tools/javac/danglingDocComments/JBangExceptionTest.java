/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 *
 * @compile/ref=empty.out -XDrawDiagnostics JBangException1.java
 * @compile/ref=empty.out -XDrawDiagnostics -Xlint:dangling-doc-comments JBangException1.java
 *
 * @compile/ref=empty.out -XDrawDiagnostics JBangException2.java
 * @compile/ref=JBangException2.enabled.out -XDrawDiagnostics -Xlint:dangling-doc-comments JBangException2.java
 *
 * @compile/ref=empty.out -XDrawDiagnostics JBangException3.java
 * @compile/ref=JBangException3.enabled.out -XDrawDiagnostics -Xlint:dangling-doc-comments JBangException3.java
 */

// The classes being tested reside in files separate from this one because
// the classes need to provide the initial dangling comment, which would
// otherwise interfere with the JTReg test comment. For similar reasons,
// the files with test classes do __NOT__ have a copyright header.
