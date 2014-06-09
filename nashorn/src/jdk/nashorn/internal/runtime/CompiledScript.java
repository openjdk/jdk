/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

/**
 * Class representing a compiled script.
 */
final class CompiledScript implements Serializable {

    /** Main class name. */
    private final String mainClassName;

    /** Map of class names to class bytes. */
    private final Map<String, byte[]> classBytes;

    /** Constants array. */
    private final Object[] constants;

    /** The source */
    private transient Source source;

    private static final long serialVersionUID = 2958227232195298340L;

    /**
     * Constructor.
     *
     * @param mainClassName main class name
     * @param classBytes map of class names to class bytes
     * @param constants constants array
     */
    CompiledScript(final Source source, final String mainClassName, final Map<String, byte[]> classBytes, final Object[] constants) {
        this.source = source;
        this.mainClassName = mainClassName;
        this.classBytes = classBytes;
        this.constants = constants;
    }

    /**
     * Returns the main class name.
     * @return the main class name
     */
    public String getMainClassName() {
        return mainClassName;
    }

    /**
     * Returns a map of class names to class bytes.
     * @return map of class bytes
     */
    public Map<String, byte[]> getClassBytes() {
        return classBytes;
    }

    /**
     * Returns the constants array.
     * @return constants array
     */
    public Object[] getConstants() {
        return constants;
    }

    /**
     * Returns the source of this cached script.
     * @return the source
     */
    public Source getSource() {
        return source;
    }

    /**
     * Sets the source of this cached script.
     * @param source the source
     */
    void setSource(final Source source) {
        this.source = source;
    }

    @Override
    public int hashCode() {
        int hash = mainClassName.hashCode();
        hash = 31 * hash + classBytes.hashCode();
        hash = 31 * hash + Arrays.hashCode(constants);
        return hash;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof CompiledScript)) {
            return false;
        }

        final CompiledScript cs = (CompiledScript) obj;
        return mainClassName.equals(cs.mainClassName)
                && classBytes.equals(cs.classBytes)
                && Arrays.equals(constants, cs.constants);
    }
}
