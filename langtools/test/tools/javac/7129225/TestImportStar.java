/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 7129225
 * @summary import xxx.* isn't handled correctly by annotation processing
 * @library ../lib
 * @build JavacTestingAbstractProcessor
 * @compile/fail/ref=NegTest.ref -XDrawDiagnostics TestImportStar.java
 * @compile Anno.java AnnoProcessor.java
 * @compile/ref=TestImportStar.ref -XDrawDiagnostics -processor AnnoProcessor -proc:only TestImportStar.java
 */

 //The @compile/fail... verifies that the fix doesn't break the normal compilation of import xxx.*
 //The @comple/ref... verifies the fix fixes the bug

import xxx.*;

@Anno
public class TestImportStar {
}
