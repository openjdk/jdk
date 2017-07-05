/*
 * Copyright (c) 2000, 2002, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider.certpath;

import sun.security.util.Debug;

import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import sun.security.x509.AuthorityKeyIdentifierExtension;
import sun.security.x509.KeyIdentifier;
import sun.security.x509.SubjectKeyIdentifierExtension;
import sun.security.x509.X509CertImpl;

/*
 * This class represents a vertex in the adjacency list. A
 * vertex in the builder's view is just a distinguished name
 * in the directory.  The Vertex contains a certificate
 * along an attempted certification path, along with a pointer
 * to a list of certificates that followed this one in various
 * attempted certification paths.
 *
 * @author      Sean Mullan
 * @since       1.4
 */
public class Vertex {

    private static final Debug debug = Debug.getInstance("certpath");
    private Certificate cert;
    private int         index;
    private Throwable   throwable;

    /**
     * Constructor; creates vertex with index of -1
     * Use setIndex method to set another index.
     *
     * @param cert Certificate associated with vertex
     */
    Vertex(Certificate cert) {
        this.cert = cert;
        this.index = -1;
    }

    /**
     * return the certificate for this vertex
     *
     * @returns Certificate
     */
    public Certificate getCertificate() {
        return cert;
    }

    /**
     * get the index for this vertex, where the index is the row of the
     * adjacency list that contains certificates that could follow this
     * certificate.
     *
     * @returns int index for this vertex, or -1 if no following certificates.
     */
    public int getIndex() {
        return index;
    }

    /**
     * set the index for this vertex, where the index is the row of the
     * adjacency list that contains certificates that could follow this
     * certificate.
     *
     * @param ndx int index for vertex, or -1 if no following certificates.
     */
    void setIndex(int ndx) {
        index = ndx;
    }

    /**
     * return the throwable associated with this vertex;
     * returns null if none.
     *
     * @returns Throwable
     */
    public Throwable getThrowable() {
        return throwable;
    }

    /**
     * set throwable associated with this vertex; default value is null.
     *
     * @param throwable Throwable associated with this vertex
     *                  (or null)
     */
    void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    /**
     * Return full string representation of vertex
     *
     * @returns String representation of vertex
     */
    public String toString() {
        return certToString() + throwableToString() + indexToString();
    }

    /**
     * Return string representation of this vertex's
     * certificate information.
     *
     * @returns String representation of certificate info
     */
    public String certToString() {
        String out = "";
        if (cert == null || ! (cert instanceof X509Certificate))
            return "Cert:       Not an X509Certificate\n";

        X509CertImpl x509Cert = null;
        try {
            x509Cert = X509CertImpl.toImpl((X509Certificate)cert);
        } catch (CertificateException ce) {
            if (debug != null) {
                debug.println("Vertex.certToString() unexpected exception");
                ce.printStackTrace();
            }
            return out;
        }

        out =       "Issuer:     " + x509Cert.getIssuerX500Principal() + "\n";
        out = out + "Subject:    " + x509Cert.getSubjectX500Principal() + "\n";
        out = out + "SerialNum:  " + (x509Cert.getSerialNumber()).toString(16) + "\n";
        out = out + "Expires:    " + x509Cert.getNotAfter().toString() + "\n";
        boolean[] iUID = x509Cert.getIssuerUniqueID();
        if (iUID != null) {
            out = out + "IssuerUID:  ";
            for (int i=0; i < iUID.length; i++) {
                out = out + (iUID[i]?1:0);
            }
            out = out + "\n";
        }
        boolean[] sUID = x509Cert.getSubjectUniqueID();
        if (sUID != null) {
            out = out + "SubjectUID: ";
            for (int i=0; i< sUID.length; i++) {
                out = out + (sUID[i]?1:0);
            }
            out = out + "\n";
        }
        SubjectKeyIdentifierExtension sKeyID = null;
        try {
            sKeyID = x509Cert.getSubjectKeyIdentifierExtension();
            if (sKeyID != null) {
                KeyIdentifier keyID = (KeyIdentifier)sKeyID.get(sKeyID.KEY_ID);
                out = out + "SubjKeyID:  " + keyID.toString();
            }
        } catch (Exception e) {
            if (debug != null) {
                debug.println("Vertex.certToString() unexpected exception");
                e.printStackTrace();
            }
        }
        AuthorityKeyIdentifierExtension aKeyID = null;
        try {
            aKeyID = x509Cert.getAuthorityKeyIdentifierExtension();
            if (aKeyID != null) {
                KeyIdentifier keyID = (KeyIdentifier)aKeyID.get(aKeyID.KEY_ID);
                out = out + "AuthKeyID:  " + keyID.toString();
            }
        } catch (Exception e) {
            if (debug != null) {
                debug.println("Vertex.certToString() 2 unexpected exception");
                e.printStackTrace();
            }
        }
        return out;
    }

    /**
     * return Vertex throwable as String compatible with
     * the way toString returns other information
     *
     * @returns String form of exception (or "none")
     */
    public String throwableToString() {
        String out = "Exception:  ";
        if (throwable != null)
            out = out + throwable.toString();
        else
            out = out + "null";
        out = out + "\n";
        return out;
    }

    /**
     * return Vertex index as String compatible with
     * the way other Vertex.xToString() methods display
     * information.
     *
     * @returns String form of index as "Last cert?  [Yes/No]
     */
    public String moreToString() {
        String out = "Last cert?  ";
        out = out + ((index == -1)?"Yes":"No");
        out = out + "\n";
        return out;
    }

    /**
     * return Vertex index as String compatible with
     * the way other Vertex.xToString() methods displays other information.
     *
     * @returns String form of index as "Index:     [numeric index]"
     */
    public String indexToString() {
        String out = "Index:      " + index + "\n";
        return out;
    }
}
