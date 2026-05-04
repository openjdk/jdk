/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package doccheckutils.checkers;

import doccheckutils.FileChecker;
import doccheckutils.Log;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the contents of an HTML file for bad/unmappable characters.
 * <p>
 * The file encoding is determined from the file contents.
 */
public class BadCharacterChecker implements FileChecker, AutoCloseable {
    private static final Pattern doctype = Pattern.compile("(?i)<!doctype html>");
    private static final Pattern metaCharset = Pattern.compile("(?i)<meta\\s+charset=\"([^\"]+)\">");
    private static final Pattern metaContentType = Pattern.compile("(?i)<meta\\s+http-equiv=\"Content-Type\"\\s+content=\"text/html;charset=([^\"]+)\">");
    private final Log errors;
    private int files = 0;
    private int badFiles = 0;

    public BadCharacterChecker() {
        errors = new Log();
    }

    public void checkFile(Path path) {
        files++;
        boolean ok = true;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            CharsetDecoder d = getCharset(in).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, d));
            int lineNumber = 0;
            String line;
            try {
                while ((line = r.readLine()) != null) {
                    lineNumber++;
                    int errorsOnLine = 0;
                    for (int i = 0; i < line.length(); i++) {
                        char ch = line.charAt(i);
                        if (ch == 0xFFFD) {
                            errorsOnLine++;
                        }
                    }
                    if (errorsOnLine > 0) {
                        errors.log(path, lineNumber, "found %d invalid characters", errorsOnLine);
                        ok = false;
                    }
                }
            } catch (IOException e) {
                errors.log(path, lineNumber, e);
                ok = false;

            }
        } catch (IOException e) {
            errors.log(path, e);
            ok = false;
        }
        if (!ok)
            badFiles++;
    }

    @Override
    public void checkFiles(List<Path> files) {
        for (Path file : files) {
            checkFile(file);
        }
    }

    private Charset getCharset(InputStream in) throws IOException {
        CharsetDecoder initial = StandardCharsets.US_ASCII.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        in.mark(1024);
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, initial));
            char[] buf = new char[1024];
            int n = r.read(buf, 0, buf.length);
            String head = new String(buf, 0, n);
            boolean html5 = doctype.matcher(head).find();
            Matcher m1 = metaCharset.matcher(head);
            if (m1.find()) {
                return Charset.forName(m1.group(1));
            }
            Matcher m2 = metaContentType.matcher(head);
            if (m2.find()) {
                return Charset.forName(m2.group(1));
            }
            return html5 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1;
        } finally {
            in.reset();
        }
    }

    @Override
    public void report() {
        if (!errors.noErrors() && files > 0) {
            System.err.println("Bad characters found in the generated HTML");

            System.err.println(MessageFormat.format(
                    """
                            Bad Characters Report
                            {0} files read
                                {1} files contained bad characters"
                                {2} bad characters or other errors found
                            """,
                    files, badFiles, files));

            for (String s : errors.getErrors()) {
                System.err.println(s);
            }
            throw new RuntimeException("Bad character found in the generated HTML");
        }
    }

    @Override
    public boolean isOK() {
        return errors.noErrors();
    }

    @Override
    public void close() {
        report();
    }
}
