/*
 * Copyright (c) 2001, 2007, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.crypto.provider;

import java.io.IOException;
import java.io.Serializable;
import java.io.ObjectStreamException;
import java.security.AlgorithmParameters;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SealedObject;
import javax.crypto.spec.*;

/**
 * This class is introduced to workaround a problem in
 * the SunJCE provider shipped in JCE 1.2.1: the class
 * SealedObjectForKeyProtector was obfuscated due to a mistake.
 *
 * In order to retrieve secret keys in a JCEKS KeyStore written
 * by the SunJCE provider in JCE 1.2.1, this class will be used.
 *
 * @author Valerie Peng
 *
 *
 * @see JceKeyStore
 */

final class ai extends javax.crypto.SealedObject {

    static final long serialVersionUID = -7051502576727967444L;

    ai(SealedObject so) {
        super(so);
    }

    Object readResolve() throws ObjectStreamException {
        return new SealedObjectForKeyProtector(this);
    }
}
