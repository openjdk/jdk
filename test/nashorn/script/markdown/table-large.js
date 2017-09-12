/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Test for Showdown markdown parser work with Nashorn.
 *
 * @test
 * @run
 */

var input = "| First Header  | Second Header | Third Header  | Fourth Header |\n| ------------- | ------------- | ------------  | ------------- |\n| Row 1 Cell 1  | Row 1 Cell 2  | Row 1 Cell 3  | Row 1 Cell 4  |\n| Row 2 Cell 1  | Row 2 Cell 2  | Row 2 Cell 3  | Row 2 Cell 4  |\n| Row 3 Cell 1  | Row 3 Cell 2  | Row 3 Cell 3  | Row 3 Cell 4  |\n| Row 4 Cell 1  | Row 4 Cell 2  | Row 4 Cell 3  | Row 4 Cell 4  |\n| Row 5 Cell 1  | Row 5 Cell 2  | Row 5 Cell 3  | Row 5 Cell 4  |\n";
var output = converter.makeHtml(input);
print(output);
