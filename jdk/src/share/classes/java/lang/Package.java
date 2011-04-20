/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.io.InputStream;
import java.util.Enumeration;

import java.util.StringTokenizer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;
import java.security.AccessController;
import java.security.PrivilegedAction;

import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarException;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

import sun.net.www.ParseUtil;

import java.lang.annotation.Annotation;

/**
 * {@code Package} objects contain version information
 * about the implementation and specification of a Java package.
 * This versioning information is retrieved and made available
 * by the {@link ClassLoader} instance that
 * loaded the class(es).  Typically, it is stored in the manifest that is
 * distributed with the classes.
 *
 * <p>The set of classes that make up the package may implement a
 * particular specification and if so the specification title, version number,
 * and vendor strings identify that specification.
 * An application can ask if the package is
 * compatible with a particular version, see the {@link
 * #isCompatibleWith isCompatibleWith}
 * method for details.
 *
 * <p>Specification version numbers use a syntax that consists of nonnegative
 * decimal integers separated by periods ".", for example "2.0" or
 * "1.2.3.4.5.6.7".  This allows an extensible number to be used to represent
 * major, minor, micro, etc. versions.  The version specification is described
 * by the following formal grammar:
 * <blockquote>
 * <dl>
 * <dt><i>SpecificationVersion:
 * <dd>Digits RefinedVersion<sub>opt</sub></i>

 * <p><dt><i>RefinedVersion:</i>
 * <dd>{@code .} <i>Digits</i>
 * <dd>{@code .} <i>Digits RefinedVersion</i>
 *
 * <p><dt><i>Digits:
 * <dd>Digit
 * <dd>Digits</i>
 *
 * <p><dt><i>Digit:</i>
 * <dd>any character for which {@link Character#isDigit} returns {@code true},
 * e.g. 0, 1, 2, ...
 * </dl>
 * </blockquote>
 *
 * <p>The implementation title, version, and vendor strings identify an
 * implementation and are made available conveniently to enable accurate
 * reporting of the packages involved when a problem occurs. The contents
 * all three implementation strings are vendor specific. The
 * implementation version strings have no specified syntax and should
 * only be compared for equality with desired version identifiers.
 *
 * <p>Within each {@code ClassLoader} instance all classes from the same
 * java package have the same Package object.  The static methods allow a package
 * to be found by name or the set of all packages known to the current class
 * loader to be found.
 *
 * @see ClassLoader#definePackage
 */
public class Package implements java.lang.reflect.AnnotatedElement {
    /**
     * Return the name of this package.
     *
     * @return  The fully-qualified name of this package as defined in section 6.5.3 of
     *          <cite>The Java&trade; Language Specification</cite>,
     *          for example, {@code java.lang}
     */
    public String getName() {
        return pkgName;
    }


    /**
     * Return the title of the specification that this package implements.
     * @return the specification title, null is returned if it is not known.
     */
    public String getSpecificationTitle() {
        return specTitle;
    }

    /**
     * Returns the version number of the specification
     * that this package implements.
     * This version string must be a sequence of nonnegative decimal
     * integers separated by "."'s and may have leading zeros.
     * When version strings are compared the most significant
     * numbers are compared.
     * @return the specification version, null is returned if it is not known.
     */
    public String getSpecificationVersion() {
        return specVersion;
    }

    /**
     * Return the name of the organization, vendor,
     * or company that owns and maintains the specification
     * of the classes that implement this package.
     * @return the specification vendor, null is returned if it is not known.
     */
    public String getSpecificationVendor() {
        return specVendor;
    }

    /**
     * Return the title of this package.
     * @return the title of the implementation, null is returned if it is not known.
     */
    public String getImplementationTitle() {
        return implTitle;
    }

    /**
     * Return the version of this implementation. It consists of any string
     * assigned by the vendor of this implementation and does
     * not have any particular syntax specified or expected by the Java
     * runtime. It may be compared for equality with other
     * package version strings used for this implementation
     * by this vendor for this package.
     * @return the version of the implementation, null is returned if it is not known.
     */
    public String getImplementationVersion() {
        return implVersion;
    }

    /**
     * Returns the name of the organization,
     * vendor or company that provided this implementation.
     * @return the vendor that implemented this package..
     */
    public String getImplementationVendor() {
        return implVendor;
    }

    /**
     * Returns true if this package is sealed.
     *
     * @return true if the package is sealed, false otherwise
     */
    public boolean isSealed() {
        return sealBase != null;
    }

    /**
     * Returns true if this package is sealed with respect to the specified
     * code source url.
     *
     * @param url the code source url
     * @return true if this package is sealed with respect to url
     */
    public boolean isSealed(URL url) {
        return url.equals(sealBase);
    }

    /**
     * Compare this package's specification version with a
     * desired version. It returns true if
     * this packages specification version number is greater than or equal
     * to the desired version number. <p>
     *
     * Version numbers are compared by sequentially comparing corresponding
     * components of the desired and specification strings.
     * Each component is converted as a decimal integer and the values
     * compared.
     * If the specification value is greater than the desired
     * value true is returned. If the value is less false is returned.
     * If the values are equal the period is skipped and the next pair of
     * components is compared.
     *
     * @param desired the version string of the desired version.
     * @return true if this package's version number is greater
     *          than or equal to the desired version number
     *
     * @exception NumberFormatException if the desired or current version
     *          is not of the correct dotted form.
     */
    public boolean isCompatibleWith(String desired)
        throws NumberFormatException
    {
        if (specVersion == null || specVersion.length() < 1) {
            throw new NumberFormatException("Empty version string");
        }

        String [] sa = specVersion.split("\\.", -1);
        int [] si = new int[sa.length];
        for (int i = 0; i < sa.length; i++) {
            si[i] = Integer.parseInt(sa[i]);
            if (si[i] < 0)
                throw NumberFormatException.forInputString("" + si[i]);
        }

        String [] da = desired.split("\\.", -1);
        int [] di = new int[da.length];
        for (int i = 0; i < da.length; i++) {
            di[i] = Integer.parseInt(da[i]);
            if (di[i] < 0)
                throw NumberFormatException.forInputString("" + di[i]);
        }

        int len = Math.max(di.length, si.length);
        for (int i = 0; i < len; i++) {
            int d = (i < di.length ? di[i] : 0);
            int s = (i < si.length ? si[i] : 0);
            if (s < d)
                return false;
            if (s > d)
                return true;
        }
        return true;
    }

    /**
     * Find a package by name in the callers {@code ClassLoader} instance.
     * The callers {@code ClassLoader} instance is used to find the package
     * instance corresponding to the named class. If the callers
     * {@code ClassLoader} instance is null then the set of packages loaded
     * by the system {@code ClassLoader} instance is searched to find the
     * named package. <p>
     *
     * Packages have attributes for versions and specifications only if the class
     * loader created the package instance with the appropriate attributes. Typically,
     * those attributes are defined in the manifests that accompany the classes.
     *
     * @param name a package name, for example, java.lang.
     * @return the package of the requested name. It may be null if no package
     *          information is available from the archive or codebase.
     */
    public static Package getPackage(String name) {
        ClassLoader l = ClassLoader.getCallerClassLoader();
        if (l != null) {
            return l.getPackage(name);
        } else {
            return getSystemPackage(name);
        }
    }

    /**
     * Get all the packages currently known for the caller's {@code ClassLoader}
     * instance.  Those packages correspond to classes loaded via or accessible by
     * name to that {@code ClassLoader} instance.  If the caller's
     * {@code ClassLoader} instance is the bootstrap {@code ClassLoader}
     * instance, which may be represented by {@code null} in some implementations,
     * only packages corresponding to classes loaded by the bootstrap
     * {@code ClassLoader} instance will be returned.
     *
     * @return a new array of packages known to the callers {@code ClassLoader}
     * instance.  An zero length array is returned if none are known.
     */
    public static Package[] getPackages() {
        ClassLoader l = ClassLoader.getCallerClassLoader();
        if (l != null) {
            return l.getPackages();
        } else {
            return getSystemPackages();
        }
    }

    /**
     * Get the package for the specified class.
     * The class's class loader is used to find the package instance
     * corresponding to the specified class. If the class loader
     * is the bootstrap class loader, which may be represented by
     * {@code null} in some implementations, then the set of packages
     * loaded by the bootstrap class loader is searched to find the package.
     * <p>
     * Packages have attributes for versions and specifications only
     * if the class loader created the package
     * instance with the appropriate attributes. Typically those
     * attributes are defined in the manifests that accompany
     * the classes.
     *
     * @param class the class to get the package of.
     * @return the package of the class. It may be null if no package
     *          information is available from the archive or codebase.  */
    static Package getPackage(Class<?> c) {
        String name = c.getName();
        int i = name.lastIndexOf('.');
        if (i != -1) {
            name = name.substring(0, i);
            ClassLoader cl = c.getClassLoader();
            if (cl != null) {
                return cl.getPackage(name);
            } else {
                return getSystemPackage(name);
            }
        } else {
            return null;
        }
    }

    /**
     * Return the hash code computed from the package name.
     * @return the hash code computed from the package name.
     */
    public int hashCode(){
        return pkgName.hashCode();
    }

    /**
     * Returns the string representation of this Package.
     * Its value is the string "package " and the package name.
     * If the package title is defined it is appended.
     * If the package version is defined it is appended.
     * @return the string representation of the package.
     */
    public String toString() {
        String spec = specTitle;
        String ver =  specVersion;
        if (spec != null && spec.length() > 0)
            spec = ", " + spec;
        else
            spec = "";
        if (ver != null && ver.length() > 0)
            ver = ", version " + ver;
        else
            ver = "";
        return "package " + pkgName + spec + ver;
    }

    private Class<?> getPackageInfo() {
        if (packageInfo == null) {
            try {
                packageInfo = Class.forName(pkgName + ".package-info", false, loader);
            } catch (ClassNotFoundException ex) {
                // store a proxy for the package info that has no annotations
                class PackageInfoProxy {}
                packageInfo = PackageInfoProxy.class;
            }
        }
        return packageInfo;
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @since 1.5
     */
    public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
        return getPackageInfo().getAnnotation(annotationClass);
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     * @since 1.5
     */
    public boolean isAnnotationPresent(
        Class<? extends Annotation> annotationClass) {
        return getPackageInfo().isAnnotationPresent(annotationClass);
    }

    /**
     * @since 1.5
     */
    public Annotation[] getAnnotations() {
        return getPackageInfo().getAnnotations();
    }

    /**
     * @since 1.5
     */
    public Annotation[] getDeclaredAnnotations()  {
        return getPackageInfo().getDeclaredAnnotations();
    }

    /**
     * Construct a package instance with the specified version
     * information.
     * @param pkgName the name of the package
     * @param spectitle the title of the specification
     * @param specversion the version of the specification
     * @param specvendor the organization that maintains the specification
     * @param impltitle the title of the implementation
     * @param implversion the version of the implementation
     * @param implvendor the organization that maintains the implementation
     * @return a new package for containing the specified information.
     */
    Package(String name,
            String spectitle, String specversion, String specvendor,
            String impltitle, String implversion, String implvendor,
            URL sealbase, ClassLoader loader)
    {
        pkgName = name;
        implTitle = impltitle;
        implVersion = implversion;
        implVendor = implvendor;
        specTitle = spectitle;
        specVersion = specversion;
        specVendor = specvendor;
        sealBase = sealbase;
        this.loader = loader;
    }

    /*
     * Construct a package using the attributes from the specified manifest.
     *
     * @param name the package name
     * @param man the optional manifest for the package
     * @param url the optional code source url for the package
     */
    private Package(String name, Manifest man, URL url, ClassLoader loader) {
        String path = name.replace('.', '/').concat("/");
        String sealed = null;
        String specTitle= null;
        String specVersion= null;
        String specVendor= null;
        String implTitle= null;
        String implVersion= null;
        String implVendor= null;
        URL sealBase= null;
        Attributes attr = man.getAttributes(path);
        if (attr != null) {
            specTitle   = attr.getValue(Name.SPECIFICATION_TITLE);
            specVersion = attr.getValue(Name.SPECIFICATION_VERSION);
            specVendor  = attr.getValue(Name.SPECIFICATION_VENDOR);
            implTitle   = attr.getValue(Name.IMPLEMENTATION_TITLE);
            implVersion = attr.getValue(Name.IMPLEMENTATION_VERSION);
            implVendor  = attr.getValue(Name.IMPLEMENTATION_VENDOR);
            sealed      = attr.getValue(Name.SEALED);
        }
        attr = man.getMainAttributes();
        if (attr != null) {
            if (specTitle == null) {
                specTitle = attr.getValue(Name.SPECIFICATION_TITLE);
            }
            if (specVersion == null) {
                specVersion = attr.getValue(Name.SPECIFICATION_VERSION);
            }
            if (specVendor == null) {
                specVendor = attr.getValue(Name.SPECIFICATION_VENDOR);
            }
            if (implTitle == null) {
                implTitle = attr.getValue(Name.IMPLEMENTATION_TITLE);
            }
            if (implVersion == null) {
                implVersion = attr.getValue(Name.IMPLEMENTATION_VERSION);
            }
            if (implVendor == null) {
                implVendor = attr.getValue(Name.IMPLEMENTATION_VENDOR);
            }
            if (sealed == null) {
                sealed = attr.getValue(Name.SEALED);
            }
        }
        if ("true".equalsIgnoreCase(sealed)) {
            sealBase = url;
        }
        pkgName = name;
        this.specTitle = specTitle;
        this.specVersion = specVersion;
        this.specVendor = specVendor;
        this.implTitle = implTitle;
        this.implVersion = implVersion;
        this.implVendor = implVendor;
        this.sealBase = sealBase;
        this.loader = loader;
    }

    /*
     * Returns the loaded system package for the specified name.
     */
    static Package getSystemPackage(String name) {
        synchronized (pkgs) {
            Package pkg = pkgs.get(name);
            if (pkg == null) {
                name = name.replace('.', '/').concat("/");
                String fn = getSystemPackage0(name);
                if (fn != null) {
                    pkg = defineSystemPackage(name, fn);
                }
            }
            return pkg;
        }
    }

    /*
     * Return an array of loaded system packages.
     */
    static Package[] getSystemPackages() {
        // First, update the system package map with new package names
        String[] names = getSystemPackages0();
        synchronized (pkgs) {
            for (int i = 0; i < names.length; i++) {
                defineSystemPackage(names[i], getSystemPackage0(names[i]));
            }
            return pkgs.values().toArray(new Package[pkgs.size()]);
        }
    }

    private static Package defineSystemPackage(final String iname,
                                               final String fn)
    {
        return AccessController.doPrivileged(new PrivilegedAction<Package>() {
            public Package run() {
                String name = iname;
                // Get the cached code source url for the file name
                URL url = urls.get(fn);
                if (url == null) {
                    // URL not found, so create one
                    File file = new File(fn);
                    try {
                        url = ParseUtil.fileToEncodedURL(file);
                    } catch (MalformedURLException e) {
                    }
                    if (url != null) {
                        urls.put(fn, url);
                        // If loading a JAR file, then also cache the manifest
                        if (file.isFile()) {
                            mans.put(fn, loadManifest(fn));
                        }
                    }
                }
                // Convert to "."-separated package name
                name = name.substring(0, name.length() - 1).replace('/', '.');
                Package pkg;
                Manifest man = mans.get(fn);
                if (man != null) {
                    pkg = new Package(name, man, url, null);
                } else {
                    pkg = new Package(name, null, null, null,
                                      null, null, null, null, null);
                }
                pkgs.put(name, pkg);
                return pkg;
            }
        });
    }

    /*
     * Returns the Manifest for the specified JAR file name.
     */
    private static Manifest loadManifest(String fn) {
        try (FileInputStream fis = new FileInputStream(fn);
             JarInputStream jis = new JarInputStream(fis, false))
        {
            return jis.getManifest();
        } catch (IOException e) {
            return null;
        }
    }

    // The map of loaded system packages
    private static Map<String, Package> pkgs = new HashMap<>(31);

    // Maps each directory or zip file name to its corresponding url
    private static Map<String, URL> urls = new HashMap<>(10);

    // Maps each code source url for a jar file to its manifest
    private static Map<String, Manifest> mans = new HashMap<>(10);

    private static native String getSystemPackage0(String name);
    private static native String[] getSystemPackages0();

    /*
     * Private storage for the package name and attributes.
     */
    private final String pkgName;
    private final String specTitle;
    private final String specVersion;
    private final String specVendor;
    private final String implTitle;
    private final String implVersion;
    private final String implVendor;
    private final URL sealBase;
    private transient final ClassLoader loader;
    private transient Class packageInfo;
}
