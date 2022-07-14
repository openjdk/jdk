/*
 * Copyright (c) 2022, Red Hat Inc. and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import jdk.jpackage.internal.regfile.RegFileKey;
import jdk.jpackage.internal.regfile.RegFileParseException;
import jdk.jpackage.internal.regfile.parser.Parser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;

class RegistryFile {

    static void verify(Path regFilePath) throws ConfigException {
        fetchFrom(regFilePath);
    }

    static List<RegFileKey> fetchFrom(Path regFilePath) throws ConfigException {
        if (null == regFilePath) {
            return List.of();
        }
        try (InputStream is = new BufferedInputStream(new FileInputStream(regFilePath.toFile()))) {
            Reader reader = new InputStreamReader(is, StandardCharsets.UTF_16);
            return new Parser(reader).parse();
        } catch (IOException | RegFileParseException e) {
            String msgKey = "error.cannot-read-registry-file";
            throw new ConfigException(
                    MessageFormat.format(I18N.getString(msgKey), e.getMessage()),
                    I18N.getString(msgKey + ".advice"));
        }
    }
}
