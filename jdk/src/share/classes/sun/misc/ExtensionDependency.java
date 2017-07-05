/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.net.URL;
import java.net.MalformedURLException;
import sun.net.www.ParseUtil;

/**
 * <p>
 * This class checks dependent extensions a particular jar file may have
 * declared through its manifest attributes.
 * </p>
 * Jar file declared dependent extensions through the extension-list
 * attribute. The extension-list contains a list of keys used to
 * fetch the other attributes describing the required extension.
 * If key is the extension key declared in the extension-list
 * attribute, the following describing attribute can be found in
 * the manifest :
 * key-Extension-Name:  (Specification package name)
 * key-Specification-Version: (Specification-Version)
 * key-Implementation-Version: (Implementation-Version)
 * key-Implementation-Vendor-Id: (Imlementation-Vendor-Id)
 * key-Implementation-Version: (Implementation version)
 * key-Implementation-URL: (URL to download the requested extension)
 * <p>
 * This class also maintain versioning consistency of installed
 * extensions dependencies declared in jar file manifest.
 * </p>
 * @author  Jerome Dochez
 */
public class ExtensionDependency {

    /* Callbak interfaces to delegate installation of missing extensions */
    private static Vector providers;

    /**
     * <p>
     * Register an ExtensionInstallationProvider. The provider is responsible
     * for handling the installation (upgrade) of any missing extensions.
     * </p>
     * @param eip ExtensionInstallationProvider implementation
     */
    public synchronized static void addExtensionInstallationProvider
        (ExtensionInstallationProvider eip)
    {
        if (providers == null) {
            providers = new Vector();
        }
        providers.add(eip);
    }

    /**
     * <p>
     * Unregister a previously installed installation provider
     * </p>
     */
    public synchronized  static void removeExtensionInstallationProvider
        (ExtensionInstallationProvider eip)
    {
        providers.remove(eip);
    }

    /**
     * <p>
     * Checks the dependencies of the jar file on installed extension.
     * </p>
     * @param jarFile containing the attriutes declaring the dependencies
     */
    public static boolean checkExtensionsDependencies(JarFile jar)
    {
        if (providers == null) {
            // no need to bother, nobody is registered to install missing
            // extensions
            return true;
        }

        try {
            ExtensionDependency extDep = new ExtensionDependency();
            return extDep.checkExtensions(jar);
        } catch (ExtensionInstallationException e) {
            debug(e.getMessage());
        }
        return false;
    }

    /*
     * Check for all declared required extensions in the jar file
     * manifest.
     */
    protected boolean checkExtensions(JarFile jar)
        throws ExtensionInstallationException
    {
        Manifest man;
        try {
            man = jar.getManifest();
        } catch (IOException e) {
            return false;
        }

        if (man == null) {
            // The applet does not define a manifest file, so
            // we just assume all dependencies are satisfied.
            return true;
        }

        boolean result = true;
        Attributes attr = man.getMainAttributes();
        if (attr != null) {
            // Let's get the list of declared dependencies
            String value = attr.getValue(Name.EXTENSION_LIST);
            if (value != null) {
                StringTokenizer st = new StringTokenizer(value);
                // Iterate over all declared dependencies
                while (st.hasMoreTokens()) {
                    String extensionName = st.nextToken();
                    debug("The file " + jar.getName() +
                          " appears to depend on " + extensionName);
                    // Sanity Check
                    String extName = extensionName + "-" +
                        Name.EXTENSION_NAME.toString();
                    if (attr.getValue(extName) == null) {
                        debug("The jar file " + jar.getName() +
                              " appers to depend on "
                              + extensionName + " but does not define the " +
                              extName + " attribute in its manifest ");

                    } else {
                        if (!checkExtension(extensionName, attr)) {
                            debug("Failed installing " + extensionName);
                            result = false;
                        }
                    }
                }
            } else {
                debug("No dependencies for " + jar.getName());
            }
        }
        return result;
    }


    /*
     * <p>
     * Check that a particular dependency on an extension is satisfied.
     * </p>
     * @param extensionName is the key used for the attributes in the manifest
     * @param attr is the attributes of the manifest file
     *
     * @return true if the dependency is satisfied by the installed extensions
     */
    protected synchronized boolean checkExtension(final String extensionName,
                                     final Attributes attr)
        throws ExtensionInstallationException
    {
        debug("Checking extension " + extensionName);
        if (checkExtensionAgainstInstalled(extensionName, attr))
            return true;

        debug("Extension not currently installed ");
        ExtensionInfo reqInfo = new ExtensionInfo(extensionName, attr);
        return installExtension(reqInfo, null);
    }

    /*
     * <p>
     * Check if a particular extension is part of the currently installed
     * extensions.
     * </p>
     * @param extensionName is the key for the attributes in the manifest
     * @param attr is the attributes of the manifest
     *
     * @return true if the requested extension is already installed
     */
    boolean checkExtensionAgainstInstalled(String extensionName,
                                           Attributes attr)
        throws ExtensionInstallationException
    {
        File fExtension = checkExtensionExists(extensionName);

        if (fExtension != null) {
        // Extension already installed, just check against this one
            try {
                if (checkExtensionAgainst(extensionName, attr, fExtension))
                    return true;
            } catch (FileNotFoundException e) {
                debugException(e);
            } catch (IOException e) {
                debugException(e);
            }
            return false;

        } else {
        // Not sure if extension is already installed, so check all the
        // installed extension jar files to see if we get a match

            File[] installedExts;

            try {
            // Get the list of installed extension jar files so we can
            // compare the installed versus the requested extension
                installedExts = getInstalledExtensions();
            } catch(IOException e) {
                debugException(e);
                return false;
            }

            for (int i=0;i<installedExts.length;i++) {
                try {
                    if (checkExtensionAgainst(extensionName, attr, installedExts[i]))
                        return true;
                } catch (FileNotFoundException e) {
                    debugException(e);
                } catch (IOException e) {
                    debugException(e);
                    // let's continue with the next installed extension
                }
            }
        }
        return false;
    }

    /*
     * <p>
     * Check if the requested extension described by the attributes
     * in the manifest under the key extensionName is compatible with
     * the jar file.
     * </p>
     *
     * @param extensionName key in the attibute list
     * @param attr manifest file attributes
     * @param file installed extension jar file to compare the requested
     * extension against.
     */
    protected boolean checkExtensionAgainst(String extensionName,
                                            Attributes attr,
                                            final File file)
        throws IOException,
               FileNotFoundException,
               ExtensionInstallationException
    {

        debug("Checking extension " + extensionName +
              " against " + file.getName());

        // Load the jar file ...
        Manifest man;
        try {
            man = AccessController.doPrivileged(
                new PrivilegedExceptionAction<Manifest>() {
                    public Manifest run()
                            throws IOException, FileNotFoundException {
                         if (!file.exists())
                             throw new FileNotFoundException(file.getName());
                         JarFile jarFile =  new JarFile(file);
                         return jarFile.getManifest();
                     }
                 });
        } catch(PrivilegedActionException e) {
            if (e.getException() instanceof FileNotFoundException)
                throw (FileNotFoundException) e.getException();
            throw (IOException) e.getException();
        }

        // Construct the extension information object
        ExtensionInfo reqInfo = new ExtensionInfo(extensionName, attr);
        debug("Requested Extension : " + reqInfo);

        int isCompatible = ExtensionInfo.INCOMPATIBLE;
        ExtensionInfo instInfo = null;

        if (man != null) {
            Attributes instAttr = man.getMainAttributes();
            if (instAttr != null) {
                instInfo = new ExtensionInfo(null, instAttr);
                debug("Extension Installed " + instInfo);
                isCompatible = instInfo.isCompatibleWith(reqInfo);
                switch(isCompatible) {
                case ExtensionInfo.COMPATIBLE:
                    debug("Extensions are compatible");
                    return true;

                case ExtensionInfo.INCOMPATIBLE:
                    debug("Extensions are incompatible");
                    return false;

                default:
                    // everything else
                    debug("Extensions require an upgrade or vendor switch");
                    return installExtension(reqInfo, instInfo);

                }
            }
        }
        return false;
    }

    /*
     * <p>
     * An required extension is missing, if an ExtensionInstallationProvider is
     * registered, delegate the installation of that particular extension to it.
     * </p>
     *
     * @param reqInfo Missing extension information
     * @param instInfo Older installed version information
     *
     * @return true if the installation is successful
     */
    protected boolean installExtension(ExtensionInfo reqInfo,
                                       ExtensionInfo instInfo)
        throws ExtensionInstallationException
    {

        Vector currentProviders;
        synchronized(providers) {
            currentProviders = (Vector) providers.clone();
        }
        for (Enumeration e=currentProviders.elements();e.hasMoreElements();) {
            ExtensionInstallationProvider eip =
                (ExtensionInstallationProvider) e.nextElement();

            if (eip!=null) {
                // delegate the installation to the provider
                if (eip.installExtension(reqInfo, instInfo)) {
                    debug(reqInfo.name + " installation successful");
                    Launcher.ExtClassLoader cl = (Launcher.ExtClassLoader)
                        Launcher.getLauncher().getClassLoader().getParent();
                    addNewExtensionsToClassLoader(cl);
                    return true;
                }
            }
        }
        // We have tried all of our providers, noone could install this
        // extension, we just return failure at this point
        debug(reqInfo.name + " installation failed");
        return false;
    }

    /**
     * <p>
     * Checks if the extension, that is specified in the extension-list in
     * the applet jar manifest, is already installed (i.e. exists in the
     * extension directory).
     * </p>
     *
     * @param extensionName extension name in the extension-list
     *
     * @return the extension if it exists in the extension directory
     */
    private File checkExtensionExists(String extensionName) {
        // Function added to fix bug 4504166
        final String extName = extensionName;
        final String[] fileExt = {".jar", ".zip"};

        return AccessController.doPrivileged(
            new PrivilegedAction<File>() {
                public File run() {
                    try {
                        File fExtension;
                        File[] dirs = getExtDirs();

                        // Search the extension directories for the extension that is specified
                        // in the attribute extension-list in the applet jar manifest
                        for (int i=0;i<dirs.length;i++) {
                            for (int j=0;j<fileExt.length;j++) {
                                if (extName.toLowerCase().endsWith(fileExt[j])) {
                                    fExtension = new File(dirs[i], extName);
                                } else {
                                    fExtension = new File(dirs[i], extName+fileExt[j]);
                                }
                                debug("checkExtensionExists:fileName " + fExtension.getName());
                                if (fExtension.exists()) {
                                    return fExtension;
                                }
                            }
                        }
                        return null;

                    } catch(Exception e) {
                         debugException(e);
                         return null;
                    }
                }
            });
    }

    /**
     * <p>
     * @return the java.ext.dirs property as a list of directory
     * </p>
     */
    private static File[] getExtDirs() {
        String s = java.security.AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction("java.ext.dirs"));

        File[] dirs;
        if (s != null) {
            StringTokenizer st =
                new StringTokenizer(s, File.pathSeparator);
            int count = st.countTokens();
            debug("getExtDirs count " + count);
            dirs = new File[count];
            for (int i = 0; i < count; i++) {
                dirs[i] = new File(st.nextToken());
                debug("getExtDirs dirs["+i+"] "+ dirs[i]);
            }
        } else {
            dirs = new File[0];
            debug("getExtDirs dirs " + dirs);
        }
        debug("getExtDirs dirs.length " + dirs.length);
        return dirs;
    }

    /*
     * <p>
     * Scan the directories and return all files installed in those
     * </p>
     * @param dirs list of directories to scan
     *
     * @return the list of files installed in all the directories
     */
    private static File[] getExtFiles(File[] dirs) throws IOException {
        Vector<File> urls = new Vector<File>();
        for (int i = 0; i < dirs.length; i++) {
            String[] files = dirs[i].list(new JarFilter());
            if (files != null) {
                debug("getExtFiles files.length " + files.length);
                for (int j = 0; j < files.length; j++) {
                    File f = new File(dirs[i], files[j]);
                    urls.add(f);
                    debug("getExtFiles f["+j+"] "+ f);
                }
            }
        }
        File[] ua = new File[urls.size()];
        urls.copyInto(ua);
        debug("getExtFiles ua.length " + ua.length);
        return ua;
    }

    /*
     * <p>
     * @return the list of installed extensions jar files
     * </p>
     */
    private File[] getInstalledExtensions() throws IOException {
        return AccessController.doPrivileged(
            new PrivilegedAction<File[]>() {
                public File[] run() {
                     try {
                         return getExtFiles(getExtDirs());
                     } catch(IOException e) {
                         debug("Cannot get list of installed extensions");
                         debugException(e);
                        return new File[0];
                     }
                 }
            });
    }

    /*
     * <p>
     * Add the newly installed jar file to the extension class loader.
     * </p>
     *
     * @param cl the current installed extension class loader
     *
     * @return true if successful
     */
    private Boolean addNewExtensionsToClassLoader(Launcher.ExtClassLoader cl) {
        try {
            File[] installedExts = getInstalledExtensions();
            for (int i=0;i<installedExts.length;i++) {
                final File instFile = installedExts[i];
                URL instURL = AccessController.doPrivileged(
                    new PrivilegedAction<URL>() {
                        public URL run() {
                            try {
                                return ParseUtil.fileToEncodedURL(instFile);
                            } catch (MalformedURLException e) {
                                debugException(e);
                                return null;
                            }
                        }
                    });
                if (instURL != null) {
                    URL[] urls = cl.getURLs();
                    boolean found=false;
                    for (int j = 0; j<urls.length; j++) {
                        debug("URL["+j+"] is " + urls[j] + " looking for "+
                                           instURL);
                        if (urls[j].toString().compareToIgnoreCase(
                                    instURL.toString())==0) {
                            found=true;
                            debug("Found !");
                        }
                    }
                    if (!found) {
                        debug("Not Found ! adding to the classloader " +
                              instURL);
                        cl.addExtURL(instURL);
                    }
                }
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            // let's continue with the next installed extension
        }
        return Boolean.TRUE;
    }

    // True to display all debug and trace messages
    static final boolean DEBUG = false;

    private static void debug(String s) {
        if (DEBUG) {
            System.err.println(s);
        }
    }

    private void debugException(Throwable e) {
        if (DEBUG) {
            e.printStackTrace();
        }
    }

}
