/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6511784
 * @summary Make sure that building a path to a CRL issuer works in the
 *          reverse direction
 * @library ../../../../../java/security/testlibrary
 * @build CertUtils
 * @run main BuildPath
 */
import java.security.cert.*;
import java.util.Collections;
import sun.security.provider.certpath.SunCertPathBuilderParameters;

public class BuildPath {

    public static void main(String[] args) throws Exception {

        TrustAnchor anchor =
            new TrustAnchor(CertUtils.getCertFromFile("mgrM2mgrM"), null);
        X509Certificate target = CertUtils.getCertFromFile("mgrM2leadMA");
        X509CertSelector xcs = new X509CertSelector();
        xcs.setSubject("CN=leadMA,CN=mgrM,OU=prjM,OU=divE,OU=Comp,O=sun,C=us");
        xcs.setCertificate(target);
        SunCertPathBuilderParameters params =
            new SunCertPathBuilderParameters(Collections.singleton(anchor),xcs);
        params.setBuildForward(false);
        CertStore cs = CertUtils.createStore(new String[]
            {"mgrM2prjM", "prjM2mgrM", "prjM2divE", "mgrM2leadMA" });
        params.addCertStore(cs);
        CertStore cs2 = CertUtils.createCRLStore
            (new String[] {"mgrMcrl", "prjMcrl"});
        params.addCertStore(cs2);
        PKIXCertPathBuilderResult res = CertUtils.build(params);
    }
}
