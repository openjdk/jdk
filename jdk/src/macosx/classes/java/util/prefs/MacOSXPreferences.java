/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package java.util.prefs;

class MacOSXPreferences extends AbstractPreferences {
    // fixme need security checks?

    // CF preferences file name for Java nodes with short names
    // This value is also in MacOSXPreferencesFile.c
    private static final String defaultAppName = "com.apple.java.util.prefs";

    // true if this node is a child of userRoot or is userRoot
    private boolean isUser;

    // true if this node is userRoot or systemRoot
    private boolean isRoot;

    // CF's storage location for this node and its keys
    private MacOSXPreferencesFile file;

    // absolutePath() + "/"
    private String path;

    // User root and system root nodes
    private static MacOSXPreferences userRoot = null;
    private static MacOSXPreferences systemRoot = null;


    // Returns user root node, creating it if necessary.
    // Called by MacOSXPreferencesFactory
    static synchronized Preferences getUserRoot() {
        if (userRoot == null) {
            userRoot = new MacOSXPreferences(true);
        }
        return userRoot;
    }


    // Returns system root node, creating it if necessary.
    // Called by MacOSXPreferencesFactory
    static synchronized Preferences getSystemRoot() {
        if (systemRoot == null) {
            systemRoot = new MacOSXPreferences(false);
        }
        return systemRoot;
    }


    // Create a new root node. Called by getUserRoot() and getSystemRoot()
    // Synchronization is provided by the caller.
    private MacOSXPreferences(boolean newIsUser)
    {
        super(null, "");
        isUser = newIsUser;
        isRoot = true;

        initFields();
    }


    // Create a new non-root node with the given parent.
    // Called by childSpi().
    private MacOSXPreferences(MacOSXPreferences parent, String name)
    {
        super(parent, name);
        isUser = isUserNode();
        isRoot = false;

        initFields();
    }


    private void initFields()
    {
        path = isRoot ? absolutePath() : absolutePath() + "/";
        file = cfFileForNode(isUser);
        newNode = file.addNode(path);
    }


    // Create and return the MacOSXPreferencesFile for this node.
    // Does not write anything to the file.
    private MacOSXPreferencesFile cfFileForNode(boolean isUser)
    {
        String name = path;
        // /one/two/three/four/five/
        // The fourth slash is the end of the first three components.
        // If there is no fourth slash, the name has fewer than 3 components
        int componentCount = 0;
        int pos = -1;
        for (int i = 0; i < 4; i++) {
            pos = name.indexOf('/', pos+1);
            if (pos == -1) break;
        }

        if (pos == -1) {
            // fewer than three components - use default name
            name = defaultAppName;
        } else {
            // truncate to three components, no leading or trailing '/'
            // replace '/' with '.' to make filesystem happy
            // convert to all lowercase to survive on HFS+
            name = name.substring(1, pos);
            name = name.replace('/', '.');
            name = name.toLowerCase();
        }

        return MacOSXPreferencesFile.getFile(name, isUser);
    }


    // AbstractPreferences implementation
    protected void putSpi(String key, String value)
    {
        file.addKeyToNode(path, key, value);
    }

    // AbstractPreferences implementation
    protected String getSpi(String key)
    {
        return file.getKeyFromNode(path, key);
    }

    // AbstractPreferences implementation
    protected void removeSpi(String key)
    {
        file.removeKeyFromNode(path, key);
    }


    // AbstractPreferences implementation
    protected void removeNodeSpi()
        throws BackingStoreException
    {
        // Disallow flush or sync between these two operations
        // (they may be manipulating two different files)
        synchronized(MacOSXPreferencesFile.class) {
            ((MacOSXPreferences)parent()).removeChild(name());
            file.removeNode(path);
        }
    }

    // Erase knowledge about a child of this node. Called by removeNodeSpi.
    private void removeChild(String child)
    {
        file.removeChildFromNode(path, child);
    }


    // AbstractPreferences implementation
    protected String[] childrenNamesSpi()
        throws BackingStoreException
    {
        String[] result = file.getChildrenForNode(path);
        if (result == null) throw new BackingStoreException("Couldn't get list of children for node '" + path + "'");
        return result;
    }

    // AbstractPreferences implementation
    protected String[] keysSpi()
        throws BackingStoreException
    {
        String[] result = file.getKeysForNode(path);
        if (result == null) throw new BackingStoreException("Couldn't get list of keys for node '" + path + "'");
        return result;
    }

    // AbstractPreferences implementation
    protected AbstractPreferences childSpi(String name)
    {
        // Add to parent's child list here and disallow sync
        // because parent and child might be in different files.
        synchronized(MacOSXPreferencesFile.class) {
            file.addChildToNode(path, name);
            return new MacOSXPreferences(this, name);
        }
    }

    // AbstractPreferences override
    public void flush()
        throws BackingStoreException
    {
        // Flush should *not* check for removal, unlike sync, but should
        // prevent simultaneous removal.
        synchronized(lock) {
            // fixme! overkill
            if (!MacOSXPreferencesFile.flushWorld()) {
                throw new BackingStoreException("Synchronization failed for node '" + path + "'");
            }
        }
    }

    // AbstractPreferences implementation
    protected void flushSpi()
        throws BackingStoreException
    {
        // nothing here - overridden flush() doesn't call this
    }

    // AbstractPreferences override
    public void sync()
        throws BackingStoreException
    {
        synchronized(lock) {
            if (isRemoved())
                throw new IllegalStateException("Node has been removed");
            // fixme! overkill
            if (!MacOSXPreferencesFile.syncWorld()) {
                throw new BackingStoreException("Synchronization failed for node '" + path + "'");
            }
        }
    }

    // AbstractPreferences implementation
    protected void syncSpi()
        throws BackingStoreException
    {
        // nothing here - overridden sync() doesn't call this
    }
}

