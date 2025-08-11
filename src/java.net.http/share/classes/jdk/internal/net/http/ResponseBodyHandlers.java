/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.ResponseInfo;
import java.net.http.HttpResponse.BodySubscriber;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.internal.net.http.ResponseSubscribers.PathSubscriber;
import jdk.internal.util.Exceptions;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

public final class ResponseBodyHandlers {

    private ResponseBodyHandlers() { }

    /**
     * A Path body handler.
     */
    public static class PathBodyHandler implements BodyHandler<Path>{
        private final Path file;
        private final List<OpenOption> openOptions;  // immutable list

        /**
         * Factory for creating PathBodyHandler.
         */
        public static PathBodyHandler create(Path file,
                                             List<OpenOption> openOptions) {

            return new PathBodyHandler(file, openOptions);
        }

        private PathBodyHandler(Path file,
                                List<OpenOption> openOptions) {
            this.file = file;
            this.openOptions = openOptions;
        }

        @Override
        public BodySubscriber<Path> apply(ResponseInfo responseInfo) {
            return new PathSubscriber(file, openOptions);
        }
    }

    /** With push promise Map implementation */
    public static class PushPromisesHandlerWithMap<T>
        implements HttpResponse.PushPromiseHandler<T>
    {
        private final ConcurrentMap<HttpRequest,CompletableFuture<HttpResponse<T>>> pushPromisesMap;
        private final Function<HttpRequest,BodyHandler<T>> pushPromiseHandler;

        public PushPromisesHandlerWithMap(Function<HttpRequest,BodyHandler<T>> pushPromiseHandler,
                                          ConcurrentMap<HttpRequest,CompletableFuture<HttpResponse<T>>> pushPromisesMap) {
            this.pushPromiseHandler = pushPromiseHandler;
            this.pushPromisesMap = pushPromisesMap;
        }

        @Override
        public void applyPushPromise(
                HttpRequest initiatingRequest, HttpRequest pushRequest,
                Function<BodyHandler<T>,CompletableFuture<HttpResponse<T>>> acceptor)
        {
            URI initiatingURI = initiatingRequest.uri();
            URI pushRequestURI = pushRequest.uri();
            if (!initiatingURI.getHost().equalsIgnoreCase(pushRequestURI.getHost()))
                return;

            String initiatingScheme = initiatingURI.getScheme();
            String pushRequestScheme = pushRequestURI.getScheme();

            if (!initiatingScheme.equalsIgnoreCase(pushRequestScheme)) return;

            int initiatingPort = initiatingURI.getPort();
            if (initiatingPort == -1 ) {
                if ("https".equalsIgnoreCase(initiatingScheme))
                    initiatingPort = 443;
                else
                    initiatingPort = 80;
            }
            int pushPort = pushRequestURI.getPort();
            if (pushPort == -1 ) {
                if ("https".equalsIgnoreCase(pushRequestScheme))
                    pushPort = 443;
                else
                    pushPort = 80;
            }
            if (initiatingPort != pushPort)
                return;

            CompletableFuture<HttpResponse<T>> cf =
                    acceptor.apply(pushPromiseHandler.apply(pushRequest));
            pushPromisesMap.put(pushRequest, cf);
        }
    }

    // Similar to Path body handler, but for file download.
    public static class FileDownloadBodyHandler implements BodyHandler<Path> {
        private final Path directory;
        private final List<OpenOption> openOptions;

        /**
         * Factory for creating FileDownloadBodyHandler.
         */
        public static FileDownloadBodyHandler create(Path directory,
                                                     List<OpenOption> openOptions) {
            try {
                directory.toFile().getPath();
            } catch (UnsupportedOperationException uoe) {
                // directory not associated with the default file system provider
                throw new IllegalArgumentException("invalid path: " + directory, uoe);
            }

            // existence, etc, checks must be after FS checks
            if (Files.notExists(directory))
                throw new IllegalArgumentException("non-existent directory: " + directory);
            if (!Files.isDirectory(directory))
                throw new IllegalArgumentException("not a directory: " + directory);
            if (!Files.isWritable(directory))
                throw new IllegalArgumentException("non-writable directory: " + directory);

            return new FileDownloadBodyHandler(directory, openOptions);
        }

        private FileDownloadBodyHandler(Path directory,
                                       List<OpenOption> openOptions) {
            this.directory = directory;
            this.openOptions = openOptions;
        }

        /** The "attachment" disposition-type and separator. */
        static final String DISPOSITION_TYPE = "attachment;";

        /** The "filename" parameter. */
        static final Pattern FILENAME = Pattern.compile("filename\\s*=\\s*", CASE_INSENSITIVE);

        static final List<String> PROHIBITED = List.of(".", "..", "", "~" , "|");

        // Characters disallowed in token values

        static final Set<Character> NOT_ALLOWED_IN_TOKEN = Set.of(
            '(', ')', '<', '>', '@',
            ',', ';', ':', '\\', '"',
            '/', '[', ']', '?', '=',
            '{', '}', ' ', '\t');

        static boolean allowedInToken(char c) {
            if (NOT_ALLOWED_IN_TOKEN.contains(c))
                return false;
            // exclude CTL chars <= 31, == 127, or anything >= 128
            return isTokenText(c);
        }

        static final UncheckedIOException unchecked(ResponseInfo rinfo,
                                                    String msg) {
            String s;
            if (Exceptions.enhancedNonSocketExceptions()) {
                s = String.format("%s in response [%d, %s]", msg, rinfo.statusCode(), rinfo.headers());
            } else {
                s = String.format("%s in response [%d]", msg, rinfo.statusCode());
            }
            return new UncheckedIOException(new IOException(s));
        }

        static final UncheckedIOException unchecked(String msg) {
            return new UncheckedIOException(new IOException(msg));
        }

        // Process a "filename=" parameter, which is either a "token"
        // or a "quoted string". If a token, it is terminated by a
        // semicolon or the end of the string.
        // If a quoted string (surrounded by "" chars then the closing "
        // terminates the name.
        // quoted strings may contain quoted-pairs (eg embedded " chars)

        static String processFilename(String src) throws UncheckedIOException {
            if ("".equals(src))
                return src;
            if (src.charAt(0) == '\"') {
                return processQuotedString(src.substring(1));
            } else {
                return processToken(src);
            }
        }

        static boolean isTokenText(char c) throws UncheckedIOException {
            return c > 31 && c < 127;
        }

        static boolean isQuotedStringText(char c) throws UncheckedIOException {
            return c > 31;
        }

        static String processQuotedString(String src) throws UncheckedIOException {
            boolean inqpair = false;
            int len = src.length();
            StringBuilder sb = new StringBuilder();

            for (int i=0; i<len; i++) {
                char c = src.charAt(i);
                if (!isQuotedStringText(c)) {
                    throw unchecked("Illegal character");
                }
                if (c == '\"') {
                    if (!inqpair) {
                        return sb.toString();
                    } else {
                        sb.append(c);
                    }
                } else if (c == '\\') {
                    if (!inqpair) {
                        inqpair = true;
                        continue;
                    } else {
                        // the quoted char is '\'
                        sb.append(c);
                    }
                } else {
                    sb.append(c);
                }
                if (inqpair) {
                    inqpair = false;
                }
            }
            // not terminated by "
            throw unchecked("Invalid quoted string");
        }

        static String processToken(String src) throws UncheckedIOException {
            int end = 0;
            int len = src.length();
            boolean whitespace = false;

            for (int i=0; i<len; i++) {
                char c = src.charAt(i);
                if (c == ';') {
                    break;
                }
                if (c == ' ' || c == '\t') {
                    // WS only until ; or end of string
                    whitespace = true;
                    continue;
                }
                end++;
                if (whitespace || !allowedInToken(c)) {
                    String msg = whitespace ? "whitespace must be followed by a semicolon"
                                            : c + " is not allowed in a token";
                    throw unchecked(msg);
                }
            }
            return src.substring(0, end);
        }

        @Override
        public BodySubscriber<Path> apply(ResponseInfo responseInfo) {
            String dispoHeader = responseInfo.headers().firstValue("Content-Disposition")
                    .orElseThrow(() -> unchecked(responseInfo, "No Content-Disposition header"));

            if (!dispoHeader.regionMatches(true, // ignoreCase
                                           0, DISPOSITION_TYPE,
                                           0, DISPOSITION_TYPE.length())) {
                throw unchecked(responseInfo, "Unknown Content-Disposition type");
            }

            Matcher matcher = FILENAME.matcher(dispoHeader);
            if (!matcher.find()) {
                throw unchecked(responseInfo, "Bad Content-Disposition filename parameter");
            }
            int n = matcher.end();

            String filenameParam = processFilename(dispoHeader.substring(n));

            // strip all but the last path segment
            int x = filenameParam.lastIndexOf("/");
            if (x != -1) {
                filenameParam = filenameParam.substring(x+1);
            }
            x = filenameParam.lastIndexOf("\\");
            if (x != -1) {
                filenameParam = filenameParam.substring(x+1);
            }

            filenameParam = filenameParam.trim();

            if (PROHIBITED.contains(filenameParam)) {
                throw unchecked(responseInfo,
                        "Prohibited Content-Disposition filename parameter:"
                                + filenameParam);
            }

            Path file = Paths.get(directory.toString(), filenameParam);

            if (!file.startsWith(directory)) {
                throw unchecked(responseInfo,
                        "Resulting file, " + file.toString() + ", outside of given directory");
            }

            return new PathSubscriber(file, openOptions);
        }
    }
}
