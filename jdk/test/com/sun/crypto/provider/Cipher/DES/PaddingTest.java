/*
 * Copyright (c) 1997, 2007, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 0000000 6296075
 * @summary PaddingTest
 * @author Jan Luehe
 */
import java.io.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import com.sun.crypto.provider.*;

public class PaddingTest {

    Cipher cipher;
    IvParameterSpec params = null;
    SecretKey cipherKey = null;
    String pinfile = null;
    String cfile = null;
    String poutfile = null;

    public static byte[] key = {
        (byte)0x01,(byte)0x23,(byte)0x45,(byte)0x67,
        (byte)0x89,(byte)0xab,(byte)0xcd,(byte)0xef
    };

    public static byte[] key3 = {
        (byte)0x01,(byte)0x23,(byte)0x45,(byte)0x67,
        (byte)0x89,(byte)0xab,(byte)0xcd,(byte)0xef,
        (byte)0xf0,(byte)0xe1,(byte)0xd2,(byte)0xc3,
        (byte)0xb4,(byte)0xa5,(byte)0x96,(byte)0x87,
        (byte)0xfe,(byte)0xdc,(byte)0xba,(byte)0x98,
        (byte)0x76,(byte)0x54,(byte)0x32,(byte)0x10};

    public static byte[] iv  = {
        (byte)0xfe,(byte)0xdc,(byte)0xba,(byte)0x98,
        (byte)0x76,(byte)0x54,(byte)0x32,(byte)0x10};

    static String[] crypts = {"DES", "DESede"};
    static String[] modes = {"ECB", "CBC", "CFB", "OFB", "PCBC"};
    static String[] paddings = {"PKCS5Padding", "NoPadding"};
    static int numFiles = 11;
    static final String currDir = System.getProperty("test.src", ".");
    static String dataDir = currDir + "/inputData/";

    private String padding = null;

    public static void main(String argv[]) throws Exception {
        PaddingTest pt = new PaddingTest();
        pt.run();
    }

    public PaddingTest() {
    }

    public void run() throws Exception {

        for (int l=0; l<numFiles; l++) {
            pinfile = new String(dataDir + "plain" + l + ".txt");
            for (int i=0; i<crypts.length; i++) {
                for (int j=0; j<modes.length; j++) {
                    for (int k=0; k<paddings.length; k++) {
                        System.out.println
                            ("===============================");
                        System.out.println
                            (crypts[i]+" "+modes[j]+" " + paddings[k]+ " " +
                             "plain" + l + " test");
                        cfile = new String
                            ("c" + l + "_" +
                             crypts[i] + "_" +
                             modes[j] + "_" +
                             paddings[k] + ".bin");
                        poutfile = new String
                            ("p" + l +
                             "_" + crypts[i] + modes[j] + paddings[k] + ".txt");

                        init(crypts[i], modes[j], paddings[k]);
                        padding = paddings[k];
                        runTest();
                    }
                }
            }
        }
    }

    public void init(String crypt, String mode, String padding)
        throws Exception {

        SunJCE jce = new SunJCE();
        Security.addProvider(jce);

        KeySpec desKeySpec = null;
        SecretKeyFactory factory = null;

        StringBuffer cipherName = new StringBuffer(crypt);
        if (mode.length() != 0)
            cipherName.append("/" + mode);
        if (padding.length() != 0)
            cipherName.append("/" + padding);

        cipher = Cipher.getInstance(cipherName.toString());
        if (crypt.endsWith("ede")) {
            desKeySpec = new DESedeKeySpec(key3);
            factory = SecretKeyFactory.getInstance("DESede", "SunJCE");
        } else {
            desKeySpec = new DESKeySpec(key);
            factory = SecretKeyFactory.getInstance("DES", "SunJCE");
        }

        // retrieve the cipher key
        cipherKey = factory.generateSecret(desKeySpec);

        // retrieve iv
        if (!mode.equals("ECB"))
            params = new IvParameterSpec(iv);
        else
            params = null;
    }

    public void runTest() throws Exception {

        int bufferLen = 512;
        byte[] input = new byte[bufferLen];
        int len;
        int totalInputLen = 0;

        BufferedInputStream pin = null;
        BufferedOutputStream cout = null;
        BufferedInputStream cin = null;
        BufferedOutputStream pout = null;

        try {
            pin = new BufferedInputStream(new FileInputStream(pinfile));
            cout = new BufferedOutputStream(new FileOutputStream(cfile));
            cipher.init(Cipher.ENCRYPT_MODE, cipherKey, params);

            while ((len = pin.read(input, 0, bufferLen)) > 0) {
                totalInputLen += len;
                byte[] output = cipher.update(input, 0, len);
                cout.write(output, 0, output.length);
                cout.flush();
            }

            len = cipher.getOutputSize(0);

            byte[] out = new byte[len];
            len = cipher.doFinal(out, 0);
            cout.write(out, 0, len);
            cout.flush();

            cin = new BufferedInputStream(new FileInputStream(cfile));
            pout = new BufferedOutputStream(new FileOutputStream(poutfile));
            cipher.init(Cipher.DECRYPT_MODE, cipherKey, params);

            byte[] output = null;
            while ((len = cin.read(input, 0, bufferLen)) > 0) {
                output = cipher.update(input, 0, len);
                pout.write(output, 0, output.length);
                pout.flush();
            }

            len = cipher.getOutputSize(0);
            out = new byte[len];
            len = cipher.doFinal(out, 0);
            pout.write(out, 0, len);
            pout.flush();

            Process child = Runtime.getRuntime().exec
                ("diff " + pinfile + " " + poutfile);
            InputStream in = child.getInputStream();
            byte[] data = new byte[64];

            while((len = in.read(data)) != -1)
                System.out.write(data, 0, len);
            in.close();
            child.waitFor();
            System.out.println("child exited with " + child.exitValue());
        }
        catch (IllegalBlockSizeException ex) {
            if ((totalInputLen % 8 != 0) && (padding.equals("NoPadding")))
                return;
            else {
                System.out.println("Test failed!");
                throw ex;
            }
        }
        finally {
            try {
                if (pin != null)
                    pin.close();
                if (pout != null)
                    pout.close();
                if (cin != null)
                    cin.close();
                if (cout != null)
                    cout.close();
            }
            catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
    }

}
