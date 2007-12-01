/*
 * Copyright 2002-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.validator;

import java.security.cert.*;

/**
 * ValidatorException thrown by the Validator. It has optional fields that
 * allow better error diagnostics.
 *
 * @author Andreas Sterbenz
 */
public class ValidatorException extends CertificateException {

    private static final long serialVersionUID = -2836879718282292155L;

    public final static Object T_NO_TRUST_ANCHOR =
        "No trusted certificate found";

    public final static Object T_EE_EXTENSIONS =
        "End entity certificate extension check failed";

    public final static Object T_CA_EXTENSIONS =
        "CA certificate extension check failed";

    public final static Object T_CERT_EXPIRED =
        "Certificate expired";

    public final static Object T_SIGNATURE_ERROR =
        "Certificate signature validation failed";

    public final static Object T_NAME_CHAINING =
        "Certificate chaining error";

    private Object type;
    private X509Certificate cert;

    public ValidatorException(String msg) {
        super(msg);
    }

    public ValidatorException(String msg, Throwable cause) {
        super(msg);
        initCause(cause);
    }

    public ValidatorException(Object type) {
        this(type, null);
    }

    public ValidatorException(Object type, X509Certificate cert) {
        super((String)type);
        this.type = type;
        this.cert = cert;
    }

    public ValidatorException(Object type, X509Certificate cert,
            Throwable cause) {
        this(type, cert);
        initCause(cause);
    }

    public ValidatorException(String msg, Object type, X509Certificate cert) {
        super(msg);
        this.type = type;
        this.cert = cert;
    }

    public ValidatorException(String msg, Object type, X509Certificate cert,
            Throwable cause) {
        this(msg, type, cert);
        initCause(cause);
    }

    /**
     * Get the type of the failure (one of the T_XXX constants), if
     * available. This may be helpful when designing a user interface.
     */
    public Object getErrorType() {
        return type;
    }

    /**
     * Get the certificate causing the exception, if available.
     */
    public X509Certificate getErrorCertificate() {
        return cert;
    }

}
