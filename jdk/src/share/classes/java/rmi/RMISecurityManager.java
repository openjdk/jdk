/*
 * Copyright (c) 1996, 2003, Oracle and/or its affiliates. All rights reserved.
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

package java.rmi;

import java.security.*;

/**
 * A subclass of {@link SecurityManager} used by RMI applications that use
 * downloaded code.  RMI's class loader will not download any classes from
 * remote locations if no security manager has been set.
 * <code>RMISecurityManager</code> does not apply to applets, which run
 * under the protection of their browser's security manager.
 *
 * <code>RMISecurityManager</code> implements a policy that
 * is no different than the policy implemented by {@link SecurityManager}.
 * Therefore an RMI application should use the <code>SecurityManager</code>
 * class or another application-specific <code>SecurityManager</code>
 * implementation instead of this class.
 *
 * <p>To use a <code>SecurityManager</code> in your application, add
 * the following statement to your code (it needs to be executed before RMI
 * can download code from remote hosts, so it most likely needs to appear
 * in the <code>main</code> method of your application):
 *
 * <pre>
 * System.setSecurityManager(new SecurityManager());
 * </pre>
 *
 * @author  Roger Riggs
 * @author  Peter Jones
 * @since JDK1.1
 **/
public class RMISecurityManager extends SecurityManager {

    /**
     * Constructs a new <code>RMISecurityManager</code>.
     * @since JDK1.1
     */
    public RMISecurityManager() {
    }
}
