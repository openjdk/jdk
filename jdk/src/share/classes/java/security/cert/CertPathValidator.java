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
 * A class for validating certification paths (also known as certificate
 * chains).
 * <p>
 * This class uses a provider-based architecture.
 * To create a <code>CertPathValidator</code>,
 * call one of the static <code>getInstance</code> methods, passing in the
 * algorithm name of the <code>CertPathValidator</code> desired and
 * optionally the name of the provider desired.
 * <p>
 * Once a <code>CertPathValidator</code> object has been created, it can
 * be used to validate certification paths by calling the {@link #validate
 * validate} method and passing it the <code>CertPath</code> to be validated
 * and an algorithm-specific set of parameters. If successful, the result is
 * returned in an object that implements the
 * <code>CertPathValidatorResult</code> interface.
 * <p>
 * <b>Concurrent Access</b>
 * <p>
 * The static methods of this class are guaranteed to be thread-safe.
 * Multiple threads may concurrently invoke the static methods defined in
 * this class with no ill effects.
 * <p>
 * However, this is not true for the non-static methods defined by this class.
 * Unless otherwise documented by a specific provider, threads that need to
 * access a single <code>CertPathValidator</code> instance concurrently should
 * synchronize amongst themselves and provide the necessary locking. Multiple
 * threads each manipulating a different <code>CertPathValidator</code>
 * instance need not synchronize.
 *
 * @see CertPath
 *
 * @since       1.4
 * @author      Yassir Elley
 */
public class CertPathValidator {

    /*
     * Constant to lookup in the Security properties file to determine
     * the default certpathvalidator type. In the Security properties file,
     * the default certpathvalidator type is given as:
     * <pre>
     * certpathvalidator.type=PKIX
     * </pre>
     */
    private static final String CPV_TYPE = "certpathvalidator.type";
    private static final Debug debug = Debug.getInstance("certpath");
    private CertPathValidatorSpi validatorSpi;
    private Provider provider;
    private String algorithm;

    /**
     * Creates a <code>CertPathValidator</code> object of the given algorithm,
     * and encapsulates the given provider implementation (SPI object) in it.
     *
     * @param validatorSpi the provider implementation
     * @param provider the provider
     * @param algorithm the algorithm name
     */
    protected CertPathValidator(CertPathValidatorSpi validatorSpi,
        Provider provider, String algorithm)
    {
        this.validatorSpi = validatorSpi;
        this.provider = provider;
        this.algorithm = algorithm;
    }

    /**
     * Returns a <code>CertPathValidator</code> object that implements the
     * specified algorithm.
     *
     * <p> This method traverses the list of registered security Providers,
     * starting with the most preferred Provider.
     * A new CertPathValidator object encapsulating the
     * CertPathValidatorSpi implementation from the first
     * Provider that supports the specified algorithm is returned.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param algorithm the name of the requested <code>CertPathValidator</code>
     *  algorithm.  See Appendix A in the <a href=
     *  "../../../../technotes/guides/security/certpath/CertPathProgGuide.html#AppA">
     *  Java Certification Path API Programmer's Guide </a>
     *  for information about standard algorithm names.
     *
     * @return a <code>CertPathValidator</code> object that implements the
     *          specified algorithm.
     *
     * @exception NoSuchAlgorithmException if no Provider supports a
     *          CertPathValidatorSpi implementation for the
     *          specified algorithm.
     *
     * @see java.security.Provider
     */
    public static CertPathValidator getInstance(String algorithm)
            throws NoSuchAlgorithmException {
        Instance instance = GetInstance.getInstance("CertPathValidator",
            CertPathValidatorSpi.class, algorithm);
        return new CertPathValidator((CertPathValidatorSpi)instance.impl,
            instance.provider, algorithm);
    }

    /**
     * Returns a <code>CertPathValidator</code> object that implements the
     * specified algorithm.
     *
     * <p> A new CertPathValidator object encapsulating the
     * CertPathValidatorSpi implementation from the specified provider
     * is returned.  The specified provider must be registered
     * in the security provider list.
     *
     * <p> Note that the list of registered providers may be retrieved via
     * the {@link Security#getProviders() Security.getProviders()} method.
     *
     * @param algorithm the name of the requested <code>CertPathValidator</code>
     *  algorithm.  See Appendix A in the <a href=
     *  "../../../../technotes/guides/security/certpath/CertPathProgGuide.html#AppA">
     *  Java Certification Path API Programmer's Guide </a>
     *  for information about standard algorithm names.
     *
     * @param provider the name of the provider.
     *
     * @return a <code>CertPathValidator</code> object that implements the
     *          specified algorithm.
     *
     * @exception NoSuchAlgorithmException if a CertPathValidatorSpi
     *          implementation for the specified algorithm is not
     *          available from the specified provider.
     *
     * @exception NoSuchProviderException if the specified provider is not
     *          registered in the security provider list.
     *
     * @exception IllegalArgumentException if the <code>provider</code> is
     *          null or empty.
     *
     * @see java.security.Provider
     */
    public static CertPathValidator getInstance(String algorithm,
            String provider) throws NoSuchAlgorithmException,
            NoSuchProviderException {
        Instance instance = GetInstance.getInstance("CertPathValidator",
            CertPathValidatorSpi.class, algorithm, provider);
        return new CertPathValidator((CertPathValidatorSpi)instance.impl,
            instance.provider, algorithm);
    }

    /**
     * Returns a <code>CertPathValidator</code> object that implements the
     * specified algorithm.
     *
     * <p> A new CertPathValidator object encapsulating the
     * CertPathValidatorSpi implementation from the specified Provider
     * object is returned.  Note that the specified Provider object
     * does not have to be registered in the provider list.
     *
     * @param algorithm the name of the requested
     *  <code>CertPathValidator</code> algorithm.
     *  See Appendix A in the <a href=
     *  "../../../../technotes/guides/security/certpath/CertPathProgGuide.html#AppA">
     *  Java Certification Path API Programmer's Guide </a>
     *  for information about standard algorithm names.
     *
     * @param provider the provider.
     *
     * @return a <code>CertPathValidator</code> object that implements the
     *          specified algorithm.
     *
     * @exception NoSuchAlgorithmException if a CertPathValidatorSpi
     *          implementation for the specified algorithm is not available
     *          from the specified Provider object.
     *
     * @exception IllegalArgumentException if the <code>provider</code> is
     *          null.
     *
     * @see java.security.Provider
     */
    public static CertPathValidator getInstance(String algorithm,
            Provider provider) throws NoSuchAlgorithmException {
        Instance instance = GetInstance.getInstance("CertPathValidator",
            CertPathValidatorSpi.class, algorithm, provider);
        return new CertPathValidator((CertPathValidatorSpi)instance.impl,
            instance.provider, algorithm);
    }

    /**
     * Returns the <code>Provider</code> of this
     * <code>CertPathValidator</code>.
     *
     * @return the <code>Provider</code> of this <code>CertPathValidator</code>
     */
    public final Provider getProvider() {
        return this.provider;
    }

    /**
     * Returns the algorithm name of this <code>CertPathValidator</code>.
     *
     * @return the algorithm name of this <code>CertPathValidator</code>
     */
    public final String getAlgorithm() {
        return this.algorithm;
    }

    /**
     * Validates the specified certification path using the specified
     * algorithm parameter set.
     * <p>
     * The <code>CertPath</code> specified must be of a type that is
     * supported by the validation algorithm, otherwise an
     * <code>InvalidAlgorithmParameterException</code> will be thrown. For
     * example, a <code>CertPathValidator</code> that implements the PKIX
     * algorithm validates <code>CertPath</code> objects of type X.509.
     *
     * @param certPath the <code>CertPath</code> to be validated
     * @param params the algorithm parameters
     * @return the result of the validation algorithm
     * @exception CertPathValidatorException if the <code>CertPath</code>
     * does not validate
     * @exception InvalidAlgorithmParameterException if the specified
     * parameters or the type of the specified <code>CertPath</code> are
     * inappropriate for this <code>CertPathValidator</code>
     */
    public final CertPathValidatorResult validate(CertPath certPath,
        CertPathParameters params)
        throws CertPathValidatorException, InvalidAlgorithmParameterException
    {
        return validatorSpi.engineValidate(certPath, params);
    }

    /**
     * Returns the default <code>CertPathValidator</code> type as specified in
     * the Java security properties file, or the string &quot;PKIX&quot;
     * if no such property exists. The Java security properties file is
     * located in the file named &lt;JAVA_HOME&gt;/lib/security/java.security.
     * &lt;JAVA_HOME&gt; refers to the value of the java.home system property,
     * and specifies the directory where the JRE is installed.
     *
     * <p>The default <code>CertPathValidator</code> type can be used by
     * applications that do not want to use a hard-coded type when calling one
     * of the <code>getInstance</code> methods, and want to provide a default
     * type in case a user does not specify its own.
     *
     * <p>The default <code>CertPathValidator</code> type can be changed by
     * setting the value of the "certpathvalidator.type" security property
     * (in the Java security properties file) to the desired type.
     *
     * @return the default <code>CertPathValidator</code> type as specified
     * in the Java security properties file, or the string &quot;PKIX&quot;
     * if no such property exists.
     */
    public final static String getDefaultType() {
        String cpvtype;
        cpvtype = AccessController.doPrivileged(new PrivilegedAction<String>() {
            public String run() {
                return Security.getProperty(CPV_TYPE);
            }
        });
        if (cpvtype == null) {
            cpvtype = "PKIX";
        }
        return cpvtype;
    }
}
