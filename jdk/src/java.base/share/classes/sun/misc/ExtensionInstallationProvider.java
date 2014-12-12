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

package sun.misc;

/**
 * This interface defines the contract a extension installation capable
 * provided to the extension installation dependency mechanism to
 * install new extensions on the user's disk
 *
 * @deprecated this class will be removed in a future release.
 * @author  Jerome Dochez
 */
@Deprecated
public interface ExtensionInstallationProvider {

    /*
     * <p>
     * Request the installation of an extension in the extension directory
     * </p>
     *
     * @param requestExtInfo information on the extension that need to be
     * installed
     * @param installedExtInfo information on the current compatible installed
     * extension. Can be null if no current installation has been found.
     * @return true if the installation is successful, false if the
     * installation could not be attempted.
     * @exception ExtensionInstallationException if an installation was
     * attempted but did not succeed.
     */
    boolean installExtension(ExtensionInfo requestExtInfo,
                             ExtensionInfo installedExtInfo)
        throws ExtensionInstallationException;
}
