/*
 * Copyright (c) 2000, 2011, Oracle and/or its affiliates. All rights reserved.
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

package java.security.cert;

import java.security.AccessController;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;
import sun.security.util.Debug;

import sun.security.jca.*;
import sun.security.jca.GetInstance.Instance;

/**
 * A class for building certification paths (also known as certificate chains).
 * <p>
 * This class uses a provider-based architecture.
 * To create a <code>CertPathBuilder</code>, call
 * one of the static <code>getInstance</code> methods, passing in the
 * algorithm name of the <code>CertPathBuilder</code> desired and optionally
 * the name of the provider desired.
 * <p>
 * Once a <code>CertPathBuilder</code> object has been created, certification
 * paths can be constructed by calling the {@link #build build} method and
 * passing it an algorithm-specific set of parameters. If successful, the
 * result (including the <code>CertPath</code> that was built) is returned
 * in an object that implements the <code>CertPathBuilderResult</code>
 * interface.
 *
 * <p> Every implementation of the Java platform is required to support the
 * following standard <code>CertPathBuilder</code> algorithm:
 * <ul>
 * <li><tt>PKIX</tt></li>
 * </ul>
 * This algorithm is described in the <a href=
 * "{@docRoot}/../technotes/guides/security/StandardNames.html#CertPathBuilder">
 * CertPathBuilder section</a> of the
 * Java Cryptography Architecture Standard Algorithm Name Documentation.
 * Consult the release documentation for your implementation to see if any
 * other algorithms are supported.
 *
 * <p>
 * <b>Concurrent Access</b>
 * <p>
 * The static methods of this class are guaranteed to be thread-safe.
 * Multiple threads may concurrently invoke the static methods defined in
 * this class with no ill effects.
 * <p>
 * However, this is not true for the non-static methods defined by this class.
 * Unless otherwise documented by a specific provider, threads that need to
 * access a single <code>CertPathBuilder</code> instance concurrently should
 * synchronize amongst themselves and provide the necessary locking. Multiple
 * threads each manipulating a different <code>CertPathBuilder</code> instance
 * need not synchronize.
 *
 * @see CertPath
 *
 * @since       1.4
 * @author      Sean Mullan
 * @author      Yassir Elley
 */
public class CertPathBuilder {

    /*
     * Constant to lookup in the Security properties file to determine
     * the default certpathbuilder type. In the Security properties file,
     * the default certpathbuilder type is given as:
     * <pre>
     * certpathbuilder.type=PKIX
     * </pre>
     */
    private static final String CPB_TYPE = "certpathbuilder.type";
    private static final Debug debug = Debug.getInstance("certpath");
    private CertPathBuilderSpi builderSpi;
    private Provider provider;
    private String algorithm;

    /**
     * Creates a <code>CertPathBuilder</code> object of the given algorithm,
     * and encapsulates the given provider implementation (SPI object) in it.
     *
     * @param builderSpi the provider implementation
     * @param provider the provider
     * @param algorithm the algorithm name
     */
    protected CertPathBuilder(CertPathBuilderSpi builderSpi, Provider provider,
        String algorithm)
    {
        this.builderSpi = builderSpi;
        this.provider = provider;
        this.algorithm = algorithm;
    }

    /**
     * Returns a <code>CertPathBuilder</code> object that implements the
     * specified algorithm.
     *
     * <p> This method traverses the list of registered security Providers,
     * starting with the most preferred Provider.
     * A new CertPathBuilder object encapsulating the
     * CertPathBuilderSpi implementation from the first
     * Provider that supports the specified algorithm is returned.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param algorithm the name of the requested <code>CertPathBuilder</code>
     *  algorithm.  See the CertPathBuilder section in the <a href=
     *  "{@docRoot}/../technotes/guides/security/StandardNames.html#CertPathBuilder">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard algorithm names.
     *
     * @return a <code>CertPathBuilder</code> object that implements the
     *          specified algorithm.
     *
     * @throws NoSuchAlgorithmException if no Provider supports a
     *          CertPathBuilderSpi implementation for the
     *          specified algorithm.
     *
     * @see java.security.Provider
     */
    public static CertPathBuilder getInstance(String algorithm)
            throws NoSuchAlgorithmException {
        Instance instance = GetInstance.getInstance("CertPathBuilder",
            CertPathBuilderSpi.class, algorithm);
        return new CertPathBuilder((CertPathBuilderSpi)instance.impl,
            instance.provider, algorithm);
    }

    /**
     * Returns a <code>CertPathBuilder</code> object that implements the
     * specified algorithm.
     *
     * <p> A new CertPathBuilder object encapsulating the
     * CertPathBuilderSpi implementation from the specified provider
     * is returned.  The specified provider must be registered
     * in the security provider list.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param algorithm the name of the requested <code>CertPathBuilder</code>
     *  algorithm.  See the CertPathBuilder section in the <a href=
     *  "{@docRoot}/../technotes/guides/security/StandardNames.html#CertPathBuilder">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard algorithm names.
     *
     * @param provider the name of the provider.
     *
     * @return a <code>CertPathBuilder</code> object that implements the
     *          specified algorithm.
     *
     * @throws NoSuchAlgorithmException if a CertPathBuilderSpi
     *          implementation for the specified algorithm is not
     *          available from the specified provider.
     *
     * @throws NoSuchProviderException if the specified provider is not
     *          registered in the security provider list.
     *
     * @exception IllegalArgumentException if the <code>provider</code> is
     *          null or empty.
     *
     * @see java.security.Provider
     */
    public static CertPathBuilder getInstance(String algorithm, String provider)
           throws NoSuchAlgorithmException, NoSuchProviderException {
        Instance instance = GetInstance.getInstance("CertPathBuilder",
            CertPathBuilderSpi.class, algorithm, provider);
        return new CertPathBuilder((CertPathBuilderSpi)instance.impl,
            instance.provider, algorithm);
    }

    /**
     * Returns a <code>CertPathBuilder</code> object that implements the
     * specified algorithm.
     *
     * <p> A new CertPathBuilder object encapsulating the
     * CertPathBuilderSpi implementation from the specified Provider
     * object is returned.  Note that the specified Provider object
     * does not have to be registered in the provider list.
     *
     * @param algorithm the name of the requested <code>CertPathBuilder</code>
     *  algorithm.  See the CertPathBuilder section in the <a href=
     *  "{@docRoot}/../technotes/guides/security/StandardNames.html#CertPathBuilder">
     * Java Cryptography Architecture Standard Algorithm Name Documentation</a>
     * for information about standard algorithm names.
     *
     * @param provider the provider.
     *
     * @return a <code>CertPathBuilder</code> object that implements the
     *          specified algorithm.
     *
     * @exception NoSuchAlgorithmException if a CertPathBuilderSpi
     *          implementation for the specified algorithm is not available
     *          from the specified Provider object.
     *
     * @exception IllegalArgumentException if the <code>provider</code> is
     *          null.
     *
     * @see java.security.Provider
     */
    public static CertPathBuilder getInstance(String algorithm,
            Provider provider) throws NoSuchAlgorithmException {
        Instance instance = GetInstance.getInstance("CertPathBuilder",
            CertPathBuilderSpi.class, algorithm, provider);
        return new CertPathBuilder((CertPathBuilderSpi)instance.impl,
            instance.provider, algorithm);
    }

    /**
     * Returns the provider of this <code>CertPathBuilder</code>.
     *
     * @return the provider of this <code>CertPathBuilder</code>
     */
    public final Provider getProvider() {
        return this.provider;
    }

    /**
     * Returns the name of the algorithm of this <code>CertPathBuilder</code>.
     *
     * @return the name of the algorithm of this <code>CertPathBuilder</code>
     */
    public final String getAlgorithm() {
        return this.algorithm;
    }

    /**
     * Attempts to build a certification path using the specified algorithm
     * parameter set.
     *
     * @param params the algorithm parameters
     * @return the result of the build algorithm
     * @throws CertPathBuilderException if the builder is unable to construct
     *  a certification path that satisfies the specified parameters
     * @throws InvalidAlgorithmParameterException if the specified parameters
     * are inappropriate for this <code>CertPathBuilder</code>
     */
    public final CertPathBuilderResult build(CertPathParameters params)
        throws CertPathBuilderException, InvalidAlgorithmParameterException
    {
        return builderSpi.engineBuild(params);
    }

    /**
     * Returns the default <code>CertPathBuilder</code> type as specified in
     * the Java security properties file, or the string &quot;PKIX&quot;
     * if no such property exists. The Java security properties file is
     * located in the file named &lt;JAVA_HOME&gt;/lib/security/java.security.
     * &lt;JAVA_HOME&gt; refers to the value of the java.home system property,
     * and specifies the directory where the JRE is installed.
     *
     * <p>The default <code>CertPathBuilder</code> type can be used by
     * applications that do not want to use a hard-coded type when calling one
     * of the <code>getInstance</code> methods, and want to provide a default
     * type in case a user does not specify its own.
     *
     * <p>The default <code>CertPathBuilder</code> type can be changed by
     * setting the value of the "certpathbuilder.type" security property
     * (in the Java security properties file) to the desired type.
     *
     * @return the default <code>CertPathBuilder</code> type as specified
     * in the Java security properties file, or the string &quot;PKIX&quot;
     * if no such property exists.
     */
    public final static String getDefaultType() {
        String cpbtype;
        cpbtype = AccessController.doPrivileged(new PrivilegedAction<String>() {
            public String run() {
                return Security.getProperty(CPB_TYPE);
            }
        });
        if (cpbtype == null) {
            cpbtype = "PKIX";
        }
        return cpbtype;
    }
}
