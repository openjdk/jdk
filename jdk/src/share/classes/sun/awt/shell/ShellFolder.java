/*
 * Copyright 2000-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.awt.shell;

import java.awt.Image;
import java.awt.Toolkit;
import java.io.*;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * @author Michael Martak
 * @since 1.4
 */

public abstract class ShellFolder extends File {
    protected ShellFolder parent;

    /**
     * Create a file system shell folder from a file
     */
    ShellFolder(ShellFolder parent, String pathname) {
        super((pathname != null) ? pathname : "ShellFolder");
        this.parent = parent;
    }

    /**
     * @return Whether this is a file system shell folder
     */
    public boolean isFileSystem() {
        return (!getPath().startsWith("ShellFolder"));
    }

    /**
     * This method must be implemented to make sure that no instances
     * of <code>ShellFolder</code> are ever serialized. If <code>isFileSystem()</code> returns
     * <code>true</code>, then the object should be representable with an instance of
     * <code>java.io.File</code> instead. If not, then the object is most likely
     * depending on some internal (native) state and cannot be serialized.
     *
     * @returns a <code>java.io.File</code> replacement object, or <code>null</code>
     * if no suitable replacement can be found.
     */
    protected abstract Object writeReplace() throws java.io.ObjectStreamException;

    /**
     * Returns the path for this object's parent,
     * or <code>null</code> if this object does not name a parent
     * folder.
     *
     * @return  the path as a String for this object's parent,
     * or <code>null</code> if this object does not name a parent
     * folder
     *
     * @see java.io.File#getParent()
     * @since 1.4
     */
    public String getParent() {
        if (parent == null && isFileSystem()) {
            return super.getParent();
        }
        if (parent != null) {
            return (parent.getPath());
        } else {
            return null;
        }
    }

    /**
     * Returns a File object representing this object's parent,
     * or <code>null</code> if this object does not name a parent
     * folder.
     *
     * @return  a File object representing this object's parent,
     * or <code>null</code> if this object does not name a parent
     * folder
     *
     * @see java.io.File#getParentFile()
     * @since 1.4
     */
    public File getParentFile() {
        if (parent != null) {
            return parent;
        } else if (isFileSystem()) {
            return super.getParentFile();
        } else {
            return null;
        }
    }

    public File[] listFiles() {
        return listFiles(true);
    }

    public File[] listFiles(boolean includeHiddenFiles) {
        File[] files = super.listFiles();

        if (!includeHiddenFiles) {
            Vector v = new Vector();
            int nameCount = (files == null) ? 0 : files.length;
            for (int i = 0; i < nameCount; i++) {
                if (!files[i].isHidden()) {
                    v.addElement(files[i]);
                }
            }
            files = (File[])v.toArray(new File[v.size()]);
        }

        return files;
    }


    /**
     * @return Whether this shell folder is a link
     */
    public abstract boolean isLink();

    /**
     * @return The shell folder linked to by this shell folder, or null
     * if this shell folder is not a link
     */
    public abstract ShellFolder getLinkLocation() throws FileNotFoundException;

    /**
     * @return The name used to display this shell folder
     */
    public abstract String getDisplayName();

    /**
     * @return The type of shell folder as a string
     */
    public abstract String getFolderType();

    /**
     * @return The executable type as a string
     */
    public abstract String getExecutableType();

    /**
     * Compares this ShellFolder with the specified ShellFolder for order.
     *
     * @see #compareTo(Object)
     */
    public int compareTo(File file2) {
        if (file2 == null || !(file2 instanceof ShellFolder)
            || ((file2 instanceof ShellFolder) && ((ShellFolder)file2).isFileSystem())) {

            if (isFileSystem()) {
                return super.compareTo(file2);
            } else {
                return -1;
            }
        } else {
            if (isFileSystem()) {
                return 1;
            } else {
                return getName().compareTo(file2.getName());
            }
        }
    }

    /**
     * @param getLargeIcon whether to return large icon (ignored in base implementation)
     * @return The icon used to display this shell folder
     */
    public Image getIcon(boolean getLargeIcon) {
        return null;
    }


    // Static

    private static ShellFolderManager shellFolderManager;

    static {
        Class managerClass = (Class)Toolkit.getDefaultToolkit().
            getDesktopProperty("Shell.shellFolderManager");
        if (managerClass == null) {
            managerClass = ShellFolderManager.class;
        }
        try {
            shellFolderManager =
                (ShellFolderManager)managerClass.newInstance();
        } catch (InstantiationException e) {
            throw new Error("Could not instantiate Shell Folder Manager: "
            + managerClass.getName());
        } catch (IllegalAccessException e) {
            throw new Error ("Could not access Shell Folder Manager: "
            + managerClass.getName());
        }
    }

    /**
     * Return a shell folder from a file object
     * @exception FileNotFoundException if file does not exist
     */
    public static ShellFolder getShellFolder(File file) throws FileNotFoundException {
        if (file instanceof ShellFolder) {
            return (ShellFolder)file;
        }
        if (!file.exists()) {
            throw new FileNotFoundException();
        }
        return shellFolderManager.createShellFolder(file);
    }

    /**
     * @param key a <code>String</code>
     * @return An Object matching the string <code>key</code>.
     * @see ShellFolderManager#get(String)
     */
    public static Object get(String key) {
        return shellFolderManager.get(key);
    }

    /**
     * Does <code>dir</code> represent a "computer" such as a node on the network, or
     * "My Computer" on the desktop.
     */
    public static boolean isComputerNode(File dir) {
        return shellFolderManager.isComputerNode(dir);
    }

    /**
     * @return Whether this is a file system root directory
     */
    public static boolean isFileSystemRoot(File dir) {
        return shellFolderManager.isFileSystemRoot(dir);
    }

    /**
     * Canonicalizes files that don't have symbolic links in their path.
     * Normalizes files that do, preserving symbolic links from being resolved.
     */
    public static File getNormalizedFile(File f) throws IOException {
        File canonical = f.getCanonicalFile();
        if (f.equals(canonical)) {
            // path of f doesn't contain symbolic links
            return canonical;
        }

        // preserve symbolic links from being resolved
        return new File(f.toURI().normalize());
    }

    // Override File methods

    public static void sortFiles(List files) {
        shellFolderManager.sortFiles(files);
    }

    public boolean isAbsolute() {
        return (!isFileSystem() || super.isAbsolute());
    }

    public File getAbsoluteFile() {
        return (isFileSystem() ? super.getAbsoluteFile() : this);
    }

    public boolean canRead() {
        return (isFileSystem() ? super.canRead() : true);       // ((Fix?))
    }

    /**
     * Returns true if folder allows creation of children.
     * True for the "Desktop" folder, but false for the "My Computer"
     * folder.
     */
    public boolean canWrite() {
        return (isFileSystem() ? super.canWrite() : false);     // ((Fix?))
    }

    public boolean exists() {
        // Assume top-level drives exist, because state is uncertain for
        // removable drives.
        return (!isFileSystem() || isFileSystemRoot(this) || super.exists()) ;
    }

    public boolean isDirectory() {
        return (isFileSystem() ? super.isDirectory() : true);   // ((Fix?))
    }

    public boolean isFile() {
        return (isFileSystem() ? super.isFile() : !isDirectory());      // ((Fix?))
    }

    public long lastModified() {
        return (isFileSystem() ? super.lastModified() : 0L);    // ((Fix?))
    }

    public long length() {
        return (isFileSystem() ? super.length() : 0L);  // ((Fix?))
    }

    public boolean createNewFile() throws IOException {
        return (isFileSystem() ? super.createNewFile() : false);
    }

    public boolean delete() {
        return (isFileSystem() ? super.delete() : false);       // ((Fix?))
    }

    public void deleteOnExit() {
        if (isFileSystem()) {
            super.deleteOnExit();
        } else {
            // Do nothing       // ((Fix?))
        }
    }

    public boolean mkdir() {
        return (isFileSystem() ? super.mkdir() : false);
    }

    public boolean mkdirs() {
        return (isFileSystem() ? super.mkdirs() : false);
    }

    public boolean renameTo(File dest) {
        return (isFileSystem() ? super.renameTo(dest) : false); // ((Fix?))
    }

    public boolean setLastModified(long time) {
        return (isFileSystem() ? super.setLastModified(time) : false); // ((Fix?))
    }

    public boolean setReadOnly() {
        return (isFileSystem() ? super.setReadOnly() : false); // ((Fix?))
    }

    public String toString() {
        return (isFileSystem() ? super.toString() : getDisplayName());
    }

    public static ShellFolderColumnInfo[] getFolderColumns(File dir) {
        return shellFolderManager.getFolderColumns(dir);
    }

    public static Object getFolderColumnValue(File file, int column) {
        return shellFolderManager.getFolderColumnValue(file, column);
    }

    public ShellFolderColumnInfo[] getFolderColumns() {
        return null;
    }

    public Object getFolderColumnValue(int column) {
        return null;
    }
}
