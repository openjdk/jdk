/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.test.stdmock;

public interface DebToolsMock extends EnvironmentMock {

    String versionDpkg();

    String versionFakeroot();

    boolean withPackageLookup();

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder {

        public DebToolsMock create() {
            return new DefaultDebToolsMock(versionDpkg, versionFakeroot, packageLookup);
        }

        public Builder env(DebToolsMock v) {
            return versionDpkg(v.versionDpkg()).versionFakeroot(v.versionFakeroot()).withPackageLookup(v.withPackageLookup());
        }

        public Builder versionDpkg(String v) {
            versionDpkg = v;
            return this;
        }

        public Builder versionFakeroot(String v) {
            versionFakeroot = v;
            return this;
        }

        public Builder withPackageLookup(boolean v) {
            if(v) {
                packageLookup = LinuxPackageLookupMock.ENABLED;
            } else {
                packageLookup = LinuxPackageLookupMock.DISABLED;
            }
            return this;
        }

        private String versionDpkg;
        private String versionFakeroot;
        private LinuxPackageLookupMock packageLookup = LinuxPackageLookupMock.DISABLED;
    }
}
