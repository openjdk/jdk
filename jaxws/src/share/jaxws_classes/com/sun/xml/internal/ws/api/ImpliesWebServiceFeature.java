/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api;

/**
 * Features, Providers, and JWS implementations can implement this interface to
 * receive a callback allowing them to modify the features enabled for a client
 * or endpoint binding.
 *
 * Implementations of this interface can make any changes they like to the set of
 * features; however, general best practice is that implementations should not
 * override features specified by the developer.  For instance, a Feature object
 * for WS-ReliableMessaging might use this interface to automatically enable
 * WS-Addressing (by adding the AddressingFeature), but not modify addressing if the
 * user had already specified a different addressing version.
 *
 * @since 2.2.6
 * @deprecated use {@link FeatureListValidatorAnnotation}
 */
public interface ImpliesWebServiceFeature {
        /**
         * Callback that may inspect the current feature list and add additional features
         * @param list Feature list
         */
        public void implyFeatures(WSFeatureList list);
}
