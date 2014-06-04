/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8030202: Nashorn: Multiple RegExp#ignoreCase issues
 *
 * @test
 * @run
 */

print(/\u2160/i.test("\u2170"));
print(/[\u2160]/i.test("\u2170"));
print(/\u2170/i.test("\u2160"));
print(/[\u2170]/i.test("\u2160"));

print(/\u0130/i.test("\u0069"));
print(/[\u0130]/i.test("\u0069"));
print(/\u0069/i.test("\u0130"));
print(/[\u0069]/i.test("\u0130"));

print(/\u1e9e/i.test("\u00df"));
print(/[\u1e9e]/i.test("\u00df"));
print(/\u00df/i.test("\u1e9e"));
print(/[\u00df]/i.test("\u1e9e"));

print(/[^\u1e9e]/i.test("\u00df"));
print(/[^\u00df]/i.test("\u1e9e"));

print(/\u0345{4}/i.test("\u0345\u0399\u03b9\u1fbe"));
print(/\u0399{4}/i.test("\u0345\u0399\u03b9\u1fbe"));
print(/\u03b9{4}/i.test("\u0345\u0399\u03b9\u1fbe"));
print(/\u1fbe{4}/i.test("\u0345\u0399\u03b9\u1fbe"));

print(/[\u0345]{4}/i.test("\u0345\u0399\u03b9\u1fbe"));
print(/[\u0399]{4}/i.test("\u0345\u0399\u03b9\u1fbe"));
print(/[\u03b9]{4}/i.test("\u0345\u0399\u03b9\u1fbe"));
print(/[\u1fbe]{4}/i.test("\u0345\u0399\u03b9\u1fbe"));
