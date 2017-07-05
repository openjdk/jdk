/*
 * Copyright 1999-2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package javax.net.ssl;

/**
 * This is the base interface for JSSE key managers.
 * <P>
 * <code>KeyManager</code>s are responsible for managing the
 * key material which is used to authenticate the local SSLSocket
 * to its peer.  If no key material is available, the socket will
 * be unable to present authentication credentials.
 * <P>
 * <code>KeyManager</code>s are created by either
 * using a <code>KeyManagerFactory</code>,
 * or by implementing one of the <code>KeyManager</code> subclasses.
 *
 * @since 1.4
 * @see KeyManagerFactory
 */
public interface KeyManager {
}
