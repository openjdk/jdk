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
            // Remove up to 3 leading slashes
            String afterSlashes;

            HostPattern hostPattern = null;
            Integer port = null;
            PathPattern pathPattern = null;
            // Handle file and jrt schemes as path patterns.
            boolean isLocalScheme = scheme.equals("file") || scheme.equals("jrt");
            if (isLocalScheme) {
                // Remove up to 3 leading slashes
                afterSlashes = rest.replaceFirst("^/{0,3}", "");
                // Should be only path or wildcard, must not be empty (file: is not allowed)
                if (afterSlashes.isEmpty())
                    throw new IllegalArgumentException(scheme + " rule must have non-empty path: " + pattern);
                // afterSlashes is the path pattern, can be "*"
                if (afterSlashes.equals("*")) {
                    pathPattern = PathPattern.of("*");
                } else {
                    if (!afterSlashes.startsWith("/")) afterSlashes = "/" + afterSlashes;
                    pathPattern = PathPattern.of(afterSlashes);
                }
            } else {
                // Remove up to 2 leading slashes
                afterSlashes = rest.replaceFirst("^/{0,2}", "");
                // Find "host[:port][/path]"
                if (afterSlashes.isEmpty() || afterSlashes.startsWith(":") || afterSlashes.startsWith("/")) {
                    throw new IllegalArgumentException("Rule for scheme '" + scheme + "' must specify a non-empty host: " + pattern);
                }

                String hostPart;
                String pathPart = null;
                int slashIndex = afterSlashes.indexOf('/');
                if (slashIndex >= 0) {
                    hostPart = afterSlashes.substring(0, slashIndex);
                    pathPart = afterSlashes.substring(slashIndex); // includes "/"
                } else {
                    hostPart = afterSlashes;
                }
                // Validate host
                if (hostPart.isEmpty())
                    throw new IllegalArgumentException("Host must not be blank for scheme: " + scheme);
                // Port
                int portSep = hostPart.lastIndexOf(':');
                if (portSep == hostPart.length() - 1) {
                    throw new IllegalArgumentException("Empty port: " + pattern);
                }
                if (portSep > 0 && portSep < hostPart.length() - 1
                    && isPortNumber(hostPart.substring(portSep + 1))) {
                    hostPattern = HostPattern.of(hostPart.substring(0, portSep));
                    try {
                        port = Integer.parseInt(hostPart.substring(portSep + 1));
                        if (port < 0 || port > 65535)
                            throw new IllegalArgumentException("Port must be 0-65535: " + pattern);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Port must be numeric: " + pattern);
                    }
                } else if (!hostPart.isEmpty()) {
                    hostPattern = HostPattern.of(hostPart);
                }

                // Validate path
                if (pathPart != null && pathPart.length() > 1 && pathPart.indexOf("//") >= 0)
                    throw new IllegalArgumentException("Path component must not contain empty segments: " + pattern);

                // pathPattern is set only if specified, null otherwise
                if (pathPart != null && !pathPart.isEmpty() && !pathPart.equals("/*")) {
                    pathPattern = PathPattern.of(pathPart);
                } else if (pathPart != null && (pathPart.isEmpty() || pathPart.equals("/*"))) {
                    pathPattern = PathPattern.of("*");
                }
            }
            return new URIPatternRule(scheme, hostPattern, port, pathPattern);
        }

        public boolean matches(URI uri) {
            if (uri != null && "jar".equalsIgnoreCase(uri.getScheme())) {
                URI jarFile = getJarFileURI(uri);
                return jarFile != null && matches(jarFile);
            }

            // Resource access matching applies only to hierarchical URIs. Opaque URIs
            // have no hierarchical host/port/path components to match.
            if (uri == null || uri.isOpaque()) return false;
            String testScheme = uri.getScheme();
            if (testScheme == null || !testScheme.equalsIgnoreCase(scheme)) return false;

            // Local: path-pattern match only
            if (hostPattern == null) {
                if (pathPattern == null) return true; // match all local of that scheme
                String uriPath = uri.getPath();
                return pathPattern.matches(uriPath);
            }

            // Network: host and port required
            String testHost = uri.getHost();
            if (!hostPattern.matches(testHost)) return false;
            if (port != null && port != (uri.getPort() == -1 ? getDefaultPort(scheme) : uri.getPort())) return false;
            // If a pathPattern is present, also match path; else, path is ignored
            if (pathPattern != null) {
                String uriPath = uri.getPath();
                return pathPattern.matches(uriPath);
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
         * Check if string is an integer between 0 and 65535 (valid TCP/UDP port range).
         */
        private static boolean isPortNumber(String str) {
            try {
                int port = Integer.parseInt(str);
                return port >= 0 && port <= 65535;
            } catch (NumberFormatException e) {
                return false;
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
        private final String pattern; // E.g. /dtds/* or /*
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
            if (pattern.endsWith("/*")) {
                return new PathPattern(normalizePath(pattern.substring(0, pattern.length() - 2)), false, true);
            }
            return new PathPattern(normalizePath(pattern), false, false);
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

        // Normalizes URI path for rule matching, consistent with URI.normalize().
        private static String normalizePath(String path) {
            boolean absolute = path.startsWith("/");
            List<String> segments = new ArrayList<>();
            for (String segment : path.split("/")) {
                if (segment.isEmpty() || segment.equals(".")) {
                    continue;
                }
                if (segment.equals("..")) {
                    if (!segments.isEmpty()) {
                        segments.remove(segments.size() - 1);
                    } else if (!absolute) {
                        segments.add(segment);
                    }
                } else {
                    segments.add(segment);
                }
            }
            String normalizedPath = String.join("/", segments);
            return absolute ? "/" + normalizedPath : normalizedPath;
        }
    }
}
