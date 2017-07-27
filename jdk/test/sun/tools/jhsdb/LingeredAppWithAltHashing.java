/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.apps.LingeredApp;

public class LingeredAppWithAltHashing extends LingeredApp {

    public static void main(String args[]) {
        LingeredApp.main(args);
    }

    // Following strings generate the same hashcode

    static final String str1 = "AaAaAaAaAaAaAa";
    static final String str2 = "AaAaAaAaAaAaBB";
    static final String str3 = "AaAaAaAaAaBBAa";
    static final String str4 = "AaAaAaAaAaBBBB";
    static final String str5 = "AaAaAaAaBBAaAa";
    static final String str6 = "AaAaAaAaBBAaBB";
    static final String str7 = "AaAaAaAaBBBBAa";
    static final String str8 = "AaAaAaAaBBBBBB";
    static final String str9 = "AaAaAaBBAaAaAa";
    static final String str10 = "AaAaAaBBAaAaBB";
    static final String str11 = "AaAaAaBBAaBBAa";
    static final String str12 = "AaAaAaBBAaBBBB";
    static final String str13 = "AaAaAaBBBBAaAa";
    static final String str14 = "AaAaAaBBBBAaBB";
    static final String str15 = "AaAaAaBBBBBBAa";
    static final String str16 = "AaAaAaBBBBBBBB";
    static final String str17 = "AaAaBBAaAaAaAa";
    static final String str18 = "AaAaBBAaAaAaBB";
    static final String str19 = "AaAaBBAaAaBBAa";
    static final String str20 = "AaAaBBAaAaBBBB";
    static final String str21 = "AaAaBBAaBBAaAa";
    static final String str22 = "AaAaBBAaBBAaBB";
    static final String str23 = "AaAaBBAaBBBBAa";
    static final String str24 = "AaAaBBAaBBBBBB";
    static final String str25 = "AaAaBBBBAaAaAa";
    static final String str26 = "AaAaBBBBAaAaBB";
    static final String str27 = "AaAaBBBBAaBBAa";
    static final String str28 = "AaAaBBBBAaBBBB";
    static final String str29 = "AaAaBBBBBBAaAa";
    static final String str30 = "AaAaBBBBBBAaBB";
    static final String str31 = "AaAaBBBBBBBBAa";
    static final String str32 = "AaAaBBBBBBBBBB";
    static final String str33 = "AaBBAaAaAaAaAa";
    static final String str34 = "AaBBAaAaAaAaBB";
    static final String str35 = "AaBBAaAaAaBBAa";
    static final String str36 = "AaBBAaAaAaBBBB";
    static final String str37 = "AaBBAaAaBBAaAa";
    static final String str38 = "AaBBAaAaBBAaBB";
    static final String str39 = "AaBBAaAaBBBBAa";
    static final String str40 = "AaBBAaAaBBBBBB";
    static final String str41 = "AaBBAaBBAaAaAa";
    static final String str42 = "AaBBAaBBAaAaBB";
    static final String str43 = "AaBBAaBBAaBBAa";
    static final String str44 = "AaBBAaBBAaBBBB";
    static final String str45 = "AaBBAaBBBBAaAa";
    static final String str46 = "AaBBAaBBBBAaBB";
    static final String str47 = "AaBBAaBBBBBBAa";
    static final String str48 = "AaBBAaBBBBBBBB";
    static final String str49 = "AaBBBBAaAaAaAa";
    static final String str50 = "AaBBBBAaAaAaBB";
    static final String str51 = "AaBBBBAaAaBBAa";
    static final String str52 = "AaBBBBAaAaBBBB";
    static final String str53 = "AaBBBBAaBBAaAa";
    static final String str54 = "AaBBBBAaBBAaBB";
    static final String str55 = "AaBBBBAaBBBBAa";
    static final String str56 = "AaBBBBAaBBBBBB";
    static final String str57 = "AaBBBBBBAaAaAa";
    static final String str58 = "AaBBBBBBAaAaBB";
    static final String str59 = "AaBBBBBBAaBBAa";
    static final String str60 = "AaBBBBBBAaBBBB";
    static final String str61 = "AaBBBBBBBBAaAa";
    static final String str62 = "AaBBBBBBBBAaBB";
    static final String str63 = "AaBBBBBBBBBBAa";
    static final String str64 = "AaBBBBBBBBBBBB";
    static final String str65 = "BBAaAaAaAaAaAa";
    static final String str66 = "BBAaAaAaAaAaBB";
    static final String str67 = "BBAaAaAaAaBBAa";
    static final String str68 = "BBAaAaAaAaBBBB";
    static final String str69 = "BBAaAaAaBBAaAa";
    static final String str70 = "BBAaAaAaBBAaBB";
    static final String str71 = "BBAaAaAaBBBBAa";
    static final String str72 = "BBAaAaAaBBBBBB";
    static final String str73 = "BBAaAaBBAaAaAa";
    static final String str74 = "BBAaAaBBAaAaBB";
    static final String str75 = "BBAaAaBBAaBBAa";
    static final String str76 = "BBAaAaBBAaBBBB";
    static final String str77 = "BBAaAaBBBBAaAa";
    static final String str78 = "BBAaAaBBBBAaBB";
    static final String str79 = "BBAaAaBBBBBBAa";
    static final String str80 = "BBAaAaBBBBBBBB";
    static final String str81 = "BBAaBBAaAaAaAa";
    static final String str82 = "BBAaBBAaAaAaBB";
    static final String str83 = "BBAaBBAaAaBBAa";
    static final String str84 = "BBAaBBAaAaBBBB";
    static final String str85 = "BBAaBBAaBBAaAa";
    static final String str86 = "BBAaBBAaBBAaBB";
    static final String str87 = "BBAaBBAaBBBBAa";
    static final String str88 = "BBAaBBAaBBBBBB";
    static final String str89 = "BBAaBBBBAaAaAa";
    static final String str90 = "BBAaBBBBAaAaBB";
    static final String str91 = "BBAaBBBBAaBBAa";
    static final String str92 = "BBAaBBBBAaBBBB";
    static final String str93 = "BBAaBBBBBBAaAa";
    static final String str94 = "BBAaBBBBBBAaBB";
    static final String str95 = "BBAaBBBBBBBBAa";
    static final String str96 = "BBAaBBBBBBBBBB";
    static final String str97 = "BBBBAaAaAaAaAa";
    static final String str98 = "BBBBAaAaAaAaBB";
    static final String str99 = "BBBBAaAaAaBBAa";
    static final String str100 = "BBBBAaAaAaBBBB";
    static final String str101 = "BBBBAaAaBBAaAa";
    static final String str102 = "BBBBAaAaBBAaBB";
    static final String str103 = "BBBBAaAaBBBBAa";
    static final String str104 = "BBBBAaAaBBBBBB";
    static final String str105 = "BBBBAaBBAaAaAa";
    static final String str106 = "BBBBAaBBAaAaBB";
    static final String str107 = "BBBBAaBBAaBBAa";
    static final String str108 = "BBBBAaBBAaBBBB";
    static final String str109 = "BBBBAaBBBBAaAa";
    static final String str110 = "BBBBAaBBBBAaBB";
    static final String str111 = "BBBBAaBBBBBBAa";
    static final String str112 = "BBBBAaBBBBBBBB";
    static final String str113 = "BBBBBBAaAaAaAa";
    static final String str114 = "BBBBBBAaAaAaBB";
    static final String str115 = "BBBBBBAaAaBBAa";
    static final String str116 = "BBBBBBAaAaBBBB";
    static final String str117 = "BBBBBBAaBBAaAa";
    static final String str118 = "BBBBBBAaBBAaBB";
    static final String str119 = "BBBBBBAaBBBBAa";
    static final String str120 = "BBBBBBAaBBBBBB";
    static final String str121 = "BBBBBBBBAaAaAa";
    static final String str122 = "BBBBBBBBAaAaBB";
    static final String str123 = "BBBBBBBBAaBBAa";
    static final String str124 = "BBBBBBBBAaBBBB";
    static final String str125 = "BBBBBBBBBBAaAa";
    static final String str126 = "BBBBBBBBBBAaBB";
    static final String str127 = "BBBBBBBBBBBBAa";
    static final String str128 = "BBBBBBBBBBBBBB";
 }
