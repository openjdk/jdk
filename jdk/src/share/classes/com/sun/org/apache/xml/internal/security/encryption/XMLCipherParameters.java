/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright  2004 The Apache Software Foundation.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.sun.org.apache.xml.internal.security.encryption;


/**
 * Constants
 */
public interface XMLCipherParameters {

    /** */
    public static final String AES_128 =
        "http://www.w3.org/2001/04/xmlenc#aes128-cbc";

    /**  */
    public static final String AES_256 =
        "http://www.w3.org/2001/04/xmlenc#aes256-cbc";

    /**  */
    public static final String AES_192 =
        "http://www.w3.org/2001/04/xmlenc#aes192-cbc";

    /**  */
    public static final String RSA_1_5 =
        "http://www.w3.org/2001/04/xmlenc#rsa-1_5";

    /**  */
    public static final String RSA_OAEP =
        "http://www.w3.org/2001/04/xmlenc#rsa-oaep-mgf1p";

    /**  */
    public static final String DIFFIE_HELLMAN =
        "http://www.w3.org/2001/04/xmlenc#dh";

    /**  */
    public static final String TRIPLEDES_KEYWRAP =
        "http://www.w3.org/2001/04/xmlenc#kw-tripledes";

    /**  */
    public static final String AES_128_KEYWRAP =
        "http://www.w3.org/2001/04/xmlenc#kw-aes128";

    /**  */
    public static final String AES_256_KEYWRAP =
        "http://www.w3.org/2001/04/xmlenc#kw-aes256";

    /**  */
    public static final String AES_192_KEYWRAP =
        "http://www.w3.org/2001/04/xmlenc#kw-aes192";

    /**  */
    public static final String SHA1 =
        "http://www.w3.org/2000/09/xmldsig#sha1";

    /**  */
    public static final String SHA256 =
        "http://www.w3.org/2001/04/xmlenc#sha256";

    /**  */
    public static final String SHA512 =
        "http://www.w3.org/2001/04/xmlenc#sha512";

    /**  */
    public static final String RIPEMD_160 =
        "http://www.w3.org/2001/04/xmlenc#ripemd160";

    /**  */
    public static final String XML_DSIG =
        "http://www.w3.org/2000/09/xmldsig#";

    /**  */
    public static final String N14C_XML =
        "http://www.w3.org/TR/2001/REC-xml-c14n-20010315";

    /**  */
    public static final String N14C_XML_CMMNTS =
        "http://www.w3.org/TR/2001/REC-xml-c14n-20010315#WithComments";

    /**  */
    public static final String EXCL_XML_N14C =
        "http://www.w3.org/2001/10/xml-exc-c14n#";

    /**  */
    public static final String EXCL_XML_N14C_CMMNTS =
        "http://www.w3.org/2001/10/xml-exc-c14n#WithComments";
}
