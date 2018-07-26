/*
 * Copyright (c) 2005, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.java.util.jar.pack;

import java.util.ListResourceBundle;

public class DriverResource_ja extends ListResourceBundle {

    public static final String VERSION = "VERSION";
    public static final String BAD_ARGUMENT = "BAD_ARGUMENT";
    public static final String BAD_OPTION = "BAD_OPTION";
    public static final String BAD_REPACK_OUTPUT = "BAD_REPACK_OUTPUT";
    public static final String DETECTED_ZIP_COMMENT = "DETECTED_ZIP_COMMENT";
    public static final String SKIP_FOR_REPACKED = "SKIP_FOR_REPACKED";
    public static final String WRITE_PACK_FILE = "WRITE_PACK_FILE";
    public static final String WRITE_PACKGZ_FILE = "WRITE_PACKGZ_FILE";
    public static final String SKIP_FOR_MOVE_FAILED = "SKIP_FOR_MOVE_FAILED";
    public static final String PACK_HELP = "PACK_HELP";
    public static final String UNPACK_HELP = "UNPACK_HELP";
    public static final String MORE_INFO = "MORE_INFO";
    public static final String DUPLICATE_OPTION = "DUPLICATE_OPTION";
    public static final String BAD_SPEC = "BAD_SPEC";
    public static final String DEPRECATED = "DEPRECATED";

    /*
     * The following are the output of 'pack200' and 'unpack200' commands.
     * Do not translate command arguments and words with a prefix of '-' or '--'.
     */
    private static final Object[][] resource = {
        {VERSION, "{0}\u30D0\u30FC\u30B8\u30E7\u30F3{1}"}, // parameter 0:class name;parameter 1: version value
        {BAD_ARGUMENT, "\u7121\u52B9\u306A\u5F15\u6570: {0}"},
        {BAD_OPTION, "\u7121\u52B9\u306A\u30AA\u30D7\u30B7\u30E7\u30F3: {0}={1}"}, // parameter 0:option name;parameter 1:option value
        {BAD_REPACK_OUTPUT, "\u7121\u52B9\u306A--repack\u51FA\u529B: {0}"}, // parameter 0:filename
        {DETECTED_ZIP_COMMENT, "\u691C\u51FA\u3055\u308C\u305FZIP\u30B3\u30E1\u30F3\u30C8: {0}"}, // parameter 0:comment
        {SKIP_FOR_REPACKED, "\u3059\u3067\u306B\u518D\u5727\u7E2E\u3055\u308C\u3066\u3044\u308B\u305F\u3081\u30B9\u30AD\u30C3\u30D7\u3057\u3066\u3044\u307E\u3059: {0}"}, // parameter 0:filename
        {WRITE_PACK_FILE, "*.pack\u30D5\u30A1\u30A4\u30EB\u3092\u66F8\u304D\u8FBC\u3080\u306B\u306F\u3001--no-gzip\u3092\u6307\u5B9A\u3057\u307E\u3059: {0}"}, // parameter 0:filename
        {WRITE_PACKGZ_FILE, "*.pack.gz\u30D5\u30A1\u30A4\u30EB\u3092\u66F8\u304D\u8FBC\u3080\u306B\u306F\u3001--gzip\u3092\u6307\u5B9A\u3057\u307E\u3059: {0}"}, // parameter 0:filename
        {SKIP_FOR_MOVE_FAILED, "\u79FB\u52D5\u304C\u5931\u6557\u3057\u305F\u305F\u3081\u89E3\u51CD\u3092\u30B9\u30AD\u30C3\u30D7\u3057\u3066\u3044\u307E\u3059: {0}"}, // parameter 0:filename
        {PACK_HELP, new String[] {
                "\u4F7F\u7528\u65B9\u6CD5:  pack200 [-opt... | --option=value]... x.pack[.gz] y.jar",
                "",
                "\u5727\u7E2E\u30AA\u30D7\u30B7\u30E7\u30F3",
                "  -r\u3001--repack                    jar\u3092\u518D\u5727\u7E2E\u307E\u305F\u306F\u6B63\u898F\u5316\u3059\u308B\u30AA\u30D7\u30B7\u30E7\u30F3\u3067\u3001",
                "                                  jarsigner\u306B\u3088\u308B\u7F72\u540D\u306B\u9069\u3057\u307E\u3059",
                "  -g\u3001--no-gzip                   \u30D7\u30EC\u30FC\u30F3\u306Apack\u30D5\u30A1\u30A4\u30EB\u3092\u51FA\u529B\u3059\u308B\u30AA\u30D7\u30B7\u30E7\u30F3\u3067\u3001",
                "                                  \u30D5\u30A1\u30A4\u30EB\u5727\u7E2E\u30E6\u30FC\u30C6\u30A3\u30EA\u30C6\u30A3\u306B\u3088\u308B\u5727\u7E2E\u306B\u9069\u3057\u307E\u3059",
                "  --gzip                          (\u30C7\u30D5\u30A9\u30EB\u30C8) pack\u51FA\u529B\u3092\u5F8C\u51E6\u7406\u3067\u5727\u7E2E\u3057\u307E\u3059",
                "                                  (gzip\u3092\u4F7F\u7528)",
                "  -G\u3001--strip-debug               \u5727\u7E2E\u4E2D\u306B\u30C7\u30D0\u30C3\u30B0\u5C5E\u6027(SourceFile\u3001",
                "                                  LineNumberTable\u3001LocalVariableTable",
                "                                  \u3001LocalVariableTypeTable)\u3092\u524A\u9664\u3057\u307E\u3059",
                "  -O\u3001--no-keep-file-order        \u30D5\u30A1\u30A4\u30EB\u306E\u9806\u5E8F\u4ED8\u3051\u60C5\u5831\u3092\u8EE2\u9001\u3057\u307E\u305B\u3093",
                "  --keep-file-order               (\u30C7\u30D5\u30A9\u30EB\u30C8)\u5165\u529B\u30D5\u30A1\u30A4\u30EB\u306E\u9806\u5E8F\u4ED8\u3051\u3092\u4FDD\u6301\u3057\u307E\u3059",
                "  -S{N}\u3001--segment-limit={N}      \u30BB\u30B0\u30E1\u30F3\u30C8\u30FB\u30B5\u30A4\u30BA\u3092\u5236\u9650\u3057\u307E\u3059(\u30C7\u30D5\u30A9\u30EB\u30C8\u306F\u7121\u5236\u9650)",
                "  -E{N}\u3001--effort={N}             \u5727\u7E2E\u306E\u8A66\u884C(\u30C7\u30D5\u30A9\u30EB\u30C8N=5)",
                "  -H{h}\u3001--deflate-hint={h}       \u30C7\u30D5\u30EC\u30FC\u30C8\u30FB\u30D2\u30F3\u30C8\u3092\u8EE2\u9001\u3057\u307E\u3059: true\u3001false",
                "                                  \u307E\u305F\u306Fkeep(\u30C7\u30D5\u30A9\u30EB\u30C8)",
                "  -m{V}\u3001--modification-time={V}  \u5909\u66F4\u6642\u9593\u3092\u8EE2\u9001\u3057\u307E\u3059: latest\u307E\u305F\u306Fkeep(\u30C7\u30D5\u30A9\u30EB\u30C8)",
                "  -P{F}\u3001--pass-file={F}          \u6307\u5B9A\u3055\u308C\u305F\u5165\u529B\u8981\u7D20\u3092\u305D\u306E\u307E\u307E\u8EE2\u9001\u3057\u307E\u3059",
                "  -U{a}\u3001--unknown-attribute={a}  \u4E0D\u660E\u306E\u5C5E\u6027\u30A2\u30AF\u30B7\u30E7\u30F3: error\u3001strip",
                "                                  \u307E\u305F\u306Fpass(\u30C7\u30D5\u30A9\u30EB\u30C8)",
                "  -C{N}={L}\u3001--class-attribute={N}={L}  (\u30E6\u30FC\u30B6\u30FC\u5B9A\u7FA9\u5C5E\u6027)",
                "  -F{N}={L}\u3001--field-attribute={N}={L}  (\u30E6\u30FC\u30B6\u30FC\u5B9A\u7FA9\u5C5E\u6027)",
                "  -M{N}={L}\u3001--method-attribute={N}={L} (\u30E6\u30FC\u30B6\u30FC\u5B9A\u7FA9\u5C5E\u6027)",
                "  -D{N}={L}\u3001--code-attribute={N}={L}   (\u30E6\u30FC\u30B6\u30FC\u5B9A\u7FA9\u5C5E\u6027)",
                "  -f{F}\u3001--config-file={F}        Pack200.Packer\u30D7\u30ED\u30D1\u30C6\u30A3\u306B\u30D5\u30A1\u30A4\u30EBF\u3092\u8AAD\u307F\u8FBC\u307F\u307E\u3059",
                "  -v\u3001--verbose                   \u30D7\u30ED\u30B0\u30E9\u30E0\u306E\u5197\u9577\u6027\u3092\u9AD8\u3081\u307E\u3059",
                "  -q\u3001--quiet                     \u5197\u9577\u6027\u3092\u6700\u4F4E\u30EC\u30D9\u30EB\u306B\u8A2D\u5B9A\u3057\u307E\u3059",
                "  -l{F}\u3001--log-file={F}           \u6307\u5B9A\u306E\u30ED\u30B0\u30FB\u30D5\u30A1\u30A4\u30EB\u307E\u305F\u306FSystem.out ",
                "                                  ('-'\u306E\u5834\u5408)\u306B\u51FA\u529B\u3057\u307E\u3059",
                "  -?\u3001-h\u3001--help                  \u3053\u306E\u30D8\u30EB\u30D7\u30FB\u30E1\u30C3\u30BB\u30FC\u30B8\u3092\u51FA\u529B\u3057\u307E\u3059",
                "  -V\u3001--version                   \u30D7\u30ED\u30B0\u30E9\u30E0\u306E\u30D0\u30FC\u30B8\u30E7\u30F3\u3092\u51FA\u529B\u3057\u307E\u3059",
                "  -J{X}                           \u30AA\u30D7\u30B7\u30E7\u30F3X\u3092\u57FA\u790E\u3068\u306A\u308BJava VM\u306B\u6E21\u3057\u307E\u3059",
                "",
                "\u6CE8:",
                "  -P\u3001-C\u3001-F\u3001-M\u304A\u3088\u3073-D\u30AA\u30D7\u30B7\u30E7\u30F3\u306F\u7D2F\u7A4D\u3055\u308C\u307E\u3059\u3002",
                "  \u5C5E\u6027\u5B9A\u7FA9\u306E\u4F8B:  -C SourceFile=RUH .",
                "  Config.\u30D5\u30A1\u30A4\u30EB\u30FB\u30D7\u30ED\u30D1\u30C6\u30A3\u306F\u3001Pack200 API\u306B\u3088\u3063\u3066\u5B9A\u7FA9\u3055\u308C\u307E\u3059\u3002",
                "  -S\u3001-E\u3001-H\u3001-m\u3001-U\u306E\u5024\u306E\u610F\u5473\u306F\u3001Pack200 API\u3092\u53C2\u7167\u3057\u3066\u304F\u3060\u3055\u3044\u3002",
                "  \u30EC\u30A4\u30A2\u30A6\u30C8\u5B9A\u7FA9(RUH\u306A\u3069)\u306FJSR 200\u306B\u3088\u3063\u3066\u5B9A\u7FA9\u3055\u308C\u307E\u3059\u3002",
                "",
                "\u518D\u5727\u7E2E\u30E2\u30FC\u30C9\u3067\u306F\u3001JAR\u30D5\u30A1\u30A4\u30EB\u304C\u5727\u7E2E/\u89E3\u51CD\u30B5\u30A4\u30AF\u30EB\u3067\u66F4\u65B0\u3055\u308C\u307E\u3059:",
                "    pack200 [-r|--repack] [-opt | --option=value]... [repackedy.jar] y.jar\n",
                "",
                "\u7D42\u4E86\u30B9\u30C6\u30FC\u30BF\u30B9:",
                "  0 (\u6210\u529F\u3057\u305F\u5834\u5408)\u3001>0 (\u30A8\u30E9\u30FC\u304C\u767A\u751F\u3057\u305F\u5834\u5408)"
            }
        },
        {UNPACK_HELP, new String[] {
                "\u4F7F\u7528\u65B9\u6CD5:  unpack200 [-opt... | --option=value]... x.pack[.gz] y.jar\n",
                "",
                "\u89E3\u51CD\u30AA\u30D7\u30B7\u30E7\u30F3",
                "  -H{h}\u3001--deflate-hint={h}     \u8EE2\u9001\u3055\u308C\u305F\u30C7\u30D5\u30EC\u30FC\u30C8\u30FB\u30D2\u30F3\u30C8\u3092\u30AA\u30FC\u30D0\u30FC\u30E9\u30A4\u30C9\u3057\u307E\u3059:",
                "                                 true\u3001false\u307E\u305F\u306Fkeep(\u30C7\u30D5\u30A9\u30EB\u30C8)",
                "  -r\u3001--remove-pack-file        \u89E3\u51CD\u5F8C\u306B\u5165\u529B\u30D5\u30A1\u30A4\u30EB\u3092\u524A\u9664\u3057\u307E\u3059",
                "  -v\u3001--verbose                 \u30D7\u30ED\u30B0\u30E9\u30E0\u306E\u5197\u9577\u6027\u3092\u9AD8\u3081\u307E\u3059",
                "  -q\u3001--quiet                   \u5197\u9577\u6027\u3092\u6700\u4F4E\u30EC\u30D9\u30EB\u306B\u8A2D\u5B9A\u3057\u307E\u3059",
                "  -l{F}\u3001--log-file={F}           \u6307\u5B9A\u306E\u30ED\u30B0\u30FB\u30D5\u30A1\u30A4\u30EB\u307E\u305F\u306F",
                "                                  System.out ('-'\u306E\u5834\u5408)\u306B\u51FA\u529B\u3057\u307E\u3059",
                "  -?\u3001-h\u3001--help                \u3053\u306E\u30D8\u30EB\u30D7\u30FB\u30E1\u30C3\u30BB\u30FC\u30B8\u3092\u51FA\u529B\u3057\u307E\u3059",
                "  -V\u3001--version                 \u30D7\u30ED\u30B0\u30E9\u30E0\u306E\u30D0\u30FC\u30B8\u30E7\u30F3\u3092\u51FA\u529B\u3057\u307E\u3059",
                "  -J{X}                         \u30AA\u30D7\u30B7\u30E7\u30F3X\u3092\u57FA\u790E\u3068\u306A\u308BJava VM\u306B\u6E21\u3057\u307E\u3059"
            }
        },
        {MORE_INFO, "(\u8A73\u7D30\u306F\u3001{0} --help\u3092\u5B9F\u884C\u3057\u3066\u304F\u3060\u3055\u3044\u3002)"}, // parameter 0:command name
        {DUPLICATE_OPTION, "\u91CD\u8907\u30AA\u30D7\u30B7\u30E7\u30F3: {0}"}, // parameter 0:option
        {BAD_SPEC, "{0}\u306E\u7121\u52B9\u306A\u4ED5\u69D8: {1}"}, // parameter 0:option;parameter 1:specifier
        {DEPRECATED, "\n\u8B66\u544A: {0}\u30C4\u30FC\u30EB\u306F\u975E\u63A8\u5968\u3067\u3042\u308A\u3001\u4ECA\u5F8C\u306EJDK\u30EA\u30EA\u30FC\u30B9\u3067\u524A\u9664\u3055\u308C\u308B\u4E88\u5B9A\u3067\u3059\u3002\n"} // parameter 0:command name
    };

    protected Object[][] getContents() {
        return resource;
    }
}
