/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.model;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dotted numeric version string. E.g.: 1.0.37, 10, 0.5
 */
public final class DottedVersion {

    private DottedVersion(String version, boolean greedy) {
        this.value = version;
        if (version.isEmpty()) {
            if (greedy) {
                throw new IllegalArgumentException(I18N.getString("error.version-string-empty"));
            } else {
                this.components = new BigInteger[0];
                this.suffix = "";
            }
        } else {
            var ds = new DigitsSupplier(version);
            components = Stream.generate(ds::getNextDigits).takeWhile(Objects::nonNull).map(
                    digits -> {
                        if (digits.isEmpty()) {
                            if (!greedy) {
                                return null;
                            } else {
                                ds.throwException();
                            }
                        }

                        try {
                            return new BigInteger(digits);
                        } catch (NumberFormatException ex) {
                            if (!greedy) {
                                return null;
                            } else {
                                throw new IllegalArgumentException(MessageFormat.format(I18N.
                                        getString("error.version-string-invalid-component"), version,
                                        digits));
                            }
                        }
                    }).takeWhile(Objects::nonNull).toArray(BigInteger[]::new);
            suffix = ds.getUnprocessedString();
            if (!suffix.isEmpty() && greedy) {
                ds.throwException();
            }
        }
    }

    private static class DigitsSupplier {

        DigitsSupplier(String input) {
            this.input = input;
        }

        String getNextDigits() {
            if (stoped) {
                return null;
            }

            var sb = new StringBuilder();
            while (cursor != input.length()) {
                var chr = input.charAt(cursor++);
                if (Character.isDigit(chr)) {
                    sb.append(chr);
                } else {
                    var curStopAtDot = (chr == '.');
                    if (!curStopAtDot) {
                        if (lastDotPos >= 0) {
                            cursor = lastDotPos;
                        } else {
                            cursor--;
                        }
                        stoped = true;
                    } else if (!sb.isEmpty()) {
                        lastDotPos = cursor - 1;
                    } else {
                        cursor = Math.max(lastDotPos, 0);
                        stoped = true;
                    }
                    return sb.toString();
                }
            }

            if (sb.isEmpty()) {
                if (lastDotPos >= 0) {
                    cursor = lastDotPos;
                } else {
                    cursor--;
                }
            }

            stoped = true;
            return sb.toString();
        }

        String getUnprocessedString() {
            return input.substring(cursor);
        }

        void throwException() {
            final String tail;
            if (lastDotPos >= 0) {
                tail = input.substring(lastDotPos + 1);
            } else {
                tail = getUnprocessedString();
            }

            final String errMessage;
            if (tail.isEmpty()) {
                errMessage = MessageFormat.format(I18N.getString(
                        "error.version-string-zero-length-component"), input);
            } else {
                errMessage = MessageFormat.format(I18N.getString(
                        "error.version-string-invalid-component"), input, tail);
            }
            throw new IllegalArgumentException(errMessage);
        }

        private int cursor;
        private int lastDotPos = -1;
        private boolean stoped;
        private final String input;
    }

    public static DottedVersion greedy(String version) {
        return new DottedVersion(version, true);
    }

    public static DottedVersion lazy(String version) {
        return new DottedVersion(version, false);
    }

    public static int compareComponents(DottedVersion a, DottedVersion b) {
        int result = 0;
        BigInteger[] aComponents = a.getComponents();
        BigInteger[] bComponents = b.getComponents();
        for (int i = 0; i < Math.max(aComponents.length, bComponents.length)
                && result == 0; ++i) {
            final BigInteger x;
            if (i < aComponents.length) {
                x = aComponents[i];
            } else {
                x = BigInteger.ZERO;
            }

            final BigInteger y;
            if (i < bComponents.length) {
                y = bComponents[i];
            } else {
                y = BigInteger.ZERO;
            }
            result = x.compareTo(y);
        }

        return result;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 29 * hash + Arrays.deepHashCode(this.components);
        hash = 29 * hash + Objects.hashCode(this.suffix);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DottedVersion other = (DottedVersion) obj;
        if (!Objects.equals(this.suffix, other.suffix)) {
            return false;
        }
        return Arrays.deepEquals(this.components, other.components);
    }

    @Override
    public String toString() {
        return value;
    }

    public String getUnprocessedSuffix() {
        return suffix;
    }

    public String toComponentsString() {
        return Stream.of(components).map(BigInteger::toString).collect(Collectors.joining("."));
    }

    public BigInteger[] getComponents() {
        return components;
    }

    private final BigInteger[] components;
    private final String value;
    private final String suffix;
}
