/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8189131 8198240 8191844
 * @requires java.runtime.name ~= "OpenJDK.*"
 * @summary Check root CA entries in cacerts file
 */
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.*;
import java.util.*;

public class VerifyCACerts {

    private static final String CACERTS
            = System.getProperty("java.home") + File.separator + "lib"
            + File.separator + "security" + File.separator + "cacerts";

    // The numbers of certs now.
    private static final int COUNT = 79;

    // map of cert alias to SHA-256 fingerprint
    private static final Map<String, String> FINGERPRINT_MAP
            = new HashMap<String, String>() {
        {
            put("actalisauthenticationrootca [jdk]",
                    "55:92:60:84:EC:96:3A:64:B9:6E:2A:BE:01:CE:0B:A8:6A:64:FB:FE:BC:C7:AA:B5:AF:C1:55:B3:7F:D7:60:66");
            put("buypassclass2ca [jdk]",
                    "9A:11:40:25:19:7C:5B:B9:5D:94:E6:3D:55:CD:43:79:08:47:B6:46:B2:3C:DF:11:AD:A4:A0:0E:FF:15:FB:48");
            put("buypassclass3ca [jdk]",
                    "ED:F7:EB:BC:A2:7A:2A:38:4D:38:7B:7D:40:10:C6:66:E2:ED:B4:84:3E:4C:29:B4:AE:1D:5B:93:32:E6:B2:4D");
            put("camerfirmachambersca [jdk]",
                    "06:3E:4A:FA:C4:91:DF:D3:32:F3:08:9B:85:42:E9:46:17:D8:93:D7:FE:94:4E:10:A7:93:7E:E2:9D:96:93:C0");
            put("camerfirmachambersignca [jdk]",
                    "13:63:35:43:93:34:A7:69:80:16:A0:D3:24:DE:72:28:4E:07:9D:7B:52:20:BB:8F:BD:74:78:16:EE:BE:BA:CA");
            put("camerfirmachamberscommerceca [jdk]",
                    "0C:25:8A:12:A5:67:4A:EF:25:F2:8B:A7:DC:FA:EC:EE:A3:48:E5:41:E6:F5:CC:4E:E6:3B:71:B3:61:60:6A:C3");
            put("certumca [jdk]",
                    "D8:E0:FE:BC:1D:B2:E3:8D:00:94:0F:37:D2:7D:41:34:4D:99:3E:73:4B:99:D5:65:6D:97:78:D4:D8:14:36:24");
            put("certumtrustednetworkca [jdk]",
                    "5C:58:46:8D:55:F5:8E:49:7E:74:39:82:D2:B5:00:10:B6:D1:65:37:4A:CF:83:A7:D4:A3:2D:B7:68:C4:40:8E");
            put("chunghwaepkirootca [jdk]",
                    "C0:A6:F4:DC:63:A2:4B:FD:CF:54:EF:2A:6A:08:2A:0A:72:DE:35:80:3E:2F:F5:FF:52:7A:E5:D8:72:06:DF:D5");
            put("comodorsaca [jdk]",
                    "52:F0:E1:C4:E5:8E:C6:29:29:1B:60:31:7F:07:46:71:B8:5D:7E:A8:0D:5B:07:27:34:63:53:4B:32:B4:02:34");
            put("comodoaaaca [jdk]",
                    "D7:A7:A0:FB:5D:7E:27:31:D7:71:E9:48:4E:BC:DE:F7:1D:5F:0C:3E:0A:29:48:78:2B:C8:3E:E0:EA:69:9E:F4");
            put("comodoeccca [jdk]",
                    "17:93:92:7A:06:14:54:97:89:AD:CE:2F:8F:34:F7:F0:B6:6D:0F:3A:E3:A3:B8:4D:21:EC:15:DB:BA:4F:AD:C7");
            put("usertrustrsaca [jdk]",
                    "E7:93:C9:B0:2F:D8:AA:13:E2:1C:31:22:8A:CC:B0:81:19:64:3B:74:9C:89:89:64:B1:74:6D:46:C3:D4:CB:D2");
            put("usertrusteccca [jdk]",
                    "4F:F4:60:D5:4B:9C:86:DA:BF:BC:FC:57:12:E0:40:0D:2B:ED:3F:BC:4D:4F:BD:AA:86:E0:6A:DC:D2:A9:AD:7A");
            put("utnuserfirstobjectca [jdk]",
                    "6F:FF:78:E4:00:A7:0C:11:01:1C:D8:59:77:C4:59:FB:5A:F9:6A:3D:F0:54:08:20:D0:F4:B8:60:78:75:E5:8F");
            put("utnuserfirstclientauthemailca [jdk]",
                    "43:F2:57:41:2D:44:0D:62:74:76:97:4F:87:7D:A8:F1:FC:24:44:56:5A:36:7A:E6:0E:DD:C2:7A:41:25:31:AE");
            put("utnuserfirsthardwareca [jdk]",
                    "6E:A5:47:41:D0:04:66:7E:ED:1B:48:16:63:4A:A3:A7:9E:6E:4B:96:95:0F:82:79:DA:FC:8D:9B:D8:81:21:37");
            put("addtrustclass1ca [jdk]",
                    "8C:72:09:27:9A:C0:4E:27:5E:16:D0:7F:D3:B7:75:E8:01:54:B5:96:80:46:E3:1F:52:DD:25:76:63:24:E9:A7");
            put("addtrustexternalca [jdk]",
                    "68:7F:A4:51:38:22:78:FF:F0:C8:B1:1F:8D:43:D5:76:67:1C:6E:B2:BC:EA:B4:13:FB:83:D9:65:D0:6D:2F:F2");
            put("addtrustqualifiedca [jdk]",
                    "80:95:21:08:05:DB:4B:BC:35:5E:44:28:D8:FD:6E:C2:CD:E3:AB:5F:B9:7A:99:42:98:8E:B8:F4:DC:D0:60:16");
            put("baltimorecybertrustca [jdk]",
                    "16:AF:57:A9:F6:76:B0:AB:12:60:95:AA:5E:BA:DE:F2:2A:B3:11:19:D6:44:AC:95:CD:4B:93:DB:F3:F2:6A:EB");
            put("baltimorecodesigningca [jdk]",
                    "A9:15:45:DB:D2:E1:9C:4C:CD:F9:09:AA:71:90:0D:18:C7:35:1C:89:B3:15:F0:F1:3D:05:C1:3A:8F:FB:46:87");
            put("digicertglobalrootca [jdk]",
                    "43:48:A0:E9:44:4C:78:CB:26:5E:05:8D:5E:89:44:B4:D8:4F:96:62:BD:26:DB:25:7F:89:34:A4:43:C7:01:61");
            put("digicertglobalrootg2 [jdk]",
                    "CB:3C:CB:B7:60:31:E5:E0:13:8F:8D:D3:9A:23:F9:DE:47:FF:C3:5E:43:C1:14:4C:EA:27:D4:6A:5A:B1:CB:5F");
            put("digicertglobalrootg3 [jdk]",
                    "31:AD:66:48:F8:10:41:38:C7:38:F3:9E:A4:32:01:33:39:3E:3A:18:CC:02:29:6E:F9:7C:2A:C9:EF:67:31:D0");
            put("digicerttrustedrootg4 [jdk]",
                    "55:2F:7B:DC:F1:A7:AF:9E:6C:E6:72:01:7F:4F:12:AB:F7:72:40:C7:8E:76:1A:C2:03:D1:D9:D2:0A:C8:99:88");
            put("digicertassuredidrootca [jdk]",
                    "3E:90:99:B5:01:5E:8F:48:6C:00:BC:EA:9D:11:1E:E7:21:FA:BA:35:5A:89:BC:F1:DF:69:56:1E:3D:C6:32:5C");
            put("digicertassuredidg2 [jdk]",
                    "7D:05:EB:B6:82:33:9F:8C:94:51:EE:09:4E:EB:FE:FA:79:53:A1:14:ED:B2:F4:49:49:45:2F:AB:7D:2F:C1:85");
            put("digicertassuredidg3 [jdk]",
                    "7E:37:CB:8B:4C:47:09:0C:AB:36:55:1B:A6:F4:5D:B8:40:68:0F:BA:16:6A:95:2D:B1:00:71:7F:43:05:3F:C2");
            put("digicerthighassuranceevrootca [jdk]",
                    "74:31:E5:F4:C3:C1:CE:46:90:77:4F:0B:61:E0:54:40:88:3B:A9:A0:1E:D0:0B:A6:AB:D7:80:6E:D3:B1:18:CF");
            put("equifaxsecureca [jdk]",
                    "08:29:7A:40:47:DB:A2:36:80:C7:31:DB:6E:31:76:53:CA:78:48:E1:BE:BD:3A:0B:01:79:A7:07:F9:2C:F1:78");
            put("equifaxsecureebusinessca1 [jdk]",
                    "2E:3A:2B:B5:11:25:05:83:6C:A8:96:8B:E2:CB:37:27:CE:9B:56:84:5C:6E:E9:8E:91:85:10:4A:FB:9A:F5:96");
            put("equifaxsecureglobalebusinessca1 [jdk]",
                    "86:AB:5A:65:71:D3:32:9A:BC:D2:E4:E6:37:66:8B:A8:9C:73:1E:C2:93:B6:CB:A6:0F:71:63:40:A0:91:CE:AE");
            put("geotrustglobalca [jdk]",
                    "FF:85:6A:2D:25:1D:CD:88:D3:66:56:F4:50:12:67:98:CF:AB:AA:DE:40:79:9C:72:2D:E4:D2:B5:DB:36:A7:3A");
            put("geotrustprimaryca [jdk]",
                    "37:D5:10:06:C5:12:EA:AB:62:64:21:F1:EC:8C:92:01:3F:C5:F8:2A:E9:8E:E5:33:EB:46:19:B8:DE:B4:D0:6C");
            put("geotrustprimarycag2 [jdk]",
                    "5E:DB:7A:C4:3B:82:A0:6A:87:61:E8:D7:BE:49:79:EB:F2:61:1F:7D:D7:9B:F9:1C:1C:6B:56:6A:21:9E:D7:66");
            put("geotrustprimarycag3 [jdk]",
                    "B4:78:B8:12:25:0D:F8:78:63:5C:2A:A7:EC:7D:15:5E:AA:62:5E:E8:29:16:E2:CD:29:43:61:88:6C:D1:FB:D4");
            put("geotrustuniversalca [jdk]",
                    "A0:45:9B:9F:63:B2:25:59:F5:FA:5D:4C:6D:B3:F9:F7:2F:F1:93:42:03:35:78:F0:73:BF:1D:1B:46:CB:B9:12");
            put("gtecybertrustglobalca [jdk]",
                    "A5:31:25:18:8D:21:10:AA:96:4B:02:C7:B7:C6:DA:32:03:17:08:94:E5:FB:71:FF:FB:66:67:D5:E6:81:0A:36");
            put("thawteprimaryrootca [jdk]",
                    "8D:72:2F:81:A9:C1:13:C0:79:1D:F1:36:A2:96:6D:B2:6C:95:0A:97:1D:B4:6B:41:99:F4:EA:54:B7:8B:FB:9F");
            put("thawteprimaryrootcag2 [jdk]",
                    "A4:31:0D:50:AF:18:A6:44:71:90:37:2A:86:AF:AF:8B:95:1F:FB:43:1D:83:7F:1E:56:88:B4:59:71:ED:15:57");
            put("thawteprimaryrootcag3 [jdk]",
                    "4B:03:F4:58:07:AD:70:F2:1B:FC:2C:AE:71:C9:FD:E4:60:4C:06:4C:F5:FF:B6:86:BA:E5:DB:AA:D7:FD:D3:4C");
            put("thawtepremiumserverca [jdk]",
                    "3F:9F:27:D5:83:20:4B:9E:09:C8:A3:D2:06:6C:4B:57:D3:A2:47:9C:36:93:65:08:80:50:56:98:10:5D:BC:E9");
            put("verisigntsaca [jdk]",
                    "CB:6B:05:D9:E8:E5:7C:D8:82:B1:0B:4D:B7:0D:E4:BB:1D:E4:2B:A4:8A:7B:D0:31:8B:63:5B:F6:E7:78:1A:9D");
            put("verisignclass1ca [jdk]",
                    "51:84:7C:8C:BD:2E:9A:72:C9:1E:29:2D:2A:E2:47:D7:DE:1E:3F:D2:70:54:7A:20:EF:7D:61:0F:38:B8:84:2C");
            put("verisignclass1g2ca [jdk]",
                    "34:1D:E9:8B:13:92:AB:F7:F4:AB:90:A9:60:CF:25:D4:BD:6E:C6:5B:9A:51:CE:6E:D0:67:D0:0E:C7:CE:9B:7F");
            put("verisignclass1g3ca [jdk]",
                    "CB:B5:AF:18:5E:94:2A:24:02:F9:EA:CB:C0:ED:5B:B8:76:EE:A3:C1:22:36:23:D0:04:47:E4:F3:BA:55:4B:65");
            put("verisignclass2g2ca [jdk]",
                    "3A:43:E2:20:FE:7F:3E:A9:65:3D:1E:21:74:2E:AC:2B:75:C2:0F:D8:98:03:05:BC:50:2C:AF:8C:2D:9B:41:A1");
            put("verisignclass2g3ca [jdk]",
                    "92:A9:D9:83:3F:E1:94:4D:B3:66:E8:BF:AE:7A:95:B6:48:0C:2D:6C:6C:2A:1B:E6:5D:42:36:B6:08:FC:A1:BB");
            put("verisignclass3ca [jdk]",
                    "A4:B6:B3:99:6F:C2:F3:06:B3:FD:86:81:BD:63:41:3D:8C:50:09:CC:4F:A3:29:C2:CC:F0:E2:FA:1B:14:03:05");
            put("verisignclass3g2ca [jdk]",
                    "83:CE:3C:12:29:68:8A:59:3D:48:5F:81:97:3C:0F:91:95:43:1E:DA:37:CC:5E:36:43:0E:79:C7:A8:88:63:8B");
            put("verisignuniversalrootca [jdk]",
                    "23:99:56:11:27:A5:71:25:DE:8C:EF:EA:61:0D:DF:2F:A0:78:B5:C8:06:7F:4E:82:82:90:BF:B8:60:E8:4B:3C");
            put("verisignclass3g3ca [jdk]",
                    "EB:04:CF:5E:B1:F3:9A:FA:76:2F:2B:B1:20:F2:96:CB:A5:20:C1:B9:7D:B1:58:95:65:B8:1C:B9:A1:7B:72:44");
            put("verisignclass3g4ca [jdk]",
                    "69:DD:D7:EA:90:BB:57:C9:3E:13:5D:C8:5E:A6:FC:D5:48:0B:60:32:39:BD:C4:54:FC:75:8B:2A:26:CF:7F:79");
            put("verisignclass3g5ca [jdk]",
                    "9A:CF:AB:7E:43:C8:D8:80:D0:6B:26:2A:94:DE:EE:E4:B4:65:99:89:C3:D0:CA:F1:9B:AF:64:05:E4:1A:B7:DF");
            put("certplusclass2primaryca [jdk]",
                    "0F:99:3C:8A:EF:97:BA:AF:56:87:14:0E:D5:9A:D1:82:1B:B4:AF:AC:F0:AA:9A:58:B5:D5:7A:33:8A:3A:FB:CB");
            put("certplusclass3pprimaryca [jdk]",
                    "CC:C8:94:89:37:1B:AD:11:1C:90:61:9B:EA:24:0A:2E:6D:AD:D9:9F:9F:6E:1D:4D:41:E5:8E:D6:DE:3D:02:85");
            put("keynectisrootca [jdk]",
                    "42:10:F1:99:49:9A:9A:C3:3C:8D:E0:2B:A6:DB:AA:14:40:8B:DD:8A:6E:32:46:89:C1:92:2D:06:97:15:A3:32");
            put("dtrustclass3ca2 [jdk]",
                    "49:E7:A4:42:AC:F0:EA:62:87:05:00:54:B5:25:64:B6:50:E4:F4:9E:42:E3:48:D6:AA:38:E0:39:E9:57:B1:C1");
            put("dtrustclass3ca2ev [jdk]",
                    "EE:C5:49:6B:98:8C:E9:86:25:B9:34:09:2E:EC:29:08:BE:D0:B0:F3:16:C2:D4:73:0C:84:EA:F1:F3:D3:48:81");
            put("identrustdstx3 [jdk]",
                    "06:87:26:03:31:A7:24:03:D9:09:F1:05:E6:9B:CF:0D:32:E1:BD:24:93:FF:C6:D9:20:6D:11:BC:D6:77:07:39");
            put("identrustpublicca [jdk]",
                    "30:D0:89:5A:9A:44:8A:26:20:91:63:55:22:D1:F5:20:10:B5:86:7A:CA:E1:2C:78:EF:95:8F:D4:F4:38:9F:2F");
            put("identrustcommercial [jdk]",
                    "5D:56:49:9B:E4:D2:E0:8B:CF:CA:D0:8A:3E:38:72:3D:50:50:3B:DE:70:69:48:E4:2F:55:60:30:19:E5:28:AE");
            put("letsencryptisrgx1 [jdk]",
                    "96:BC:EC:06:26:49:76:F3:74:60:77:9A:CF:28:C5:A7:CF:E8:A3:C0:AA:E1:1A:8F:FC:EE:05:C0:BD:DF:08:C6");
            put("luxtrustglobalrootca [jdk]",
                    "A1:B2:DB:EB:64:E7:06:C6:16:9E:3C:41:18:B2:3B:AA:09:01:8A:84:27:66:6D:8B:F0:E2:88:91:EC:05:19:50");
            put("quovadisrootca [jdk]",
                    "A4:5E:DE:3B:BB:F0:9C:8A:E1:5C:72:EF:C0:72:68:D6:93:A2:1C:99:6F:D5:1E:67:CA:07:94:60:FD:6D:88:73");
            put("quovadisrootca1g3 [jdk]",
                    "8A:86:6F:D1:B2:76:B5:7E:57:8E:92:1C:65:82:8A:2B:ED:58:E9:F2:F2:88:05:41:34:B7:F1:F4:BF:C9:CC:74");
            put("quovadisrootca2 [jdk]",
                    "85:A0:DD:7D:D7:20:AD:B7:FF:05:F8:3D:54:2B:20:9D:C7:FF:45:28:F7:D6:77:B1:83:89:FE:A5:E5:C4:9E:86");
            put("quovadisrootca2g3 [jdk]",
                    "8F:E4:FB:0A:F9:3A:4D:0D:67:DB:0B:EB:B2:3E:37:C7:1B:F3:25:DC:BC:DD:24:0E:A0:4D:AF:58:B4:7E:18:40");
            put("quovadisrootca3 [jdk]",
                    "18:F1:FC:7F:20:5D:F8:AD:DD:EB:7F:E0:07:DD:57:E3:AF:37:5A:9C:4D:8D:73:54:6B:F4:F1:FE:D1:E1:8D:35");
            put("quovadisrootca3g3 [jdk]",
                    "88:EF:81:DE:20:2E:B0:18:45:2E:43:F8:64:72:5C:EA:5F:BD:1F:C2:D9:D2:05:73:07:09:C5:D8:B8:69:0F:46");
            put("secomscrootca1 [jdk]",
                    "E7:5E:72:ED:9F:56:0E:EC:6E:B4:80:00:73:A4:3F:C3:AD:19:19:5A:39:22:82:01:78:95:97:4A:99:02:6B:6C");
            put("secomscrootca2 [jdk]",
                    "51:3B:2C:EC:B8:10:D4:CD:E5:DD:85:39:1A:DF:C6:C2:DD:60:D8:7B:B7:36:D2:B5:21:48:4A:A4:7A:0E:BE:F6");
            put("swisssigngoldg2ca [jdk]",
                    "62:DD:0B:E9:B9:F5:0A:16:3E:A0:F8:E7:5C:05:3B:1E:CA:57:EA:55:C8:68:8F:64:7C:68:81:F2:C8:35:7B:95");
            put("swisssignplatinumg2ca [jdk]",
                    "3B:22:2E:56:67:11:E9:92:30:0D:C0:B1:5A:B9:47:3D:AF:DE:F8:C8:4D:0C:EF:7D:33:17:B4:C1:82:1D:14:36");
            put("swisssignsilverg2ca [jdk]",
                    "BE:6C:4D:A2:BB:B9:BA:59:B6:F3:93:97:68:37:42:46:C3:C0:05:99:3F:A9:8F:02:0D:1D:ED:BE:D4:8A:81:D5");
            put("soneraclass2ca [jdk]",
                    "79:08:B4:03:14:C1:38:10:0B:51:8D:07:35:80:7F:FB:FC:F8:51:8A:00:95:33:71:05:BA:38:6B:15:3D:D9:27");
            put("securetrustca [jdk]",
                    "F1:C1:B5:0A:E5:A2:0D:D8:03:0E:C9:F6:BC:24:82:3D:D3:67:B5:25:57:59:B4:E7:1B:61:FC:E9:F7:37:5D:73");
            put("xrampglobalca [jdk]",
                    "CE:CD:DC:90:50:99:D8:DA:DF:C5:B1:D2:09:B7:37:CB:E2:C1:8C:FB:2C:10:C0:FF:0B:CF:0D:32:86:FC:1A:A2");
        }
    };

    // Exception list to 90 days expiry policy
    private static final HashSet<String> EXPIRY_EXC_ENTRIES
            = new HashSet<String>(Arrays.asList(
                    "gtecybertrustglobalca [jdk]",
                    "equifaxsecureca [jdk]"
            ));

    // Ninety days in milliseconds
    private static final long NINETY_DAYS = 7776000000L;

    private static boolean atLeastOneFailed = false;

    private static MessageDigest md;

    public static void main(String[] args) throws Exception {
        System.out.println("cacerts file: " + CACERTS);
        md = MessageDigest.getInstance("SHA-256");
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(CACERTS), "changeit".toCharArray());

        // check the count of certs inside
        if (ks.size() != COUNT) {
            atLeastOneFailed = true;
            System.err.println("ERROR: " + ks.size() + " entries, should be "
                    + COUNT);
        }

        // check that all entries in the map are in the keystore
        for (String alias : FINGERPRINT_MAP.keySet()) {
            if (!ks.isCertificateEntry(alias)) {
                atLeastOneFailed = true;
                System.err.println("ERROR: " + alias + " is not in cacerts");
            }
        }

        // pull all the trusted self-signed CA certs out of the cacerts file
        // and verify their signatures
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            System.out.println("\nVerifying " + alias);
            if (!ks.isCertificateEntry(alias)) {
                atLeastOneFailed = true;
                System.err.println("ERROR: " + alias
                        + " is not a trusted cert entry");
            }
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
            if (!checkFingerprint(alias, cert)) {
                atLeastOneFailed = true;
                System.err.println("ERROR: " + alias + " SHA-256 fingerprint is incorrect");
            }
            // Make sure cert can be self-verified
            try {
                cert.verify(cert.getPublicKey());
            } catch (Exception e) {
                atLeastOneFailed = true;
                System.err.println("ERROR: cert cannot be verified:"
                        + e.getMessage());
            }

            // Make sure cert is not expired or not yet valid
            try {
                cert.checkValidity();
            } catch (CertificateExpiredException cee) {
                atLeastOneFailed = true;
                System.err.println("ERROR: cert is expired");
            } catch (CertificateNotYetValidException cne) {
                atLeastOneFailed = true;
                System.err.println("ERROR: cert is not yet valid");
            }

            // If cert is within 90 days of expiring, mark as failure so
            // that cert can be scheduled to be removed/renewed.
            Date notAfter = cert.getNotAfter();
            if (notAfter.getTime() - System.currentTimeMillis() < NINETY_DAYS) {
                if (!EXPIRY_EXC_ENTRIES.contains(alias)) {
                    atLeastOneFailed = true;
                    System.err.println("ERROR: cert \"" + alias + "\" expiry \""
                            + notAfter.toString() + "\" will expire within 90 days");
                }
            }
        }

        if (atLeastOneFailed) {
            throw new Exception("At least one cacert test failed");
        }
    }

    private static boolean checkFingerprint(String alias, Certificate cert)
            throws Exception {
        String fingerprint = FINGERPRINT_MAP.get(alias);
        if (fingerprint == null) {
            // no entry for alias
            return true;
        }
        System.out.println("Checking fingerprint of " + alias);
        byte[] digest = md.digest(cert.getEncoded());
        return fingerprint.equals(toHexString(digest));
    }

    private static String toHexString(byte[] block) {
        StringBuilder buf = new StringBuilder();
        int len = block.length;
        for (int i = 0; i < len; i++) {
            buf.append(String.format("%02X", block[i]));
            if (i < len - 1) {
                buf.append(":");
            }
        }
        return buf.toString();
    }
}
