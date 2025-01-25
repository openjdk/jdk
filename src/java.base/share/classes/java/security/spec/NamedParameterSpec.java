/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package java.security.spec;

import java.util.Objects;

/**
 * This class is used to specify any algorithm parameters that are determined
 * by a standard name. This class also holds constants for standard parameter
 * set names. The names of these constants exactly match the corresponding
 * parameter set name. For example, {@code NamedParameterSpec.X25519}
 * represents the parameter set identified by the string "X25519". These
 * strings are defined in the <a href=
 * "{@docRoot}/../specs/security/standard-names.html#namedparameterspec">
 *          Java Security Standard Algorithm Names Specification</a>.
 *
 * @since 11
 *
 */
public class NamedParameterSpec implements AlgorithmParameterSpec {

   /**
    * The X25519 parameters
    */
    public static final NamedParameterSpec X25519
        = new NamedParameterSpec("X25519");
   /**
    * The X448 parameters
    */
    public static final NamedParameterSpec X448
        = new NamedParameterSpec("X448");

    /**
     * The Ed25519 parameters
     *
     * @since 15
     */
    public static final NamedParameterSpec ED25519
        = new NamedParameterSpec("Ed25519");

    /**
     * The Ed448 parameters
     *
     * @since 15
     */
    public static final NamedParameterSpec ED448
        = new NamedParameterSpec("Ed448");

    /**
     * The ML-DSA-44 parameters
     *
     * @since 24
     */
    public static final NamedParameterSpec ML_DSA_44
        = new NamedParameterSpec("ML-DSA-44");

    /**
     * The ML-DSA-65 parameters
     *
     * @since 24
     */
    public static final NamedParameterSpec ML_DSA_65
        = new NamedParameterSpec("ML-DSA-65");

    /**
     * The ML-DSA-87 parameters
     *
     * @since 24
     */
    public static final NamedParameterSpec ML_DSA_87
        = new NamedParameterSpec("ML-DSA-87");

    /**
     * The ML-KEM-512 parameters
     *
     * @since 24
     */
    public static final NamedParameterSpec ML_KEM_512
            = new NamedParameterSpec("ML-KEM-512");

    /**
     * The ML-KEM-768 parameters
     *
     * @since 24
     */
    public static final NamedParameterSpec ML_KEM_768
            = new NamedParameterSpec("ML-KEM-768");

    /**
     * The ML-KEM-1024 parameters
     *
     * @since 24
     */
    public static final NamedParameterSpec ML_KEM_1024
            = new NamedParameterSpec("ML-KEM-1024");

    private final String name;

    /**
     * Creates a parameter specification using a standard (or predefined)
     * name {@code stdName}. For the
     * list of supported names, please consult the documentation
     * of the provider whose implementation will be used.
     *
     * @param stdName the standard name of the algorithm parameters. See the
     *        NamedParameterSpec section in the
     *        <a href=
     *        "{@docRoot}/../specs/security/standard-names.html#namedparameterspec">
     *        Java Security Standard Algorithm Names Specification</a> for
     *        information about standard names.
     *
     * @throws NullPointerException if {@code stdName} is null.
     */
    public NamedParameterSpec(String stdName) {
        Objects.requireNonNull(stdName, "stdName must not be null");

        this.name = stdName;
    }

    /**
     * Returns the standard name that determines the algorithm parameters.
     * @return the standard name.
     */
    public String getName() {
        return name;
    }
}
