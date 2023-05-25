/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8294985
 * @library /test/lib
 * @summary verify correct exception handling in the event of an unparseable
 *  DN in the peer CA
 */

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.KeyStore;


public class TestBadDNForPeerCA12 {

    // Test was originally written for TLSv1.2
    private static final String proto = "TLSv1.2";

    private static final boolean debug = false;

    private final SSLContext sslc;

    protected SSLEngine clientEngine;     // client Engine
    protected ByteBuffer clientOut;       // write side of clientEngine
    protected ByteBuffer clientIn;        // read side of clientEngine

    protected SSLEngine serverEngine;     // server Engine
    protected ByteBuffer serverOut;       // write side of serverEngine
    protected ByteBuffer serverIn;        // read side of serverEngine

    private ByteBuffer cTOs;            // "reliable" transport client->server
    protected ByteBuffer sTOc;          // "reliable" transport server->client

    private static final String keyStoreFile =
        System.getProperty("test.src", "./")
            + "/../../../../javax/net/ssl/etc/keystore";


    /*private static final byte[] clientPayload = new BigInteger
    ("16030301b3010001af03037fdfe102696c38ec073b3824c29b1965a3efda0741f681bacf3cf4c96f1e957a20ccdde8df4356c8c6eb31bf8ea491c2201e0b2c7d252a431c4219ca97659bb61e004a130213011303c02cc02bcca9c030cca8c02f009fccaa00a3009e00a2c024c028c023c027006b006a00670040c00ac014c009c0130039003800330032009d009c003d003c0035002f00ff0100011c000500050100000000000a00160014001d001700180019001e01000101010201030104000b00020100001100090007020004000000000017000000230000000d002c002a040305030603080708080804080508060809080a080b0401050106010402030303010302020302010202002b00050403040303002d000201010032002c002a040305030603080708080804080508060809080a080b04010501060104020303030103020203020102020033006b0069001d002086466ee63234d08df8a0515b7d8140377e5ef7b4db4487e34e0fee13f8d48d4300170041049a83cc4ad95603386ba222bfed4e28ca574ad7f5be724360fba99a49de0eaf001326efda71faf55449887391d450de8c4165de77db407cc8994064cb4ee3d77c", 16).toByteArray();*/
    // the following contains a certificate with an invalid/unparseable
    // distinguished name
    private static final byte[] serverHello = new BigInteger(
        "160303007A020000760303A92EBB3113D0C3369466B3037F721543EC257F366B3"
        + "0B0096FD45D33AB3A067820791EE477A0429904F7114E13CF622C4B65C42926EE5"
        + "CA2F4065AB2E0FFE66590130200002E002B0002030400330024001D002047E0CA2"
        + "E7BDBF7F00A52C100A05397B774F22776604F8DCDCCBF156E09D8820D",
        16).toByteArray();
    private static final byte[] serverPayload = new BigInteger(
        "160303043d0200005503034a587b770a0a19a66fce09ec3cb61cae9e3307fb1d3"
        + "df27ce90b578a77f3cda420513bdf9f4b654f140ec3a3d04eaff2d49131a11d93f"
        + "d99e0f44a6f3349c72b3ec02c00000d0017000000230000ff010001000b0002750"
        + "0027200026f3082026b30820153a003020102020900edbec8f705af2514300d060"
        + "92a864886f70d01010b0500303b310b3009060355040613025553310d300b06035"
        + "5040a0c044a617661311d301b060355040b0c1453756e4a5353452054657374205"
        + "3657269766365301e170d3138303532323037313831365a170d323830353231303"
        + "7313831365a3055310b3009060355040613025553310d300b060355040a0c044a6"
        + "17661311d301b060355040b0c1453756e4a5353452054657374205365726976636"
        + "53118301606035504030c0f52656772657373696f6e20546573743059301306072"
        + "a8648ce3d020106082a8648ce3d03010703420004e7d30444d4e5559d5e7a9b3c6"
        + "773ae7b966481074fb43f96204ead8f73db20aa7118f0f1bdf34ff79f40c908c42"
        + "bf7a159056b08c98da04908cd8f88201801d3a3233021301f0603551d230418301"
        + "680140ddd93c9fe4bbd35b7e8997890fbdb5a3ddb154c300d06092a864886f70d0"
        + "1010b050003820101005393544aacef4ca485988876b12c17545a237076acbbf05"
        + "d9939dae4a5b64dbf6f356f7b0039efb642a31435a4bef29e48ac3df04dd6e558b"
        + "478ccd55037442fb8f9300aefc76723ba2d6235a81e9e6bbece25e9cfc86ceb294"
        + "b6f7422536e0d4c4c12b126e70c48c23f80ba4c7fd72ebc84ec82bc704b318d9cd"
        + "4bb241f530c05b0ee4b5688fa59c6a53a22f8ca1839bc46715e39f2fc6e4fcfb0c"
        + "15f05929bdd965f0820599e34f46ffb513e9576c44ba09234255a67f94584f81d8"
        + "182a578deec7eca62cf9d031b876a69fb8401a5f9c5a2b53565c9c53473d11387f"
        + "c85338b2b835ba8e986b9608ade3edcd0b3a7abcff6100943c561723d27d7904a1"
        + "c840c00007003001d2052c61be62f27349c4c1b673a4d8e0fe93817ba4846867d8"
        + "4aad38c08c60fd6150403004830460221009937abaa6c15159db58454669c8008b"
        + "c227cfebdb601fa75f1cd7a52ec1feb1e022100b79d3d3cde7d1e2536b43977857"
        + "8db3d15220200121f35d6eb06d61839f221660d0000ef03400102002a040305030"
        + "603080708080804080508060809080a080b0401050106010402030303010302020"
        + "30201020200bd003d303b310b3009110355040613025553310d300b060355040a0"
        + "c044a617661311d301b060355040b0c1453756e4a5353452054657374205365726"
        + "9766365003d303b310b3009110355040613025553310d300b060355040a0c044a6"
        + "17661311d301b060355040b0c1453756e4a5353452054657374205365726976636"
        + "5003d303b310b3009110355040613025553310d300b060355040a0c044a6176613"
        + "11d301b060355040b0c1453756e4a535345205465737420536572697663650e000"
        + "000", 16).toByteArray();

    /*
     * The following is to set up the keystores.
     */
    private static final String passwd = "passphrase";

    /*
     * Main entry point for this demo.
     */
    public static void main(String[] args) throws Exception {
        if (debug) {
            System.setProperty("javax.net.debug", "all");
        }

        TestBadDNForPeerCA12 test = new TestBadDNForPeerCA12();

        try {
            test.runTest();
            throw new Exception(
                "TEST FAILED:  Didn't generate any exception");
        } catch (SSLHandshakeException she) {
            System.out.println("TEST PASSED:  Caught expected exception");
        }
    }

    /*
     * Create an initialized SSLContext to use for this demo.
     */

    public TestBadDNForPeerCA12() throws Exception {

        KeyStore ks = KeyStore.getInstance("JKS");
        KeyStore ts = KeyStore.getInstance("JKS");

        char[] passphrase = passwd.toCharArray();

        ks.load(new FileInputStream(keyStoreFile), passphrase);
        ts.load(new FileInputStream(keyStoreFile), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);

        SSLContext sslCtx = SSLContext.getInstance(proto);

        sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        sslc = sslCtx;
    }


    private void runTest() throws Exception {

        createSSLEngines();
        createBuffers();

        /*System.out.println("injecting client hello");
        cTOs = ByteBuffer.wrap(clientPayload);*/

        System.out.println("injecting server hello");
        sTOc = ByteBuffer.wrap(serverHello);

        sTOc.flip();


        /*SSLEngineResult serverResult = serverEngine.unwrap(cTOs, serverIn);
        System.out.println("server unwrap: " + serverResult);
        runDelegatedTasks(serverResult, serverEngine);*/

        SSLEngineResult clientHelloResult = clientEngine.unwrap(sTOc, clientIn);
        System.out.println("client unwrap: " + clientHelloResult);
        runDelegatedTasks(clientHelloResult, clientEngine);

        sTOc.compact();

        System.out.println("injecting server response");
        sTOc = ByteBuffer.wrap(serverPayload);

        sTOc.flip();

        SSLEngineResult clientResult = clientEngine.unwrap(sTOc, clientIn);
        System.out.println("client unwrap: " + clientResult);
        runDelegatedTasks(clientResult, clientEngine);
    }

    private void createSSLEngines() throws Exception {

        serverEngine = sslc.createSSLEngine();

        serverEngine.setEnabledProtocols(new String[] {proto});

        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(true);

        clientEngine = sslc.createSSLEngine();

        clientEngine.setEnabledProtocols(new String[] {proto});
        clientEngine.setUseClientMode(true);

    }


    private void createBuffers() {

        serverIn = ByteBuffer.allocateDirect(65536);

        serverOut = ByteBuffer.wrap("Hi Client, I'm Server".getBytes());

        cTOs = ByteBuffer.allocateDirect(65536);

        clientIn = ByteBuffer.allocateDirect(65536);

        clientOut = ByteBuffer.wrap("Hi Server, I'm Client".getBytes());

        sTOc = ByteBuffer.allocate(65536);
    }

    private static void runDelegatedTasks(SSLEngineResult result,
                                          SSLEngine engine) throws Exception {

        if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = engine.getDelegatedTask()) != null) {
                System.out.println("\trunning delegated task...");
                runnable.run();
            }

            HandshakeStatus hsStatus = engine.getHandshakeStatus();
            if (hsStatus == HandshakeStatus.NEED_TASK) {
                throw new Exception("handshake shouldn't need additional " +
                    "tasks");
            }
            System.out.println("\tnew HandshakeStatus: " + hsStatus);
        }
    }


}
