/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * JDK-8146147: Java linker indexed property getter does not work for computed nashorn string
 *
 * @test
 * @run
 */

var locale = java.util.Locale.ENGLISH;
var prop = 'ISO3Language';
var prop1 = 'ISO3';
var prop2 = prop1 + 'Language';
var prop3 = String(prop2);

function checkLang(obj) {
    if (obj != "eng") {
        throw new Error("FAILED: expected 'eng', got " + obj);
    }
}

checkLang(locale.ISO3Language);
checkLang(locale['ISO3Language']);
checkLang(locale[prop]);
checkLang(locale[prop1 + 'Language']);
checkLang(locale[prop2]);
checkLang(locale[prop3]);
checkLang(locale[String(prop1 + 'Language')]);
