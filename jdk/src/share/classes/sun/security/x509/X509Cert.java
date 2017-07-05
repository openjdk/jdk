/*
 * Copyright 1997-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.x509;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.*;
import java.util.Date;
import java.util.Enumeration;

import sun.security.util.*;     // DER

/**
 * @author David Brownell
 *
 * @see CertAndKeyGen
 * @deprecated  Use the new X509Certificate class.
 *              This class is only restored for backwards compatibility.
 */
@Deprecated
public class X509Cert implements Certificate, Serializable {

    static final long serialVersionUID = -52595524744692374L;

    /*
     * NOTE: All fields are marked transient, because we do not want them to
     * be included in the class description when we serialize an object of
     * this class. We override "writeObject" and "readObject" to use the
     * ASN.1 encoding of a certificate as the serialized form, instead of
     * calling the default routines which would operate on the field values.
     *
     * MAKE SURE TO MARK ANY FIELDS THAT ARE ADDED IN THE FUTURE AS TRANSIENT.
     */

    /* The algorithm id */
    transient protected AlgorithmId algid;

    /*
     * Certificate data, and its envelope
     */
    transient private byte rawCert [];
    transient private byte signature [];
    transient private byte signedCert [];

    /*
     * X509.v1 data (parsed)
     */
    transient private X500Name subject; // from subject
    transient private PublicKey pubkey;

    transient private Date notafter;    // from CA (constructor)
    transient private Date notbefore;

    transient private int version;      // from CA (signAndEncode)
    transient private BigInteger serialnum;
    transient private X500Name issuer;
    transient private AlgorithmId issuerSigAlg;

    /*
     * flag to indicate whether or not this certificate has already been parsed
     * (through a call to one of the constructors or the "decode" or
     * "readObject" methods). This is to ensure that certificates are
     * immutable.
     */
    transient private boolean parsed=false;

    /*
     * X509.v2 extensions
     */

    /*
     * X509.v3 extensions
     */

    /*
     * Other extensions ... Netscape, Verisign, SET, etc
     */


    /**
     * Construct a uninitialized X509 Cert on which <a href="#decode">
     * decode</a> must later be called (or which may be deserialized).
     */
    // XXX deprecated, delete this
    public X509Cert() { }


    /**
     * Unmarshals a certificate from its encoded form, parsing the
     * encoded bytes.  This form of constructor is used by agents which
     * need to examine and use certificate contents.  That is, this is
     * one of the more commonly used constructors.  Note that the buffer
     * must include only a certificate, and no "garbage" may be left at
     * the end.  If you need to ignore data at the end of a certificate,
     * use another constructor.
     *
     * @param cert the encoded bytes, with no terminatu (CONSUMED)
     * @exception IOException when the certificate is improperly encoded.
     */
    public X509Cert(byte cert []) throws IOException
    {
        DerValue in = new DerValue (cert);
        parse (in);
        if (in.data.available () != 0)
            throw new CertParseError ("garbage at end");
        signedCert = cert;
    }


    /**
     * Unmarshals a certificate from its encoded form, parsing the
     * encoded bytes.  This form of constructor is used by agents which
     * need to examine and use certificate contents.  That is, this is
     * one of the most commonly used constructors.
     *
     * @param buf the buffer holding the encoded bytes
     * @param offset the offset in the buffer where the bytes begin
     * @param len how many bytes of certificate exist
     *
     * @exception IOException when the certificate is improperly encoded.
     */
    public X509Cert(byte buf [], int offset, int len) throws IOException
    {
        DerValue in = new DerValue (buf, offset, len);

        parse (in);
        if (in.data.available () != 0)
            throw new CertParseError ("garbage at end");
        signedCert = new byte [len];
        System.arraycopy (buf, offset, signedCert, 0, len);
    }


    /**
     * Unmarshal a certificate from its encoded form, parsing a DER value.
     * This form of constructor is used by agents which need to examine
     * and use certificate contents.
     *
     * @param derVal the der value containing the encoded cert.
     * @exception IOException when the certificate is improperly encoded.
     */
    public X509Cert(DerValue derVal) throws IOException
    {
        parse (derVal);
        if (derVal.data.available () != 0)
            throw new CertParseError ("garbage at end");
        signedCert = derVal.toByteArray ();
    }


    /**
     * Partially constructs a certificate from descriptive parameters.
     * This constructor may be used by Certificate Authority (CA) code,
     * which later <a href="#signAndEncode">signs and encodes</a> the
     * certificate.  Also, self-signed certificates serve as CA certificates,
     * and are sometimes used as certificate requests.
     *
     * <P>Until the certificate has been signed and encoded, some of
     * the mandatory fields in the certificate will not be available
     * via accessor functions:  the serial number, issuer name and signing
     * algorithm, and of course the signed certificate.  The fields passed
     * to this constructor are available, and must be non-null.
     *
     * <P>Note that the public key being signed is generally independent of
     * the signature algorithm being used.  So for example Diffie-Hellman
     * keys (which do not support signatures) can be placed in X.509
     * certificates when some other signature algorithm (e.g. DSS/DSA,
     * or one of the RSA based algorithms) is used.
     *
     * @see CertAndKeyGen
     *
     * @param subjectName the X.500 distinguished name being certified
     * @param subjectPublicKey the public key being certified.  This
     *  must be an "X509Key" implementing the "PublicKey" interface.
     * @param notBefore the first time the certificate is valid
     * @param notAfter the last time the certificate is valid
     *
     * @exception CertException if the public key is inappropriate
     */
    public X509Cert(X500Name subjectName, X509Key subjectPublicKey,
                    Date notBefore, Date notAfter) throws CertException
    {
        subject = subjectName;

        if (!(subjectPublicKey instanceof PublicKey))
            throw new CertException (CertException.err_INVALID_PUBLIC_KEY,
                "Doesn't implement PublicKey interface");

        // The X509 cert API requires X509 keys, else things break.
        pubkey = subjectPublicKey;
        notbefore = notBefore;
        notafter = notAfter;
        version = 0;
    }


    /**
     * Decode an X.509 certificate from an input stream.
     *
     * @param in an input stream holding at least one certificate
     * @exception IOException when the certificate is improperly encoded, or
     * if it has already been parsed.
     */
    public void decode(InputStream in) throws IOException
    {
        DerValue val = new DerValue(in);
        parse(val);
        signedCert = val.toByteArray();
    }


    /**
     * Appends the certificate to an output stream.
     *
     * @param out an input stream to which the certificate is appended.
     * @exception IOException when appending fails.
     */
    public void encode (OutputStream out) throws IOException
        { out.write (getSignedCert ()); }


    /**
     * Compares two certificates.  This is false if the
     * certificates are not both X.509 certs, otherwise it
     * compares them as binary data.
     *
     * @param other the object being compared with this one
     * @return true iff the certificates are equivalent
     */
    public boolean      equals (Object other)
    {
        if (other instanceof X509Cert)
            return equals ((X509Cert) other);
        else
            return false;
    }


    /**
     * Compares two certificates, returning false if any data
     * differs between the two.
     *
     * @param other the object being compared with this one
     * @return true iff the certificates are equivalent
     */
    public boolean      equals (X509Cert src)
    {
        if (this == src)
            return true;
        if (signedCert == null || src.signedCert == null)
            return false;
        if (signedCert.length != src.signedCert.length)
            return false;
        for (int i = 0; i < signedCert.length; i++)
            if (signedCert [i] != src.signedCert [i])
                return false;
        return true;
    }


    /** Returns the "X.509" format identifier. */
    public String getFormat () // for Certificate
        { return "X.509"; }


    /** Returns <a href="#getIssuerName">getIssuerName</a> */
    public Principal getGuarantor () // for Certificate
        { return getIssuerName (); }


    /** Returns <a href="#getSubjectName">getSubjectName</a> */
    public Principal getPrincipal ()
        { return getSubjectName (); }


    /**
     * Throws an exception if the certificate is invalid because it is
     * now outside of the certificate's validity period, or because it
     * was not signed using the verification key provided.  Successfully
     * verifying a certificate does <em>not</em> indicate that one should
     * trust the entity which it represents.
     *
     * <P><em>Note that since this class represents only a single X.509
     * certificate, it cannot know anything about the certificate chain
     * which is used to provide the verification key and to establish trust.
     * Other code must manage and use those cert chains.
     *
     * <P>For now, you must walk the cert chain being used to verify any
     * given cert.  Start at the root, which is a self-signed certificate;
     * verify it using the key inside the certificate.  Then use that to
     * verify the next certificate in the chain, issued by that CA.  In
     * this manner, verify each certificate until you reach the particular
     * certificate you wish to verify.  You should not use a certificate
     * if any of the verification operations for its certificate chain
     * were unsuccessful.
     * </em>
     *
     * @param issuerPublicKey the public key of the issuing CA
     * @exception CertException when the certificate is not valid.
     */
    public void verify (PublicKey issuerPublicKey)
    throws CertException
    {
        Date    now = new Date ();

        if (now.before (notbefore))
            throw new CertException (CertException.verf_INVALID_NOTBEFORE);
        if (now.after (notafter))
            throw new CertException (CertException.verf_INVALID_EXPIRED);
        if (signedCert == null)
            throw new CertException (CertException.verf_INVALID_SIG,
                "?? certificate is not signed yet ??");

        //
        // Verify the signature ...
        //
        String          algName = null;

        try {
            Signature   sigVerf = null;

            algName = issuerSigAlg.getName();
            sigVerf = Signature.getInstance(algName);
            sigVerf.initVerify (issuerPublicKey);
            sigVerf.update (rawCert, 0, rawCert.length);

            if (!sigVerf.verify (signature)) {
                throw new CertException (CertException.verf_INVALID_SIG,
                    "Signature ... by <" + issuer + "> for <" + subject + ">");
            }

        // Gag -- too many catch clauses, let most through.

        } catch (NoSuchAlgorithmException e) {
            throw new CertException (CertException.verf_INVALID_SIG,
                "Unsupported signature algorithm (" + algName + ")");

        } catch (InvalidKeyException e) {
            // e.printStackTrace();
            throw new CertException (CertException.err_INVALID_PUBLIC_KEY,
                "Algorithm (" + algName + ") rejected public key");

        } catch (SignatureException e) {
            throw new CertException (CertException.verf_INVALID_SIG,
                "Signature by <" + issuer + "> for <" + subject + ">");
        }
    }


    /**
     * Creates an X.509 certificate, and signs it using the issuer
     * passed (associating a signature algorithm and an X.500 name).
     * This operation is used to implement the certificate generation
     * functionality of a certificate authority.
     *
     * @see #getSignedCert
     * @see #getSigner
     * @see CertAndKeyGen
     *
     * @param serial the serial number of the certificate (non-null)
     * @param issuer the certificate issuer (CA) (non-null)
     * @return the signed certificate, as returned by getSignedCert
     *
     * @exception IOException if any of the data could not be encoded,
     *  or when any mandatory data was omitted
     * @exception SignatureException on signing failures
     */
    public byte []
    encodeAndSign (
        BigInteger      serial,
        X500Signer      issuer
    ) throws IOException, SignatureException
    {
        rawCert = null;

        /*
         * Get the remaining cert parameters, and make sure we have enough.
         *
         * We deduce version based on what attribute data are available
         * For now, we have no attributes, so we always deduce X.509v1 !
         */
        version = 0;
        serialnum = serial;
        this.issuer = issuer.getSigner ();
        issuerSigAlg = issuer.getAlgorithmId ();

        if (subject == null || pubkey == null
                || notbefore == null || notafter == null)
            throw new IOException ("not enough cert parameters");

        /*
         * Encode the raw cert, create its signature and put it
         * into the envelope.
         */
        rawCert = DERencode ();
        signedCert = sign (issuer, rawCert);
        return signedCert;
    }


    /**
     * Returns an X500Signer that may be used to create signatures.  Those
     * signature may in turn be verified using this certificate (or a
     * copy of it).
     *
     * <P><em><b>NOTE:</b>  If the private key is by itself capable of
     * creating signatures, this fact may not be recognized at this time.
     * Specifically, the case of DSS/DSA keys which get their algorithm
     * parameters from higher in the certificate chain is not supportable
     * without using an X509CertChain API, and there is no current support
     * for other sources of algorithm parameters.</em>
     *
     * @param algorithm the signature algorithm to be used.  Note that a
     *  given public/private key pair may support several such algorithms.
     * @param privateKey the private key used to create the signature,
     *  which must correspond to the public key in this certificate
     * @return the Signer object
     *
     * @exception NoSuchAlgorithmException if the signature
     *  algorithm is not supported
     * @exception InvalidKeyException if either the key in the certificate,
     *  or the private key parameter, does not support the requested
     *  signature algorithm
     */
    public X500Signer   getSigner (AlgorithmId algorithmId,
                                   PrivateKey privateKey)
    throws NoSuchAlgorithmException, InvalidKeyException
    {
        String algorithm;
        Signature       sig;

        if (privateKey instanceof Key) {
            Key key = (Key)privateKey;
            algorithm = key.getAlgorithm();
        } else {
            throw new InvalidKeyException("private key not a key!");
        }

        sig = Signature.getInstance(algorithmId.getName());

        if (!pubkey.getAlgorithm ().equals (algorithm)) {

          throw new InvalidKeyException( "Private key algorithm " +
                                         algorithm +
                                         " incompatible with certificate " +
                                         pubkey.getAlgorithm());
        }
        sig.initSign (privateKey);
        return new X500Signer (sig, subject);
    }


    /**
     * Returns a signature object that may be used to verify signatures
     * created using a specified signature algorithm and the public key
     * contained in this certificate.
     *
     * <P><em><b>NOTE:</b>  If the public key in this certificate is not by
     * itself capable of verifying signatures, this may not be recognized
     * at this time.  Specifically, the case of DSS/DSA keys which get
     * their algorithm parameters from higher in the certificate chain
     * is not supportable without using an X509CertChain API, and there
     * is no current support for other sources of algorithm parameters.</em>
     *
     * @param algorithm the algorithm of the signature to be verified
     * @return the Signature object
     * @exception NoSuchAlgorithmException if the signature
     *  algorithm is not supported
     * @exception InvalidKeyException if the key in the certificate
     *  does not support the requested signature algorithm
     */
    public Signature getVerifier(String algorithm)
    throws NoSuchAlgorithmException, InvalidKeyException
    {
        String          algName;
        Signature       sig;

        sig = Signature.getInstance(algorithm);
        sig.initVerify (pubkey);
        return sig;
    }



    /**
     * Return the signed X.509 certificate as a byte array.
     * The bytes are in standard DER marshaled form.
     * Null is returned in the case of a partially constructed cert.
     */
    public byte []      getSignedCert ()
        { return (byte[])signedCert.clone(); }


    /**
     * Returns the certificate's serial number.
     * Null is returned in the case of a partially constructed cert.
     */
    public BigInteger   getSerialNumber ()
        { return serialnum; }


    /**
     * Returns the subject's X.500 distinguished name.
     */
    public X500Name     getSubjectName ()
        { return subject; }


    /**
     * Returns the certificate issuer's X.500 distinguished name.
     * Null is returned in the case of a partially constructed cert.
     */
    public X500Name     getIssuerName ()
        { return issuer; }


    /**
     * Returns the algorithm used by the issuer to sign the certificate.
     * Null is returned in the case of a partially constructed cert.
     */
    public AlgorithmId  getIssuerAlgorithmId ()
        { return issuerSigAlg; }


    /**
     * Returns the first time the certificate is valid.
     */
    public Date getNotBefore ()
        { return new Date(notbefore.getTime()); }


    /**
     * Returns the last time the certificate is valid.
     */
    public Date getNotAfter ()
        { return new Date(notafter.getTime()); }


    /**
     * Returns the subject's public key.  Note that some public key
     * algorithms support an optional certificate generation policy
     * where the keys in the certificates are not in themselves sufficient
     * to perform a public key operation.  Those keys need to be augmented
     * by algorithm parameters, which the certificate generation policy
     * chose not to place in the certificate.
     *
     * <P>Two such public key algorithms are:  DSS/DSA, where algorithm
     * parameters could be acquired from a CA certificate in the chain
     * of issuers; and Diffie-Hellman, with a similar solution although
     * the CA then needs both a Diffie-Hellman certificate and a signature
     * capable certificate.
     */
    public PublicKey            getPublicKey ()
        { return pubkey; }


    /**
     * Returns the X.509 version number of this certificate, zero based.
     * That is, "2" indicates an X.509 version 3 (1993) certificate,
     * and "0" indicates X.509v1 (1988).
     * Zero is returned in the case of a partially constructed cert.
     */
    public int          getVersion ()
        { return version; }


    /**
     * Calculates a hash code value for the object.  Objects
     * which are equal will also have the same hashcode.
     */
    public int          hashCode ()
    {
        int     retval = 0;

        for (int i = 0; i < signedCert.length; i++)
            retval += signedCert [i] * i;
        return retval;
    }


    /**
     * Returns a printable representation of the certificate.  This does not
     * contain all the information available to distinguish this from any
     * other certificate.  The certificate must be fully constructed
     * before this function may be called; in particular, if you are
     * creating certificates you must call encodeAndSign() before calling
     * this function.
     */
    public String       toString ()
    {
        String          s;

        if (subject == null || pubkey == null
                || notbefore == null || notafter == null
                || issuer == null || issuerSigAlg == null
                || serialnum == null)
            throw new NullPointerException ("X.509 cert is incomplete");

        s = "  X.509v" + (version + 1) + " certificate,\n";
        s += "  Subject is " + subject + "\n";
        s += "  Key:  " + pubkey;
        s += "  Validity <" + notbefore + "> until <" + notafter + ">\n";
        s += "  Issuer is " + issuer + "\n";
        s += "  Issuer signature used " + issuerSigAlg.toString () + "\n";
        s += "  Serial number = " + Debug.toHexString(serialnum) + "\n";

        // optional v2, v3 extras

        return "[\n" + s + "]";
    }


    /**
     * Returns a printable representation of the certificate.
     *
     * @param detailed true iff lots of detail is requested
     */
    public String       toString (boolean detailed)
        { return toString (); }


    /************************************************************/

    /*
     * Cert is a SIGNED ASN.1 macro, a three elment sequence:
     *
     *  - Data to be signed (ToBeSigned) -- the "raw" cert
     *  - Signature algorithm (SigAlgId)
     *  - The signature bits
     *
     * This routine unmarshals the certificate, saving the signature
     * parts away for later verification.
     */
    private void parse (DerValue val) throws IOException
    {
        if (parsed == true) {
            throw new IOException("Certificate already parsed");
        }

        DerValue seq [] = new DerValue [3];

        seq [0] = val.data.getDerValue ();
        seq [1] = val.data.getDerValue ();
        seq [2] = val.data.getDerValue ();

        if (val.data.available () != 0)
            throw new CertParseError ("signed overrun, bytes = "
                    + val.data.available ());
        if (seq [0].tag != DerValue.tag_Sequence)
            throw new CertParseError ("signed fields invalid");

        rawCert = seq [0].toByteArray ();       // XXX slow; fixme!


        issuerSigAlg = AlgorithmId.parse (seq [1]);
        signature = seq [2].getBitString ();

        if (seq [1].data.available () != 0) {
            // XXX why was this error check commented out?
            // It was originally part of the next check.
            throw new CertParseError ("algid field overrun");
        }

        if (seq [2].data.available () != 0)
            throw new CertParseError ("signed fields overrun");

        /*
         * Let's have fun parsing the cert itself.
         */
        DerInputStream  in;
        DerValue        tmp;

        in = seq [0].data;

        /*
         * Version -- this is optional (default zero). If it's there it's
         * the first field and is specially tagged.
         *
         * Both branches leave "tmp" holding a value for the serial
         * number that comes next.
         */
        version = 0;
        tmp = in.getDerValue ();
        if (tmp.isConstructed () && tmp.isContextSpecific ()) {
            version = tmp.data.getInteger();
            if (tmp.data.available () != 0)
                throw new IOException ("X.509 version, bad format");
            tmp = in.getDerValue ();
        }

        /*
         * serial number ... an integer
         */
        serialnum = tmp.getBigInteger ();

        /*
         * algorithm type for CA's signature ... needs to match the
         * one on the envelope, and that's about it!  different IDs
         * may represent a signature attack.  In general we want to
         * inherit parameters.
         */
        tmp = in.getDerValue ();
        {
            AlgorithmId         algid;


            algid = AlgorithmId.parse(tmp);

            if (!algid.equals (issuerSigAlg))
                throw new CertParseError ("CA Algorithm mismatch!");

            this.algid = algid;
        }

        /*
         * issuer name
         */
        issuer = new X500Name (in);

        /*
         * validity:  SEQUENCE { start date, end date }
         */
        tmp = in.getDerValue ();
        if (tmp.tag != DerValue.tag_Sequence)
            throw new CertParseError ("corrupt validity field");

        notbefore = tmp.data.getUTCTime ();
        notafter = tmp.data.getUTCTime ();
        if (tmp.data.available () != 0)
            throw new CertParseError ("excess validity data");

        /*
         * subject name and public key
         */
        subject = new X500Name (in);

        tmp = in.getDerValue ();
        pubkey = X509Key.parse (tmp);

        /*
         * XXX for v2 and later, a bunch of tagged options follow
         */

        if (in.available () != 0) {
            /*
             * Until we parse V2/V3 data ... ignore it.
             *
            // throw new CertParseError ("excess cert data");
            System.out.println (
                    "@end'o'cert, optional V2/V3 data unparsed:  "
                    + in.available ()
                    + " bytes"
                    );
            */
        }

        parsed = true;
    }


    /*
     * Encode only the parts that will later be signed.
     */
    private byte [] DERencode () throws IOException
    {
        DerOutputStream raw = new DerOutputStream ();

        encode (raw);
        return raw.toByteArray ();
    }


    /*
     * Marshal the contents of a "raw" certificate into a DER sequence.
     */
    private void encode (DerOutputStream out) throws IOException
    {
        DerOutputStream tmp = new DerOutputStream ();

        /*
         * encode serial number, issuer signing algorithm,
         * and issuer name into the data we'll return
         */
        tmp.putInteger (serialnum);
        issuerSigAlg.encode (tmp);
        issuer.encode (tmp);

        /*
         * Validity is a two element sequence ... encode the
         * elements, then wrap them into the data we'll return
         */
        {
            DerOutputStream     seq = new DerOutputStream ();

            seq.putUTCTime (notbefore);
            seq.putUTCTime (notafter);
            tmp.write (DerValue.tag_Sequence, seq);
        }

        /*
         * Encode subject (principal) and associated key
         */
        subject.encode (tmp);
        tmp.write(pubkey.getEncoded());

        /*
         * Wrap the data; encoding of the "raw" cert is now complete.
         */
        out.write (DerValue.tag_Sequence, tmp);
    }


    /*
     * Calculate the signature of the "raw" certificate,
     * and marshal the cert with the signature and a
     * description of the signing algorithm.
     */
    private byte [] sign (X500Signer issuer, byte data [])
    throws IOException, SignatureException
    {
        /*
         * Encode the to-be-signed data, then the algorithm used
         * to create the signature.
         */
        DerOutputStream out = new DerOutputStream ();
        DerOutputStream tmp = new DerOutputStream ();

        tmp.write (data);
        issuer.getAlgorithmId ().encode(tmp);


        /*
         * Create and encode the signature itself.
         */
        issuer.update (data, 0, data.length);
        signature = issuer.sign ();
        tmp.putBitString (signature);

        /*
         * Wrap the signed data in a SEQUENCE { data, algorithm, sig }
         */
        out.write (DerValue.tag_Sequence, tmp);
        return out.toByteArray ();
    }


    /**
     * Serialization write ... X.509 certificates serialize as
     * themselves, and they're parsed when they get read back.
     * (Actually they serialize as some type data from the
     * serialization subsystem, then the cert data.)
     */
    private void writeObject (java.io.ObjectOutputStream stream)
        throws IOException
        { encode(stream); }

    /**
     * Serialization read ... X.509 certificates serialize as
     * themselves, and they're parsed when they get read back.
     */
    private void readObject (ObjectInputStream stream)
        throws IOException
        { decode(stream); }
}
