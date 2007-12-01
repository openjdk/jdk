/*
 * Copyright 2000-2001 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.security.cert;

/**
 * A specification of certification path algorithm parameters.
 * The purpose of this interface is to group (and provide type safety for)
 * all <code>CertPath</code> parameter specifications. All
 * <code>CertPath</code> parameter specifications must implement this
 * interface.
 *
 * @author      Yassir Elley
 * @see         CertPathValidator#validate(CertPath, CertPathParameters)
 * @see         CertPathBuilder#build(CertPathParameters)
 * @since       1.4
 */
public interface CertPathParameters extends Cloneable {

  /**
   * Makes a copy of this <code>CertPathParameters</code>. Changes to the
   * copy will not affect the original and vice versa.
   *
   * @return a copy of this <code>CertPathParameters</code>
   */
  Object clone();
}
