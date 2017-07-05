/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.sun.nio.zipfs;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.regex.PatternSyntaxException;

/**
 *
 * @author Xueming Shen
 */

class ZipUtils {

    /*
     * Writes a 16-bit short to the output stream in little-endian byte order.
     */
    public static void writeShort(OutputStream os, int v) throws IOException {
        os.write((v >>> 0) & 0xff);
        os.write((v >>> 8) & 0xff);
    }

    /*
     * Writes a 32-bit int to the output stream in little-endian byte order.
     */
    public static void writeInt(OutputStream os, long v) throws IOException {
        os.write((int)((v >>>  0) & 0xff));
        os.write((int)((v >>>  8) & 0xff));
        os.write((int)((v >>> 16) & 0xff));
        os.write((int)((v >>> 24) & 0xff));
    }

    /*
     * Writes a 64-bit int to the output stream in little-endian byte order.
     */
    public static void writeLong(OutputStream os, long v) throws IOException {
        os.write((int)((v >>>  0) & 0xff));
        os.write((int)((v >>>  8) & 0xff));
        os.write((int)((v >>> 16) & 0xff));
        os.write((int)((v >>> 24) & 0xff));
        os.write((int)((v >>> 32) & 0xff));
        os.write((int)((v >>> 40) & 0xff));
        os.write((int)((v >>> 48) & 0xff));
        os.write((int)((v >>> 56) & 0xff));
    }

    /*
     * Writes an array of bytes to the output stream.
     */
    public static void writeBytes(OutputStream os, byte[] b)
        throws IOException
    {
        os.write(b, 0, b.length);
    }

    /*
     * Writes an array of bytes to the output stream.
     */
    public static void writeBytes(OutputStream os, byte[] b, int off, int len)
        throws IOException
    {
        os.write(b, off, len);
    }

    /*
     * Append a slash at the end, if it does not have one yet
     */
    public static byte[] toDirectoryPath(byte[] dir) {
        if (dir.length != 0 && dir[dir.length - 1] != '/') {
            dir = Arrays.copyOf(dir, dir.length + 1);
            dir[dir.length - 1] = '/';
        }
        return dir;
    }

    /*
     * Converts DOS time to Java time (number of milliseconds since epoch).
     */
    public static long dosToJavaTime(long dtime) {
        Date d = new Date((int)(((dtime >> 25) & 0x7f) + 80),
                          (int)(((dtime >> 21) & 0x0f) - 1),
                          (int)((dtime >> 16) & 0x1f),
                          (int)((dtime >> 11) & 0x1f),
                          (int)((dtime >> 5) & 0x3f),
                          (int)((dtime << 1) & 0x3e));
        return d.getTime();
    }

    /*
     * Converts Java time to DOS time.
     */
    public static long javaToDosTime(long time) {
        Date d = new Date(time);
        int year = d.getYear() + 1900;
        if (year < 1980) {
            return (1 << 21) | (1 << 16);
        }
        return (year - 1980) << 25 | (d.getMonth() + 1) << 21 |
               d.getDate() << 16 | d.getHours() << 11 | d.getMinutes() << 5 |
               d.getSeconds() >> 1;
    }

    private static final String regexMetaChars = ".^$+{[]|()";
    private static final String globMetaChars = "\\*?[{";
    private static boolean isRegexMeta(char c) {
        return regexMetaChars.indexOf(c) != -1;
    }
    private static boolean isGlobMeta(char c) {
        return globMetaChars.indexOf(c) != -1;
    }
    private static char EOL = 0;  //TBD
    private static char next(String glob, int i) {
        if (i < glob.length()) {
            return glob.charAt(i);
        }
        return EOL;
    }

    /*
     * Creates a regex pattern from the given glob expression.
     *
     * @throws  PatternSyntaxException
     */
    public static String toRegexPattern(String globPattern) {
        boolean inGroup = false;
        StringBuilder regex = new StringBuilder("^");

        int i = 0;
        while (i < globPattern.length()) {
            char c = globPattern.charAt(i++);
            switch (c) {
                case '\\':
                    // escape special characters
                    if (i == globPattern.length()) {
                        throw new PatternSyntaxException("No character to escape",
                                globPattern, i - 1);
                    }
                    char next = globPattern.charAt(i++);
                    if (isGlobMeta(next) || isRegexMeta(next)) {
                        regex.append('\\');
                    }
                    regex.append(next);
                    break;
                case '/':
                    regex.append(c);
                    break;
                case '[':
                    // don't match name separator in class
                    regex.append("[[^/]&&[");
                    if (next(globPattern, i) == '^') {
                        // escape the regex negation char if it appears
                        regex.append("\\^");
                        i++;
                    } else {
                        // negation
                        if (next(globPattern, i) == '!') {
                            regex.append('^');
                            i++;
                        }
                        // hyphen allowed at start
                        if (next(globPattern, i) == '-') {
                            regex.append('-');
                            i++;
                        }
                    }
                    boolean hasRangeStart = false;
                    char last = 0;
                    while (i < globPattern.length()) {
                        c = globPattern.charAt(i++);
                        if (c == ']') {
                            break;
                        }
                        if (c == '/') {
                            throw new PatternSyntaxException("Explicit 'name separator' in class",
                                    globPattern, i - 1);
                        }
                        // TBD: how to specify ']' in a class?
                        if (c == '\\' || c == '[' ||
                                c == '&' && next(globPattern, i) == '&') {
                            // escape '\', '[' or "&&" for regex class
                            regex.append('\\');
                        }
                        regex.append(c);

                        if (c == '-') {
                            if (!hasRangeStart) {
                                throw new PatternSyntaxException("Invalid range",
                                        globPattern, i - 1);
                            }
                            if ((c = next(globPattern, i++)) == EOL || c == ']') {
                                break;
                            }
                            if (c < last) {
                                throw new PatternSyntaxException("Invalid range",
                                        globPattern, i - 3);
                            }
                            regex.append(c);
                            hasRangeStart = false;
                        } else {
                            hasRangeStart = true;
                            last = c;
                        }
                    }
                    if (c != ']') {
                        throw new PatternSyntaxException("Missing ']", globPattern, i - 1);
                    }
                    regex.append("]]");
                    break;
                case '{':
                    if (inGroup) {
                        throw new PatternSyntaxException("Cannot nest groups",
                                globPattern, i - 1);
                    }
                    regex.append("(?:(?:");
                    inGroup = true;
                    break;
                case '}':
                    if (inGroup) {
                        regex.append("))");
                        inGroup = false;
                    } else {
                        regex.append('}');
                    }
                    break;
                case ',':
                    if (inGroup) {
                        regex.append(")|(?:");
                    } else {
                        regex.append(',');
                    }
                    break;
                case '*':
                    if (next(globPattern, i) == '*') {
                        // crosses directory boundaries
                        regex.append(".*");
                        i++;
                    } else {
                        // within directory boundary
                        regex.append("[^/]*");
                    }
                    break;
                case '?':
                   regex.append("[^/]");
                   break;
                default:
                    if (isRegexMeta(c)) {
                        regex.append('\\');
                    }
                    regex.append(c);
            }
        }
        if (inGroup) {
            throw new PatternSyntaxException("Missing '}", globPattern, i - 1);
        }
        return regex.append('$').toString();
    }
}
