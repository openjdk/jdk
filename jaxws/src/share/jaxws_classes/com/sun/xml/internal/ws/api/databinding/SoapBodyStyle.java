/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.databinding;

/**
 * The SoapBodyStyle represents the possible choices of the mapping styles
 * between the SOAP body of a wsdl:operation input/output messages and JAVA
 * method parameters and return/output values.
 *
 * @author shih-chang.chen@oracle.com
 */
public enum SoapBodyStyle {

        /**
         * Bare style mapping of a document style with literal use wsdl:operation
         */
        DocumentBare,

        /**
         * Wrapper style mapping of a document style with literal use wsdl:operation
         */
        DocumentWrapper,

        /**
         * The mapping style of a RPC style with literal use wsdl:operation
         */
        RpcLiteral,

        /**
         * The mapping style of a RPC style with encoded use wsdl:operation.
         *
         * Note: this is not offically supported in JAX-WS.
         */
        RpcEncoded,

        /**
         * The mapping style is not specified.
         */
        Unspecificed;

        public boolean isDocument() {
                return (this.equals(DocumentBare) || this.equals(DocumentWrapper));
        }

        public boolean isRpc() {
                return (this.equals(RpcLiteral) || this.equals(RpcEncoded));
        }

        public boolean isLiteral() {
                return (this.equals(RpcLiteral) || this.isDocument());
        }

        public boolean isBare() {
                return (this.equals(DocumentBare));
        }

        public boolean isDocumentWrapper() {
                return (this.equals(DocumentWrapper));
        }
}
