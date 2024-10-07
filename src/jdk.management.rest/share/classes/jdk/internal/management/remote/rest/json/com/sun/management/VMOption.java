/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.management.remote.rest.json.com.sun.management;

import jdk.internal.management.remote.rest.json.JSONArray;
import jdk.internal.management.remote.rest.json.JSONObject;

import static com.sun.management.VMOption.Origin;

/**
 */
public class VMOption extends com.sun.management.VMOption {

    /**
     */
    public VMOption(String name, String value, boolean writeable, Origin origin) {
        super(name, value, writeable, origin);
    }

    /**
     * Returns a {@code VMOption} object represented by the
     * given {@code CompositeData}. The given {@code CompositeData}
     * must contain the following attributes:
     *
     * <blockquote>
     * <table class="striped"><caption style="display:none">description</caption>
     * <thead>
     * <tr>
     *   <th scope="col" style="text-align:left">Attribute Name</th>
     *   <th scope="col" style="text-align:left">Type</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     *   <th scope="row">name</th>
     *   <td>{@code java.lang.String}</td>
     * </tr>
     * <tr>
     *   <th scope="row">value</th>
     *   <td>{@code java.lang.String}</td>
     * </tr>
     * <tr>
     *   <th scope="row">origin</th>
     *   <td>{@code java.lang.String}</td>
     * </tr>
     * <tr>
     *   <th scope="row">writeable</th>
     *   <td>{@code java.lang.Boolean}</td>
     * </tr>
     * </tbody>
     * </table>
     * </blockquote>
     *
     * @param cd {@code CompositeData} representing a {@code VMOption}
     *
     * @throws IllegalArgumentException if {@code cd} does not
     *   represent a {@code VMOption} with the attributes described
     *   above.
     *
     * @return a {@code VMOption} object represented by {@code cd}
     *         if {@code cd} is not {@code null};
     *         {@code null} otherwise.
     */
    public static VMOption from(JSONObject json) {
        if (json == null) {
            return null;
        }

        String name = JSONObject.getObjectFieldString(json, "name");
        String value = JSONObject.getObjectFieldString(json, "value");
        boolean writeable = JSONObject.getObjectFieldBoolean(json, "writeable");
        VMOption.Origin origin = VMOption.Origin.OTHER; // XXXX  JSONObject.getObjectFieldString(json, "origin");

        return new VMOption(name, value, writeable, origin);
    }
}
