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
 * JDK-8008370: coffee script compiler doesn't work with Nashorn
 *
 * @test
 * @run
 */

print(/\sx/.exec(" x"));
print(/[a\s]x/.exec(" x"));
print(/[a\s]x/.exec("ax"));
print(/[^a\s]x/.exec("bx"));
print(/[^a\s]x/.exec("ax"));
print(/[^a\s]x/.exec(" x"));

print(/\Sx/.exec("ax"));
print(/[a\S]x/.exec("ax"));
print(/[a\S]x/.exec("xx"));
print(/[^a\S]x/.exec("ax"));
print(/[^a\S]x/.exec("bx"));
print(/[^\n\S]x/.exec(" x"));
print(/[^ \S]x/.exec(" x"));
