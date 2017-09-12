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
 * NASHORN-105 :  parseFloat function is not spec. compliant.
 *
 * @test
 * @run
 */

print(parseFloat("3.14xyz"));
print(parseFloat("2.18 43.4543"));
print(parseFloat("2.9e8E-45"));
print(parseFloat("55654.6756.4546"));
print(parseFloat("343e"));
print(parseFloat("343e+"));
print(parseFloat("343e-"));
print(parseFloat("343e+35"));
print(parseFloat("Infinity1"));
print(parseFloat("-Infinity1"));
print(parseFloat("1ex"));
print(parseFloat("2343+"));
print(parseFloat("2ex"));

// invalid stuff
print(parseFloat(""));
print(parseFloat("+"));
print(parseFloat("-"));
print(parseFloat("e"));
print(parseFloat("sjdfhdsj"));
