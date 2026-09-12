/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.xml.internal;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Represents a parsed rule for matching external resource access permissions based on URI patterns.
 * <p>
 * This class encapsulates a resource access rule consisting of a scheme, optional host and port,
 * and path pattern. It is used to determine if a specific {@link java.net.URI} is permitted based on
 * rules specified with the {@code jdk.xml.resource.access} property.
 * </p>
 * <p>
 * Supported rule format:
 * <pre>
 *   [scheme]://host[:port][/path-pattern]
 *   [scheme]:/[path-pattern]     (for local schemes such as file and jrt)
 * </pre>
 * <ul>
 *   <li><b>scheme</b>: The URI scheme (e.g., http, https, ftp, file, jrt).</li>
 *   <li><b>host</b>: Domain name, IPv4, or IPv6 address. For local schemes ("file", "jrt"), host is omitted.</li>
 *   <li><b>port</b>: (optional) Port number to match. If omitted, matches the default port for the scheme.</li>
 *   <li><b>path-pattern</b>: (optional) Resource path. Supports wildcards (e.g., {@code /*}, {@code /dtds/*}).</li>
 * </ul>
 * <p>
 * Wildcards are allowed in host (e.g., <code>*.foo.com</code>) and path (e.g., <code>/*</code> or <code>/foo/*</code>).
 * </p>
 * <p>
 * Example patterns:
 * <ul>
 *   <li>{@code http://*.foo.com:8080/*} - allows HTTP resources on any subdomain of foo.com at port 8080, any path</li>
 *   <li>{@code file:/dtds/*} - allows all files under /dtds</li>
 *   <li>{@code file:/foo/bar.dtd} - allows only the local file /foo/bar.dtd</li>
 *   <li>{@code *} - allows unrestricted access</li>
 * </ul>
 * </p>
 * <p>
 * The {@link #allows(java.net.URI)} method determines whether a given URI is permitted according to this rule.
 * </p>
 */
public class AccessRule {
    public static final AccessRule RULE_NONE = new AccessRule("");
    public static final AccessRule RULE_ALL = new AccessRule("*");
    private final List<URIPatternRule> rules = new ArrayList<>();
    private final boolean allowAll;
    private final boolean denyAll;
    private final String rawInput;

    public AccessRule(String input) {
        this.rawInput = input;
        String trimmedInput = input == null ? "" : input.trim();
        if (trimmedInput.equals("*")) {
            allowAll = true;
            denyAll = false;
            return;
        } else if (trimmedInput.isEmpty()) {
            allowAll = false;
            denyAll = true;
            return;
        }
        allowAll = false;
        denyAll = false;
        String[] tokens = input.split(",");
        for (String rawToken : tokens) {
            String token = checkToken(input, rawToken);
            rules.add(URIPatternRule.parse(token));
        }
    }

    private String checkToken(String input, String rawToken) {
        String token = rawToken.trim();
        if (token.isEmpty() && !rules.isEmpty()) {
            throw new IllegalArgumentException("Invalid format for the resource.access property: "
                + input + ". Empty rule cannot coexist with other rules.");
        } else if (token.equals("*") && !rules.isEmpty()) {
            throw new IllegalArgumentException("Invalid format for the resource.access property: "
                + input + ". All access (*) rule cannot coexist with other rules.");
        }
        return token;
    }

    public boolean allows(URI uri) {
        if (denyAll) return false;
        if (allowAll) return true;
        for (URIPatternRule rule : rules) {
            if (rule.matches(uri)) return true;
        }
        return false;
    }

    @Override
    public String toString() { return rawInput; }

    /**
     * Represents a parsed URI-based pattern rule.
     */
    public static class URIPatternRule {
        private final String scheme;
        private final HostPattern hostPattern;
        private final Integer port;  // null if not set
        private final PathPattern pathPattern;
        private URIPatternRule(String scheme, HostPattern hostPattern, Integer port, PathPattern pathPattern) {
            this.scheme = scheme;
            this.hostPattern = hostPattern;
            this.port = port;
            this.pathPattern = pathPattern;
        }

        /**
         * Parses the specified pattern string.
         * Example patterns: file:*, file:/foo, http://foo.com, https://*.foo.com:8080
         * @param pattern the pattern string
         * @return an instance of URIPatternRule from the pattern string
         */
        public static URIPatternRule parse(String pattern) {
            // Syntax: [scheme]:/{0,3}[host[:port]][/path-pattern]
            int schemeSep = pattern.indexOf(':');
            if (schemeSep <= 0)
                throw new IllegalArgumentException("Missing or invalid scheme in resource access pattern: " + pattern);

            String scheme = pattern.substring(0, schemeSep).toLowerCase(Locale.ROOT);
            if (!isSupportedScheme(scheme))
                throw new IllegalArgumentException("Unsupported scheme in resource access pattern: " + pattern);

            String rest = pattern.substring(schemeSep + 1);
            int slashCount = getSlashAfterScheme(rest);

            if ("jrt".equals(scheme) || ("file".equals(scheme) && slashCount != 2)) {
                return parseLocalPath(scheme, slashCount, rest, pattern);
            }

            return parseAuthorityForm(scheme, slashCount, rest, pattern);
        }

        private static URIPatternRule parseAuthorityForm(String scheme, int slashCount,
            String rest, String pattern) {
            // Authority must have two leading slashes.
            if (slashCount != 2) {
                throw new IllegalArgumentException("Rule for scheme '" + scheme
                        + "' must specify an authority: " + pattern);
            }
            String afterSlashes = rest.substring(2);
            if (afterSlashes.isEmpty() || afterSlashes.startsWith(":") || afterSlashes.startsWith("/")) {
                throw new IllegalArgumentException("Rule for scheme '" + scheme
                        + "' must specify a non-empty host: " + pattern);
            }

            String hostPart;
            String pathPart = null;

            int slashIndex = afterSlashes.indexOf('/');
            if (slashIndex >= 0) {
                hostPart = afterSlashes.substring(0, slashIndex);
                pathPart = afterSlashes.substring(slashIndex);
            } else {
                hostPart = afterSlashes;
            }

            int portSep;
            int pos = 0;
            // Check IPv6 literal first as ':' within the brackets belong to the host
            if (hostPart.startsWith("[")) {
                pos = hostPart.indexOf(']');
                if (pos < 0) {
                    throw new IllegalArgumentException("Invalid IPv6 host: " + pattern);
                }
            }
            portSep = hostPart.indexOf(':', pos);

            String host;
            Integer port = null;

            if (portSep >= 0) {
                if ("file".equals(scheme)) {
                    throw new IllegalArgumentException("Port component is not supported for file URI: " + pattern);
                }
                host = hostPart.substring(0, portSep);
                String portPart = hostPart.substring(portSep + 1);
                port = parsePortNumber(portPart, hostPart);
            } else {
                host = hostPart;
            }

            HostPattern hostPattern = HostPattern.of(host);

            PathPattern pathPattern = null;
            if (pathPart != null) {
                if (pathPart.isEmpty() || pathPart.equals("/*")) {
                    pathPattern = PathPattern.of("*");
                } else {
                    pathPattern = PathPattern.of(pathPart);
                }
            }

            return new URIPatternRule(scheme, hostPattern, port, pathPattern);
        }

        private static URIPatternRule parseLocalPath(
            String scheme, int slashCount, String rest, String pattern) {

            // Remove the leading slashes
            String path = rest.substring(slashCount);

            if (path.isEmpty()) {
                throw new IllegalArgumentException(
                    scheme + " rule must have non-empty path: " + pattern);
            }

            if ("*".equals(path)) {
                return new URIPatternRule(scheme, null, null, PathPattern.of("*"));
            }

            // add a slash for the path pattern
            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            return new URIPatternRule(scheme, null, null, PathPattern.of(path));
        }

        //The slash following the scheme may appear one to three times.
        private static int getSlashAfterScheme(String s) {
            int count = 0;
            while (count < s.length() && s.charAt(count) == '/') {
                count++;
                if (count > 3) {
                    throw new IllegalArgumentException("Too many leading slashes: " + s);
                }
            }
            return count;
        }

        public boolean matches(URI uri) {
            if (uri != null && "jar".equalsIgnoreCase(uri.getScheme())) {
                URI jarFile = getJarFileURI(uri);
                return jarFile != null && matches(jarFile);
            }

            // Resource access matching applies only to hierarchical URIs. Opaque URIs
            // have no hierarchical host/port/path components to match.
            if (uri == null || uri.isOpaque()) return false;

            // match scheme
            String testScheme = uri.getScheme();
            if (testScheme == null || !testScheme.equalsIgnoreCase(scheme)) return false;

            // match host
            String testHost = uri.getHost();
            if (hostPattern == null) {
                // A hostless rule only matches a hostless URI.
                if (testHost != null) {
                    return false;
                }
            } else {
                // A host-based rule requires a matching host.
                if (!hostPattern.matches(testHost)) return false;
                if (port != null && port != (uri.getPort() == -1 ? getDefaultPort(scheme) : uri.getPort())) return false;
            }
            if (pathPattern != null) {
                return pathPattern.matches(uri.getPath());
            }

            return true;
        }

        private static URI getJarFileURI(URI jarURI) {
            String schemeSpecificPart = jarURI.getRawSchemeSpecificPart();
            int separator = schemeSpecificPart.indexOf("!/");
            if (separator < 0) {
                return null;
            }
            try {
                return URI.create(schemeSpecificPart.substring(0, separator));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        private static boolean isSupportedScheme(String scheme) {
            return switch (scheme) {
                case "http", "https", "ftp", "file", "jrt" -> true;
                default -> false;
            };
        }

        /**
         * {@return the port number.}
         * @throws IllegalArgumentException if portPart is not an integer
         * between 0 and 65535 (valid TCP/UDP port range).
         */
        private static int parsePortNumber(String portPart, String pattern) {
            if (portPart.isEmpty()) {
                throw new IllegalArgumentException("Empty port: " + pattern);
            }
            try {
                int port = Integer.parseInt(portPart);
                if (port < 0 || port > 65535)
                    throw new IllegalArgumentException("Port must be 0-65535: " + pattern);
                return port;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Port must be numeric: " + pattern, e);
            }
        }

        // standard ports for known schemes
        private static int getDefaultPort(String scheme) {
            return switch (scheme) {
                case "http" -> 80;
                case "https" -> 443;
                case "ftp" -> 21;
                default -> -1;
            };
        }
    }


    // Host pattern matching for exact, IPv4 and IPv6 hosts.
    public static class HostPattern {
        private final String pattern;
        private final boolean isAny;
        private final boolean isSubdomainPattern;
        private final byte[] literalAddress;

        private HostPattern(String pattern, boolean isAny, boolean isSubdomainPattern, byte[] literalAddress) {
            this.pattern = pattern;
            this.isAny = isAny;
            this.isSubdomainPattern = isSubdomainPattern;
            this.literalAddress = literalAddress;
        }

        public static HostPattern of(String hostPattern) {
            String trimmed = hostPattern.trim();
            if (trimmed.equals("*")) {
                return new HostPattern("*", true, false, null);
            }
            if (trimmed.startsWith("*.")) {
                // *.example.com
                return new HostPattern(trimmed.substring(2).toLowerCase(Locale.ROOT), false, true, null);
            }
            byte[] literalAddress = parseLiteralAddress(trimmed);
            if (literalAddress != null)
                return new HostPattern(trimmed, false, false, literalAddress);
            // Otherwise, treat as literal domain
            return new HostPattern(trimmed.toLowerCase(Locale.ROOT), false, false, null);
        }

        public boolean matches(String testHost) {
            if (isAny) return true;
            if (testHost == null) return false;
            if (literalAddress != null) {
                byte[] testAddress = parseLiteralAddress(testHost);
                return testAddress != null && Arrays.equals(literalAddress, testAddress);
            }
            testHost = testHost.toLowerCase(Locale.ROOT);
            // Subdomain wildcard
            if (isSubdomainPattern) {
                return testHost.endsWith("." + pattern);
            }
            // Exact match (domain, IPv4, or IPv6)
            return testHost.equals(pattern);
        }

        private static byte[] parseLiteralAddress(String host) {
            try {
                return Inet4Address.ofLiteral(host).getAddress();
            } catch (IllegalArgumentException ignored) {
                // Not an IPv4 literal; try IPv6 below.
            }

            try {
                return Inet6Address.ofLiteral(host).getAddress();
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public static class PathPattern {
        private final String pattern; // e.g. /dtds/* or /*
        private final boolean isAny;
        private final boolean isDirectory; // endsWith /*

        private PathPattern(String pattern, boolean isAny, boolean isDirectory) {
            this.pattern = pattern;
            this.isAny = isAny;
            this.isDirectory = isDirectory;
        }

        public static PathPattern of(String pattern) {
            pattern = (pattern == null || pattern.isEmpty()) ? "/" : pattern;
            // supports *, /foo/*, /foo/bar
            if (pattern.equals("*") || pattern.equals("/*")) {
                return new PathPattern(pattern, true, false);
            }
            // Now validate. Note: no validation needed if the whole component is a wildcard
            validatePath(pattern);
            if (pattern.endsWith("/*")) {
                return new PathPattern(pattern.substring(0, pattern.length() - 2), false, true);
            }
            return new PathPattern(pattern, false, false);
        }

        public boolean matches(String testPath) {
            if (isAny) return true;
            if (testPath == null) return false;
            testPath = normalizePath(testPath);
            if (isDirectory) {
                // Path starts with this directory
                return testPath.startsWith(pattern + "/") || testPath.equals(pattern);
            }
            return testPath.equals(pattern);
        }

        /*
         * Validates the path component. A path is either a literal file or directory
         * or wildcard (*).
         */
        private static void validatePath(String pathPart) {
            if (pathPart != null) {
                if (pathPart.indexOf("//") >= 0) {
                    throw new IllegalArgumentException(
                        "Path component must not contain empty segments: " + pathPart);
                }

                for (String segment : pathPart.split("/")) {
                    if (isDotSegment(segment)) {
                        throw new IllegalArgumentException(
                            "Path component must not contain dot segments: " + pathPart);
                    }
                }
            }
        }
    }

    // Normalizes URI path for rule matching, consistent with URI.normalize().
    private static String normalizePath(String path) {
        boolean absolute = path.startsWith("/");
        List<String> segments = new ArrayList<>();
        boolean isDotSegment = false;
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            // URI spec:  A ".." segment is removed only if it is preceded by a non-".." segment
            if (segment.equals("..")) {
                if (!segments.isEmpty() && !segments.get(segments.size() - 1).equals("..")) {
                    segments.remove(segments.size() - 1);
                    continue;
                }
            }
            segments.add(segment);
        }
        String normalizedPath = String.join("/", segments);
        return absolute ? "/" + normalizedPath : normalizedPath;
    }

    /*
     * Checks whether the specified path segment represents a dot segment,
     * {@code "."} or {@code ".."}, or encoded as {@code "%2e"}.
     */
    private static boolean isDotSegment(String segment) {
        int len = segment.length();

        if (len == 0 || len > 6) {
            return false;
        } else if (".".equals(segment) || "..".equals(segment)) {
            return true;
        } else if (segment.indexOf('%') < 0) {
            return false;
        }

        // encoded dot segment
        int dots = 0;
        for (int i = 0; i < len;) {
            if (segment.charAt(i) == '.') {
                dots++;
                i++;
            } else if (i + 2 < len
                && segment.charAt(i) == '%'
                && segment.charAt(i + 1) == '2'
                && (segment.charAt(i + 2) == 'e'
                || segment.charAt(i + 2) == 'E')) {
                dots++;
                i += 3;
            } else {
                return false;
            }
        }

        return dots == 1 || dots == 2;
    }
}
