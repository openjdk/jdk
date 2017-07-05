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

public class DriverResource extends ListResourceBundle {
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
                {VERSION,"{0} version {1}"},//parameter 0:class name;parameter 1: version value
                {BAD_ARGUMENT,"Bad argument: {0}"},
                {BAD_OPTION,"Bad option: {0}={1}"},//parameter 0:option name;parameter 1:option value
                {BAD_REPACK_OUTPUT,"Bad --repack output: {0}"},//parameter 0:filename
                {DETECTED_ZIP_COMMENT,"Detected ZIP comment: {0}"},//parameter 0:comment
                {SKIP_FOR_REPACKED,"Skipping because already repacked: {0}"},//parameter 0:filename
                {WRITE_PACK_FILE,"To write a *.pack file, specify --no-gzip: {0}"},//parameter 0:filename
                {WIRTE_PACKGZ_FILE,"To write a *.pack.gz file, specify --gzip: {0}"},//parameter 0:filename
                {SKIP_FOR_MOVE_FAILED,"Skipping unpack because move failed: {0}"},//parameter 0:filename
                {PACK_HELP,new String[]{
                                "Usage:  pack200 [-opt... | --option=value]... x.pack[.gz] y.jar",
                                "",
                                "Packing Options",
                                "  -g, --no-gzip                   output a plain *.pack file with no zipping",
                                "  --gzip                          (default) post-process the pack output with gzip",
                                "  -G, --strip-debug               remove debugging attributes while packing",
                                "  -O, --no-keep-file-order        do not transmit file ordering information",
                                "  --keep-file-order               (default) preserve input file ordering",
                                "  -S{N}, --segment-limit={N}      output segment limit (default N=1Mb)",
                                "  -E{N}, --effort={N}             packing effort (default N=5)",
                                "  -H{h}, --deflate-hint={h}       transmit deflate hint: true, false, or keep (default)",
                                "  -m{V}, --modification-time={V}  transmit modtimes: latest or keep (default)",
                                "  -P{F}, --pass-file={F}          transmit the given input element(s) uncompressed",
                                "  -U{a}, --unknown-attribute={a}  unknown attribute action: error, strip, or pass (default)",
                                "  -C{N}={L}, --class-attribute={N}={L}  (user-defined attribute)",
                                "  -F{N}={L}, --field-attribute={N}={L}  (user-defined attribute)",
                                "  -M{N}={L}, --method-attribute={N}={L} (user-defined attribute)",
                                "  -D{N}={L}, --code-attribute={N}={L}   (user-defined attribute)",
                                "  -f{F}, --config-file={F}        read file F for Pack200.Packer properties",
                                PARAMETER_V ,
                                PARAMETER_Q ,
                                PARAMETER_LF ,
                                PARAMETER_H ,
                                PARAMETER_VER ,
                                PARAMETER_J,
                                "",
                                "Notes:",
                                "  The -P, -C, -F, -M, and -D options accumulate.",
                                "  Example attribute definition:  -C SourceFile=RUH .",
                                "  Config. file properties are defined by the Pack200 API.",
                                "  For meaning of -S, -E, -H-, -m, -U values, see Pack200 API.",
                                "  Layout definitions (like RUH) are defined by JSR 200.",
                                "",
                                "Repacking mode updates the JAR file with a pack/unpack cycle:",
                                "    pack200 [-r|--repack] [-opt | --option=value]... [repackedy.jar] y.jar\n"
                                }
                },
                {UNPACK_HELP,new String[]{
                                "Usage:  unpack200 [-opt... | --option=value]... x.pack[.gz] y.jar\n",
                                "",
                                "Unpacking Options",
                                "  -H{h}, --deflate-hint={h}     override transmitted deflate hint: true, false, or keep (default)",
                                "  -r, --remove-pack-file        remove input file after unpacking",
                                PARAMETER_V ,
                                PARAMETER_Q ,
                                PARAMETER_LF ,
                                PARAMETER_H ,
                                PARAMETER_VER ,
                                PARAMETER_J,
                            }
                },

                {MORE_INFO,"(For more information, run {0} --help .)"},//parameter 0:command name
                {DUPLICATE_OPTION,"duplicate option: {0}"},//parameter 0:option
                {BAD_SPEC,"bad spec for {0}: {1}"},//parameter 0:option;parameter 1:specifier
        };

        protected Object[][] getContents() {
                return resource;
        }


}
