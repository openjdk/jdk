/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

public class DriverResource_zh_CN extends ListResourceBundle {
        public static final String VERSION ="VERSION";
        public static final String BAD_ARGUMENT ="BAD_ARGUMENT";
        public static final String BAD_OPTION ="BAD_OPTION";
        public static final String BAD_REPACK_OUTPUT="BAD_REPACK_OUTPUT";
        public static final String DETECTED_ZIP_COMMENT="DETECTED_ZIP_COMMENT";
        public static final String SKIP_FOR_REPACKED ="SKIP_FOR_REPACKED";
        public static final String WRITE_PACK_FILE ="WRITE_PACK_FILE";
        public static final String WIRTE_PACKGZ_FILE="WIRTE_PACKGZ_FILE";
        public static final String SKIP_FOR_MOVE_FAILED="SKIP_FOR_MOVE_FAILED";
        public static final String PACK_HELP="PACK_HELP";
        public static final String UNPACK_HELP ="UNPACK_HELP";
        public static final String MORE_INFO = "MORE_INFO";
        public static final String DUPLICATE_OPTION = "DUPLICATE_OPTION";
        public static final String BAD_SPEC = "BAD_SPEC";

        //The following string is duplicate in PACK and UNPACK comment,which was draw out to ruduce translation work.
        private static final String PARAMETER_V = "  -v, --verbose                   increase program verbosity";
        private static final String PARAMETER_Q = "  -q, --quiet                     set verbosity to lowest level";
        private static final String PARAMETER_LF = "  -l{F}, --log-file={F}           output to the given log file, or '-' for System.out";
        private static final String PARAMETER_H = "  -?, -h, --help                  print this message";
        private static final String PARAMETER_VER = "  -V, --version                   print program version";
        private static final String PARAMETER_J = "  -J{X}                           pass option X to underlying Java VM";


        //The following are outputs of command 'pack200' and 'unpack200'.
        //Don't translate command arguments ,words with a prefix of '-' or '--'.
        //
        private static final Object[][] resource= {
                {VERSION,"{0}\u7248\u672C{1}"},//parameter 0:class name;parameter 1: version value
                {BAD_ARGUMENT,"\u9519\u8BEF\u53C2\u6570: {0}"},
                {BAD_OPTION,"\u9519\u8BEF\u9009\u9879: {0}={1}"},//parameter 0:option name;parameter 1:option value
                {BAD_REPACK_OUTPUT,"--repack \u8F93\u51FA\u9519\u8BEF: {0}"},//parameter 0:filename
                {DETECTED_ZIP_COMMENT,"\u68C0\u6D4B\u5230 ZIP \u6CE8\u91CA: {0}"},//parameter 0:comment
                {SKIP_FOR_REPACKED,"\u7531\u4E8E\u5DF2\u91CD\u65B0\u6253\u5305\u800C\u8DF3\u8FC7: {0}"},//parameter 0:filename
                {WRITE_PACK_FILE,"\u8981\u5199\u5165 *.pack \u6587\u4EF6, \u8BF7\u6307\u5B9A --no-gzip: {0}"},//parameter 0:filename
                {WIRTE_PACKGZ_FILE,"\u8981\u5199\u5165 *.pack.gz \u6587\u4EF6, \u8BF7\u6307\u5B9A --gzip: {0}"},//parameter 0:filename
                {SKIP_FOR_MOVE_FAILED,"\u7531\u4E8E\u79FB\u52A8\u5931\u8D25\u800C\u8DF3\u8FC7\u91CD\u65B0\u6253\u5305: {0}"},//parameter 0:filename
                {PACK_HELP,new String[]{
                                "\u7528\u6CD5:  pack200 [-opt... | --option=value]... x.pack[.gz] y.jar",
                                "",
                                "\u6253\u5305\u9009\u9879",
                                "  -g, --no-gzip                   \u8F93\u51FA\u65E0\u683C\u5F0F\u7684 *.pack \u6587\u4EF6, \u4E0D\u538B\u7F29",
                                "  --gzip                          (\u9ED8\u8BA4\u503C) \u4F7F\u7528 gzip \u5BF9\u6253\u5305\u8FDB\u884C\u540E\u5904\u7406",
                                "  -G, --strip-debug               \u6253\u5305\u65F6\u5220\u9664\u8C03\u8BD5\u5C5E\u6027",
                                "  -O, --no-keep-file-order        \u4E0D\u4F20\u8F93\u6587\u4EF6\u6392\u5E8F\u4FE1\u606F",
                                "  --keep-file-order               (\u9ED8\u8BA4\u503C) \u4FDD\u7559\u8F93\u5165\u6587\u4EF6\u6392\u5E8F",
                                "  -S{N}, --segment-limit={N}      \u8F93\u51FA\u6BB5\u9650\u5236 (\u9ED8\u8BA4\u503C N=1Mb)",
                                "  -E{N}, --effort={N}             \u6253\u5305\u6548\u679C (\u9ED8\u8BA4\u503C N=5)",
                                "  -H{h}, --deflate-hint={h}       \u4F20\u8F93\u538B\u7F29\u63D0\u793A: true, false \u6216 keep (\u9ED8\u8BA4\u503C)",
                                "  -m{V}, --modification-time={V}  \u4F20\u8F93 modtimes: latest \u6216 keep (\u9ED8\u8BA4\u503C)",
                                "  -P{F}, --pass-file={F}          \u4F20\u8F93\u672A\u89E3\u538B\u7F29\u7684\u7ED9\u5B9A\u8F93\u5165\u5143\u7D20",
                                "  -U{a}, --unknown-attribute={a}  \u672A\u77E5\u5C5E\u6027\u64CD\u4F5C: error, strip \u6216 pass (\u9ED8\u8BA4\u503C)",
                                "  -C{N}={L}, --class-attribute={N}={L}  (\u7528\u6237\u5B9A\u4E49\u7684\u5C5E\u6027)",
                                "  -F{N}={L}, --field-attribute={N}={L}  (\u7528\u6237\u5B9A\u4E49\u7684\u5C5E\u6027)",
                                "  -M{N}={L}, --method-attribute={N}={L} (\u7528\u6237\u5B9A\u4E49\u7684\u5C5E\u6027)",
                                "  -D{N}={L}, --code-attribute={N}={L}   (\u7528\u6237\u5B9A\u4E49\u7684\u5C5E\u6027)",
                                "  -f{F}, --config-file={F}        \u8BFB\u53D6\u6587\u4EF6 F \u7684 Pack200.Packer \u5C5E\u6027",
                                PARAMETER_V ,
                                PARAMETER_Q ,
                                PARAMETER_LF ,
                                PARAMETER_H ,
                                PARAMETER_VER ,
                                PARAMETER_J,
                                "",
                                "\u6CE8:",
                                "  -P, -C, -F, -M \u548C -D \u9009\u9879\u7D2F\u8BA1\u3002",
                                "  \u793A\u4F8B\u5C5E\u6027\u5B9A\u4E49:  -C SourceFile=RUH\u3002",
                                "  Config. \u6587\u4EF6\u5C5E\u6027\u7531 Pack200 API \u5B9A\u4E49\u3002",
                                "  \u6709\u5173 -S, -E, -H-, -m, -U \u503C\u7684\u542B\u4E49, \u8BF7\u53C2\u9605 Pack200 API\u3002",
                                "  \u5E03\u5C40\u5B9A\u4E49 (\u4F8B\u5982 RUH) \u7531 JSR 200 \u5B9A\u4E49\u3002",
                                "",
                                "\u91CD\u65B0\u6253\u5305\u6A21\u5F0F\u901A\u8FC7\u6253\u5305/\u89E3\u5305\u5468\u671F\u66F4\u65B0 JAR \u6587\u4EF6:",
                                "    pack200 [-r|--repack] [-opt | --option=value]... [repackedy.jar] y.jar\n"
                                }
                },
                {UNPACK_HELP,new String[]{
                                "\u7528\u6CD5:  unpack200 [-opt... | --option=value]... x.pack[.gz] y.jar\n",
                                "",
                                "\u89E3\u5305\u9009\u9879",
                                "  -H{h}, --deflate-hint={h}     \u8986\u76D6\u5DF2\u4F20\u8F93\u7684\u538B\u7F29\u63D0\u793A: true, false \u6216 keep (\u9ED8\u8BA4\u503C)",
                                "  -r, --remove-pack-file        \u89E3\u5305\u4E4B\u540E\u5220\u9664\u8F93\u5165\u6587\u4EF6",
                                PARAMETER_V ,
                                PARAMETER_Q ,
                                PARAMETER_LF ,
                                PARAMETER_H ,
                                PARAMETER_VER ,
                                PARAMETER_J,
                            }
                },

                {MORE_INFO,"(\u6709\u5173\u8BE6\u7EC6\u4FE1\u606F, \u8BF7\u8FD0\u884C {0} --help\u3002)"},//parameter 0:command name
                {DUPLICATE_OPTION,"\u91CD\u590D\u7684\u9009\u9879: {0}"},//parameter 0:option
                {BAD_SPEC,"{0}\u7684\u89C4\u8303\u9519\u8BEF: {1}"},//parameter 0:option;parameter 1:specifier
        };

        protected Object[][] getContents() {
                return resource;
        }


}
