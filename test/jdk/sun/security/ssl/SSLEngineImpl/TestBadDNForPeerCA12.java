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

    private SSLEngine clientEngine;     // server Engine
    private ByteBuffer clientIn;        // read side of serverEngine
    private ByteBuffer clientOut;        // read side of serverEngine

    private ByteBuffer sTOc;            // "reliable" transport client->server

    private static final String keyStoreFile =
        System.getProperty("test.src", "./")
            + "/../../../../javax/net/ssl/etc/keystore";

    // the following contains a certificate with an invalid/unparseable
    // distinguished name
    /*private static final byte[] payload = Base64.getDecoder().decode(
        "FgMDAcsBAAHHAwPbDfeUCIStPzVIfXuGgCu56dSJOJ6xeus1W44frG5tciDEcBfYt"
            + "/PN/6MFCGojEVcmPw21mVyjYInMo0UozIn4NwBiEwITARMDwCzAK8ypwDDMqMAvA"
            + "J/MqgCjAJ4AosAkwCjAI8AnAGsAagBnAEDALsAywC3AMcAmCgAFKsApJcDAFMAJw"
            + "BMAOQA4ADMAMsAFwA/ABMAOAJ0AnAA9ADwANgAvAP8BAAEcAAUABQEAAAAAAAoAF"
            + "gAUAB0AFwAYABkAHgEAAQEBAgEDAQQACwACAQAAEQAJAAcCAAQAAAAAABcAAAAjA"
            + "AAADQAsACoEAwUDBgMIBwgICAQIBQgGCAkICggLBAEFAQYBBAIDAwMBAwICAwIBA"
            + "gIAKwAFBAMEAwMALQACAQEAMgAsACoEAwUDBgMIBwgICAQIBQgGCAkICggLBAEFA"
            + "QYBBAIDAwMBAwICAwIBAgIALwBrAGkAHQAAAAARACAAZMUAADkwsiaOwcsWAwAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAtAAAAAAAAAAEAADAAAAA=");*/

    private static final byte[] serverResponse = new BigInteger(
        "1703030456BE252BB8F75EF05F480D763A5301B8E71F8794FAD15A84D5550ABA"
        + "C8A008596E3C41CC2375E55AF04A1A4BC6B4B323FBA1854D5E1720971D106E51C6E0"
        + "E7968213352733B56A76814545B582A708D89AFD30549E749E8E7A86A890B240C858"
        + "7104A929BB09925F638DF3520BF7934546AE124520865FAC28B481F4D6FA1B0AA8A1"
        + "ED9897F5E2220939971DF346CBCE6DD04E630184A3F9314B9AD2523994BE4B3862A3"
        + "3ECEC11F7B4079E33DB127F1DD7B1B6839106EB38F74F5FEABFAE3B10171E0E93002"
        + "F7426622C781F9C598474EA5F5D27BCA1C7D6C62FD1EC1EC3AE947A112AEFD3AB454"
        + "A62126F236466BCE0A07C437D775371D6F4A8790187649D55CDF9CC397D8F7F71A83"
        + "5F07C04A1FA211FB458793343EC0E90C80AB5324C76FDB604AB0231ADF56C46DEAD4"
        + "8EF780FA908EE2D973B15977D14D083B4F8C59CF78D5700880B87A6011BEBAFD7A4E"
        + "2B3323F04012BC83E1E904B1B68024CE255248FD2BB048C30F20A00E78B2C48986DA"
        + "A5F244FFCB1285FC843C63A5E92909E332F2D2FAB32FDDDE7358B257773B5A409A96"
        + "B2AFEF4E257660BBF66D23B48D391A56BE39A40B02BF1288950014130F31BBECE3D1"
        + "BADC34CCCBAD19ECE3B727DE8FFCDE917286A5B137515C5A3E032F3535634A1A224D"
        + "8B4E63F63A8B50192349B71B332B8617D958F32FCC03E66E418C62CDA7CBEF058DC7"
        + "BD65D62D74ACB29FA101ED1EED88FBB543D92D283A3D5B28186E1D21CBE96F396B7B"
        + "9D1F9CB6B9D7691F1C6D3FFD2EBC195F19420AA4D703AF56FA8AFA7E8863395E69C4"
        + "9D8CF33EB026D440D0352B693768D02B9CD27581EF10508E773068C98285A5F616D7"
        + "8DEEF3CD6EB00215D990343CF2C11F1ED78986837EE802EDA3712D871745108EC9D6"
        + "0D4E28A0F3315BD4B0B603308BF84DFCB00C4621C10CE25549ACF829AB693438BB42"
        + "E140FAD1AB94AFE271A2058A075EEA094F3BB4EF377A4E984E1810DB4F3B83D9A786"
        + "08EBC0051245C182FA6D75DF27231826A893E5EDDB314D6FD0CFC07CB000CD45027C"
        + "C76F0B802429043C8B3E3F7390F0B1471860C84FF946B8C79C86C8055BB455E7C75B"
        + "66B30D747FD44DA1D55EAC7537C59C992B6A513D32B6D4EE558D4D4FDCC09FEE0F74"
        + "546A537ADF54CEC64FD73B16C6DD275E87482A098E48556C956AE2121DF3E59E5459"
        + "055E299EB936FC1A513281E9CB219A43CB6F5668AFB5ACD478509054D94F4BEB5B14"
        + "238FB2405FC668EC4FB1AC71CBBDDEA2351E066D6A10AAF4A4CCE6F1D80C78AC70D5"
        + "1DA608F5EA86117EAD9B356BE145E413BD6C14B95561E3607AA765D3950F9F0DD024"
        + "A98C101CA8BBE6679FC34BC91BAC130637372D7101DA343D6E09DCA27519530FD29A"
        + "C2D5FE9AEF3ABE2C2F8A335769858031B1109620F78595DF1EC73305CCD3D2CB5E78"
        + "2944CE3CCE18096362A2B0A9D9280084BBEDED86A7AB54023F3EE0D1FC830D48ABDF"
        + "FCFD8FB112F3A762F549C030BED7A688EE6761349571535CAE49F4FDCA433A686500"
        + "DA04C6D68D55204F1EF42BF524CA496CCCFF120BD6493DCD2B5E6ED89B", 16
        ).toByteArray();

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
            test.runTest(serverResponse);
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


    private void runTest(byte[] injected) throws Exception {

        createSSLEngines();
        createBuffers();

        sTOc = ByteBuffer.wrap(injected);

        System.out.println("injecting server response");

        for (int i = 0; i < 10; i++) { //retry if survived
            SSLEngineResult clientResult = clientEngine.unwrap(sTOc, clientIn);
            System.out.println("client unwrap: " + clientResult);
            runDelegatedTasks(clientResult, clientEngine);
        }
    }

    private void createSSLEngines() throws Exception {

        clientEngine = sslc.createSSLEngine();
        clientEngine.setEnabledProtocols(new String[] {proto});
        clientEngine.setUseClientMode(true);
        clientEngine.setNeedClientAuth(true);

    }


    private void createBuffers() {

        clientIn = ByteBuffer.allocateDirect(65536);

        sTOc = ByteBuffer.allocateDirect(65536);

        clientOut = ByteBuffer.wrap("Hi Client, I'm Server".getBytes());
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
