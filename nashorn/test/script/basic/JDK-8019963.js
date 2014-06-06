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
 * JDK-8019963: empty char range in regex
 *
 * @test
 * @run
 */

var re1 = /[\x00-\x08\x0B\x0C\x0E-\x9F\uD800-\uDFFF\uFFFE\uFFFF]/;

print(re1.test("\x00"));
print(re1.test("\x04"));
print(re1.test("\x08"));
print(re1.test("\x0a"));
print(re1.test("\x0B"));
print(re1.test("\x0C"));
print(re1.test("\x0E"));
print(re1.test("\x10"));
print(re1.test("\x1A"));
print(re1.test("\x2F"));
print(re1.test("\x8E"));
print(re1.test("\x8F"));
print(re1.test("\x9F"));
print(re1.test("\xA0"));
print(re1.test("\xAF"));
print(re1.test("\uD800"));
print(re1.test("\xDA00"));
print(re1.test("\xDCFF"));
print(re1.test("\xDFFF"));
print(re1.test("\xFFFE"));
print(re1.test("\xFFFF"));

var re2 = /[\x1F\x7F-\x84\x86]/;

print(re2.test("\x1F"));
print(re2.test("\x2F"));
print(re2.test("\x3F"));
print(re2.test("\x7F"));
print(re2.test("\x80"));
print(re2.test("\x84"));
print(re2.test("\x85"));
print(re2.test("\x86"));

var re3 = /^([\x00-\x7F]|[\xC2-\xDF][\x80-\xBF]|\xE0[\xA0-\xBF][\x80-\xBF]|[\xE1-\xEC\xEE\xEF][\x80-\xBF]{2}|\xED[\x80-\x9F][\x80-\xBF]|\xF0[\x90-\xBF][\x80-\xBF]{2}|[\xF1-\xF3][\x80-\xBF]{3}|\xF4[\x80-\x8F][\x80-\xBF]{2})*$/;
