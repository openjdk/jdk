/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.artifacts;

import java.io.Serial;

/**
 * Thrown by the ArtifactResolver when failing to resolve an Artifact.
 */
public class ArtifactResolverException extends Exception {
    @Serial
    private static final long serialVersionUID = 8341884506180926911L;

    public ArtifactResolverException(String message) {
        super(message);
    }

    public ArtifactResolverException(String message, Throwable cause) {
        super(message, cause);
    }

    public String toString() {
        Throwable root = getRootCause();
        if (root != null) {
            return super.toString() + ": " + root.toString();
        } else {
            return super.toString();
        }
    }

    public Throwable getRootCause() {
        Throwable root = getCause();
        if (root == null) {
            return null;
        }
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
}
