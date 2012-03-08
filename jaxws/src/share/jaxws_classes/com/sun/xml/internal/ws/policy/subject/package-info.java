/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Provides utility classes and objects that can serve as policy subjects for
 * {@link com.sun.xml.internal.ws.policy.PolicySubject}. The current implementation provides
 * subjects for WSDL 1.0/1.1 binding elements.
 *
 * We are not trying to provide an exact model of WSDL elements. The JAX-WS
 * WSDLModel does that already. Instead, we are aiming at providing a light-weight
 * and easy to use representation of WSDL elements.
 *
 * At the same time, this implementation is providing a simple way of mapping the
 * subjects to WSDL scopes. That limits how the WsdlSubjects can be used. Ultimately,
 * each subject is always linked to one service, port and binding element. That
 * means that WsdlSubjects cannot accurately represent e.g. a WSDL message element
 * that is referenced by multiple WSDL binding operations.
 */
package com.sun.xml.internal.ws.policy.subject;
