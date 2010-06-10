/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

public class FileName {
    private String dir;
    private String prefix;
    private String stem;
    private String suffix;
    private String inverseDir;
    private String altSuffix;

    private String dpss;
    private String psa;
    private String dpsa;
    private String pss;

    private Platform plat;

    /** None of the passed strings may be null. */

    public FileName(Platform plat, String dir, String prefix,
                    String stem, String suffix,
                    String inverseDir, String altSuffix) {
        if ((dir == null) ||
            (prefix == null) ||
            (stem == null) ||
            (suffix == null) ||
            (inverseDir == null) ||
            (altSuffix == null)) {
            throw new NullPointerException("All arguments must be non-null");
        }

        this.plat = plat;

        this.dir = dir;
        this.prefix = prefix;
        this.stem = stem;
        this.suffix = suffix;
        this.inverseDir = inverseDir;
        this.altSuffix = altSuffix;

        pss = prefix + stem + suffix;
        dpss = dir + prefix + stem + suffix;
        psa = prefix + stem + altSuffix;
        dpsa = dir + prefix + stem + altSuffix;

        checkLength(plat);
    }

    public void checkLength(Platform p) {
        int len;
        String s;
        int suffLen = suffix.length();
        int altSuffLen = altSuffix.length();
        if (suffLen >= altSuffLen) {
            len = suffLen;
            s = suffix;
        } else {
            len = altSuffLen;
            s = altSuffix;
        }
        len += prefix.length() + stem.length();
        int lim = p.fileNameLengthLimit();
        if (len > lim) {
            p.fatalError(prefix + stem + s + " is too long: " +
                         len + " >= " + lim);
        }
    }

    public String dirPreStemSuff() {
        return dpss;
    }

    public String preStemSuff() {
        return pss;
    }

    public String dirPreStemAltSuff() {
        return dpsa;
    }

    public String preStemAltSuff() {
        return psa;
    }

    public FileName copyStem(String newStem) {
        return new FileName(plat, dir, prefix, newStem,
                            suffix, inverseDir, altSuffix);
    }

    String nameOfList() {
        return stem;
    }

    String getInvDir() {
        return inverseDir;
    }
}
