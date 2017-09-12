/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.hotspot.test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.AlgorithmParameters;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.hotspot.meta.HotSpotGraphBuilderPlugins;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests the intrinsification of certain crypto methods.
 */
public class HotSpotCryptoSubstitutionTest extends HotSpotGraalCompilerTest {

    @Override
    protected InstalledCode addMethod(DebugContext debug, ResolvedJavaMethod method, CompilationResult compResult) {
        return getBackend().createDefaultInstalledCode(debug, method, compResult);
    }

    SecretKey aesKey;
    SecretKey desKey;
    byte[] input;
    ByteArrayOutputStream aesExpected = new ByteArrayOutputStream();
    ByteArrayOutputStream desExpected = new ByteArrayOutputStream();

    public HotSpotCryptoSubstitutionTest() throws Exception {
        byte[] seed = {0x4, 0x7, 0x1, 0x1};
        SecureRandom random = new SecureRandom(seed);
        KeyGenerator aesKeyGen = KeyGenerator.getInstance("AES");
        KeyGenerator desKeyGen = KeyGenerator.getInstance("DESede");
        aesKeyGen.init(128, random);
        desKeyGen.init(168, random);
        aesKey = aesKeyGen.generateKey();
        desKey = desKeyGen.generateKey();
        input = readClassfile16(getClass());

        aesExpected.write(runEncryptDecrypt(aesKey, "AES/CBC/NoPadding"));
        aesExpected.write(runEncryptDecrypt(aesKey, "AES/CBC/PKCS5Padding"));

        desExpected.write(runEncryptDecrypt(desKey, "DESede/CBC/NoPadding"));
        desExpected.write(runEncryptDecrypt(desKey, "DESede/CBC/PKCS5Padding"));
    }

    @Test
    public void testAESCryptIntrinsics() throws Exception {
        if (compileAndInstall("com.sun.crypto.provider.AESCrypt", HotSpotGraphBuilderPlugins.aesEncryptName, HotSpotGraphBuilderPlugins.aesDecryptName)) {
            ByteArrayOutputStream actual = new ByteArrayOutputStream();
            actual.write(runEncryptDecrypt(aesKey, "AES/CBC/NoPadding"));
            actual.write(runEncryptDecrypt(aesKey, "AES/CBC/PKCS5Padding"));
            Assert.assertArrayEquals(aesExpected.toByteArray(), actual.toByteArray());
        }
    }

    @Test
    public void testCipherBlockChainingIntrinsics() throws Exception {
        if (compileAndInstall("com.sun.crypto.provider.CipherBlockChaining", HotSpotGraphBuilderPlugins.cbcEncryptName, HotSpotGraphBuilderPlugins.cbcDecryptName)) {
            ByteArrayOutputStream actual = new ByteArrayOutputStream();
            actual.write(runEncryptDecrypt(aesKey, "AES/CBC/NoPadding"));
            actual.write(runEncryptDecrypt(aesKey, "AES/CBC/PKCS5Padding"));
            Assert.assertArrayEquals(aesExpected.toByteArray(), actual.toByteArray());

            actual.reset();
            actual.write(runEncryptDecrypt(desKey, "DESede/CBC/NoPadding"));
            actual.write(runEncryptDecrypt(desKey, "DESede/CBC/PKCS5Padding"));
            Assert.assertArrayEquals(desExpected.toByteArray(), actual.toByteArray());
        }
    }

    /**
     * Compiles and installs the substitution for some specified methods. Once installed, the next
     * execution of the methods will use the newly installed code.
     *
     * @param className the name of the class for which substitutions are available
     * @param methodNames the names of the substituted methods
     * @return true if at least one substitution was compiled and installed
     */
    private boolean compileAndInstall(String className, String... methodNames) {
        if (!runtime().getVMConfig().useAESIntrinsics) {
            return false;
        }
        Class<?> c;
        try {
            c = Class.forName(className);
        } catch (ClassNotFoundException e) {
            // It's ok to not find the class - a different security provider
            // may have been installed
            return false;
        }
        boolean atLeastOneCompiled = false;
        for (String methodName : methodNames) {
            if (compileAndInstallSubstitution(c, methodName) != null) {
                atLeastOneCompiled = true;
            }
        }
        return atLeastOneCompiled;
    }

    AlgorithmParameters algorithmParameters;

    private byte[] encrypt(byte[] indata, SecretKey key, String algorithm) throws Exception {

        byte[] result = indata;

        Cipher c = Cipher.getInstance(algorithm);
        c.init(Cipher.ENCRYPT_MODE, key);
        algorithmParameters = c.getParameters();

        byte[] r1 = c.update(result);
        byte[] r2 = c.doFinal();

        result = new byte[r1.length + r2.length];
        System.arraycopy(r1, 0, result, 0, r1.length);
        System.arraycopy(r2, 0, result, r1.length, r2.length);

        return result;
    }

    private byte[] decrypt(byte[] indata, SecretKey key, String algorithm) throws Exception {

        byte[] result = indata;

        Cipher c = Cipher.getInstance(algorithm);
        c.init(Cipher.DECRYPT_MODE, key, algorithmParameters);

        byte[] r1 = c.update(result);
        byte[] r2 = c.doFinal();

        result = new byte[r1.length + r2.length];
        System.arraycopy(r1, 0, result, 0, r1.length);
        System.arraycopy(r2, 0, result, r1.length, r2.length);
        return result;
    }

    private static byte[] readClassfile16(Class<? extends HotSpotCryptoSubstitutionTest> c) throws IOException {
        String classFilePath = "/" + c.getName().replace('.', '/') + ".class";
        InputStream stream = c.getResourceAsStream(classFilePath);
        int bytesToRead = stream.available();
        bytesToRead -= bytesToRead % 16;
        byte[] classFile = new byte[bytesToRead];
        new DataInputStream(stream).readFully(classFile);
        return classFile;
    }

    public byte[] runEncryptDecrypt(SecretKey key, String algorithm) throws Exception {
        byte[] indata = input.clone();
        byte[] cipher = encrypt(indata, key, algorithm);
        byte[] plain = decrypt(cipher, key, algorithm);
        Assert.assertArrayEquals(indata, plain);
        return plain;
    }
}
