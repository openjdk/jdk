/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Provides the classes and interfaces for the security framework.
 * This includes classes that implement an easily configurable,
 * fine-grained access control security architecture.
 * This package also supports
 * the generation and storage of cryptographic public key pairs,
 * as well as a number of exportable cryptographic operations
 * including those for message digest and signature generation.  Finally,
 * this package provides classes that support signed/guarded objects
 * and secure random number generation.
 *
 * Many of the classes provided in this package (the cryptographic
 * and secure random number generator classes in particular) are
 * provider-based.  The class itself defines a programming interface
 * to which applications may write.  The implementations themselves may
 * then be written by independent third-party vendors and plugged
 * in seamlessly as needed.  Therefore application developers may
 * take advantage of any number of provider-based implementations
 * without having to add or rewrite code.
 *
 * <h2>Package Specification</h2>
 *
 * <ul>
 *   <li><a href="{@docRoot}/../technotes/guides/security/crypto/CryptoSpec.html">
 *     <b>Java&trade;
 *     Cryptography Architecture (JCA) Reference Guide</b></a></li>
 *
 *   <li>PKCS #8: Private-Key Information Syntax Standard, Version 1.2,
 *     November 1993</li>
 *
 *   <li><a href="{@docRoot}/../technotes/guides/security/StandardNames.html">
 *     <b>Java&trade;
 *     Cryptography Architecture Standard Algorithm Name
 *     Documentation</b></a></li>
 * </ul>
 *
 * <h2>Related Documentation</h2>
 *
 * For further documentation, please see:
 * <ul>
 *   <li><a href=
 *     "{@docRoot}/../technotes/guides/security/spec/security-spec.doc.html">
 *     <b>Java&trade;
 *     SE Platform Security Architecture</b></a></li>
 *
 *   <li><a href=
 *     "{@docRoot}/../technotes/guides/security/crypto/HowToImplAProvider.html">
 *     <b>How to Implement a Provider in the
 *     Java&trade; Cryptography Architecture
 *     </b></a></li>
 *
 *   <li><a href=
 *     "{@docRoot}/../technotes/guides/security/PolicyFiles.html"><b>
 *     Default Policy Implementation and Policy File Syntax
 *     </b></a></li>
 *
 *   <li><a href=
 *     "{@docRoot}/../technotes/guides/security/permissions.html"><b>
 *     Permissions in the
 *     Java&trade; SE Development Kit (JDK)
 *     </b></a></li>
 *
 *   <li><a href=
 *     "{@docRoot}/../technotes/guides/security/SecurityToolsSummary.html"><b>
 *     Summary of Tools for
 *     Java&trade; Platform Security
 *     </b></a></li>
 *
 *   <li><b>keytool</b>
 *     (<a href="{@docRoot}/../technotes/tools/solaris/keytool.html">
 *       for Solaris/Linux</a>)
 *     (<a href="{@docRoot}/../technotes/tools/windows/keytool.html">
 *       for Windows</a>)
 *     </li>
 *
 *   <li><b>jarsigner</b>
 *     (<a href="{@docRoot}/../technotes/tools/solaris/jarsigner.html">
 *       for Solaris/Linux</a>)
 *     (<a href="{@docRoot}/../technotes/tools/windows/jarsigner.html">
 *       for Windows</a>)
 *     </li>
 *
 * </ul>
 *
 * @since 1.1
 */
package java.security;
