/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.fs;

import java.nio.file.InvalidPathException;

/**
 * A parser of Windows path strings
 */

class WindowsPathParser {
    private WindowsPathParser() { }

    /**
     * The result of a parse operation
     */
    record Result(WindowsPathType type, String root, String path) {};

    /**
     * Parses the given input as a Windows path
     */
    static Result parse(String input) {
        return parse(input, true);
    }

    /**
     * Parses the given input as a Windows path where it is known that the
     * path is already normalized.
     */
    static Result parseNormalizedPath(String input) {
        return parse(input, false);
    }

    /**
     * Parses the given input as a Windows path.
     *
     * @param   requireToNormalize
     *          Indicates if the path requires to be normalized
     */
    private static Result parse(String input, boolean requireToNormalize) {
        // if a prefix is present, remove it and note the expected path type
        final WindowsPathType expectedType;
        if (input.startsWith("\\\\?\\")) {
            if (input.startsWith("UNC\\", 4)) {
                expectedType = WindowsPathType.UNC;
                input = "\\\\" + input.substring(8);
            } else {
                expectedType = WindowsPathType.ABSOLUTE;
                input = input.substring(4);
            }
        } else {
            expectedType = null;
        }

        String root = "";
        WindowsPathType type = null;

        int len = input.length();
        int off = 0;
        if (len > 1) {
            char c0 = input.charAt(0);
            char c1 = input.charAt(1);
            char c = 0;
            int next = 2;
            if (isSlash(c0) && isSlash(c1)) {
                // UNC: We keep the first two slashes, collapse all the
                // following, then take the hostname and share name out,
                // meanwhile collapsing all the redundant slashes.
                type = WindowsPathType.UNC;
                off = nextNonSlash(input, next, len);
                next = nextSlash(input, off, len);
                if (off == next)
                    throw new InvalidPathException(input, "UNC path is missing hostname");
                String host = input.substring(off, next);  //host
                off = nextNonSlash(input, next, len);
                next = nextSlash(input, off, len);
                if (off == next)
                    throw new InvalidPathException(input, "UNC path is missing sharename");
                root = "\\\\" + host + "\\" + input.substring(off, next) + "\\";
                off = next;
            } else {
                if (isLetter(c0) && c1 == ':') {
                    char c2;
                    if (len > 2 && isSlash(c2 = input.charAt(2))) {
                        // avoid concatenation when root is "D:\"
                        if (c2 == '\\') {
                            root = input.substring(0, 3);
                        } else {
                            root = input.substring(0, 2) + '\\';
                        }
                        off = 3;
                        type = WindowsPathType.ABSOLUTE;
                    } else {
                        root = input.substring(0, 2);
                        off = 2;
                        type = WindowsPathType.DRIVE_RELATIVE;
                    }
                }
            }
        }
        if (off == 0) {
            if (len > 0 && isSlash(input.charAt(0))) {
                type = WindowsPathType.DIRECTORY_RELATIVE;
                root = "\\";
            } else {
                type = WindowsPathType.RELATIVE;
            }
        }

        if (expectedType != null && type != expectedType) {
            if (expectedType == WindowsPathType.ABSOLUTE) { // long path prefix
                throw new InvalidPathException(input, "Long path prefix can only be used with an absolute path");
            } else if (expectedType == WindowsPathType.UNC) { // long UNC path prefix
                throw new InvalidPathException(input, "Long UNC path prefix can only be used with a UNC path");
            }
        }

        if (requireToNormalize) {
            return new Result(type, root, normalize(root, input, off));
        } else {
            return new Result(type, root, input);
        }
    }

    /**
     * Remove redundant slashes from the rest of the path, forcing all slashes
     * into the preferred slash.
     */
    private static String normalize(String root, String path, int pathOff) {

        int rootLen = root.length();
        int pathLen = path.length();

        // the result array will initally contain the characters of root in
        // the first rootLen elements followed by the chanacters of path from
        // position index pathOff to the end of path
        char[] result = new char[rootLen + pathLen - pathOff];
        root.getChars(0, rootLen, result, 0);
        path.getChars(pathOff, pathLen, result, rootLen);

        // the portion of array derived from path is normalized by copying
        // from position srcPos to position dstPos, and as the invariant
        // dstPos <= srcPos holds, no characters can be overwritten
        int dstPos = rootLen;
        int srcPos = nextNonSlash(result, rootLen, result.length);

        // pathPos is the position in array which is being tested as to
        // whether the element at that position is a slash
        int pathPos = srcPos;

        char lastC = 0;
        while (pathPos < result.length) {
            char c = result[pathPos];
            if (isSlash(c)) {
                if (lastC == ' ')
                    throw new InvalidPathException(path,
                                                   "Trailing char <" + lastC + ">",
                                                   pathPos - 1);
                int nchars = pathPos - srcPos;
                System.arraycopy(result, srcPos, result, dstPos, nchars);
                dstPos += nchars;
                pathPos = nextNonSlash(result, pathPos, result.length);
                if (pathPos != result.length)   //no slash at the end of normalized path
                    result[dstPos++] = '\\';
                srcPos = pathPos;
            } else {
                if (isInvalidPathChar(c))
                    throw new InvalidPathException(path,
                                                   "Illegal char <" + c + ">",
                                                   pathPos);
                lastC = c;
                pathPos++;
            }
        }
        if (srcPos != pathPos) {
            if (lastC == ' ')
                throw new InvalidPathException(path,
                                               "Trailing char <" + lastC + ">",
                                               pathPos - 1);
            int nchars = pathPos - srcPos;
            System.arraycopy(result, srcPos, result, dstPos, nchars);
            dstPos += nchars;
        }
        return new String(result, 0, dstPos);
    }

    private static final boolean isSlash(char c) {
        return (c == '\\') || (c == '/');
    }

    private static final int nextNonSlash(String path, int off, int end) {
        while (off < end && isSlash(path.charAt(off))) { off++; }
        return off;
    }

    private static final int nextNonSlash(char[] path, int off, int end) {
        while (off < end && isSlash(path[off])) { off++; }
        return off;
    }

    private static final int nextSlash(String path, int off, int end) {
        char c;
        while (off < end && !isSlash(c=path.charAt(off))) {
            if (isInvalidPathChar(c))
                throw new InvalidPathException(path,
                                               "Illegal character [" + c + "] in path",
                                               off);
            off++;
        }
        return off;
    }

    private static final int nextSlash(char[] path, int off, int end) {
        char c;
        while (off < end && !isSlash(c=path[off])) {
            if (isInvalidPathChar(c))
                throw new InvalidPathException(new String(path),
                                               "Illegal character [" + c + "] in path",
                                               off);
            off++;
        }
        return off;
    }

    private static final boolean isLetter(char c) {
        return ((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z'));
    }

    // Reserved characters for window path name
    private static final String reservedChars = "<>:\"|?*";
    private static final boolean isInvalidPathChar(char ch) {
        return ch < '\u0020' || reservedChars.indexOf(ch) != -1;
    }
}
