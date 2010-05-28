/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt;

/**
  * The AWTSecurityManager class provides the ability to secondarily
  * index AppContext objects through SecurityManager extensions.
  * As noted in AppContext.java, AppContexts are primarily indexed by
  * ThreadGroup.  In the case where the ThreadGroup doesn't provide
  * enough information to determine AppContext (e.g. system threads),
  * if a SecurityManager is installed which derives from
  * AWTSecurityManager, the AWTSecurityManager's getAppContext()
  * method is called to determine the AppContext.
  *
  * A typical example of the use of this class is where an applet
  * is called by a system thread, yet the system AppContext is
  * inappropriate, because applet code is currently executing.
  * In this case, the getAppContext() method can walk the call stack
  * to determine the applet code being executed and return the applet's
  * AppContext object.
  *
  * @author  Fred Ecks
  */
public class AWTSecurityManager extends SecurityManager {

    /**
      * Get the AppContext corresponding to the current context.
      * The default implementation returns null, but this method
      * may be overridden by various SecurityManagers
      * (e.g. AppletSecurity) to index AppContext objects by the
      * calling context.
      *
      * @return  the AppContext corresponding to the current context.
      * @see     sun.awt.AppContext
      * @see     java.lang.SecurityManager
      * @since   JDK1.2.1
      */
    public AppContext getAppContext() {
        return null; // Default implementation returns null
    }

} /* class AWTSecurityManager */
