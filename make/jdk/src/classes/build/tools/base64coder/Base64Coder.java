/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.base64coder;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Simple base64 en/decoder
 *
 * usage: java Base64Coder [-d] -i <inputfile> -o <outputfile>
 * where
 *   -d : decoding operation, otherise encoding operation
 *   -i : input file path
 *   -o : ouput file path
 */
public class Base64Coder {
    private static boolean decode;
    private static Path input;
    private static Path output;

    public static void main(String[] args) throws IOException {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-d" -> decode = true;
                case "-i" -> input = i < args.length -1 ? Path.of(args[++i]) : null;
                case "-o" -> output = i < args.length-1 ? Path.of(args[++i]) : null;
                default -> {
                    System.err.println("invalid argument: " + args[i]);
                    System.exit(-1);
                }
            }
        }

        if (input == null || output == null) {
            System.err.println("no input or output files provided");
            System.exit(-1);
        }

        try (OutputStream os = Files.newOutputStream(output)) {
            if (decode) {
                os.write(Base64.getDecoder().decode(Files.readAllBytes(input)));
            } else {
                os.write(Base64.getEncoder().encode(Files.readAllBytes(input)));
            }
        }
    }
}
