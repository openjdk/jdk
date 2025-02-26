/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8350710
 * @summary Interoperability test with GlobalSign Code Signing Root R45
 * @build ValidatePathWithParams
 * @run main/othervm/manual -Djava.security.debug=certpath GlobalSignCSR45 OCSP
 * @run main/othervm/manual -Djava.security.debug=certpath GlobalSignCSR45 CRL
 */

public class GlobalSignCSR45 {

    // Owner: CN=GlobalSign GCC R45 EV CodeSigning CA 2020, O=GlobalSign nv-sa, C=BE
    // Issuer: CN=GlobalSign Code Signing Root R45, O=GlobalSign nv-sa, C=BE
    // Serial number: 77bd0e05b7590bb61d4761531e3f75ed
    // Valid from: Mon Jul 27 17:00:00 PDT 2020 until: Sat Jul 27 17:00:00 PDT 2030
    private static final String INT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIG6DCCBNCgAwIBAgIQd70OBbdZC7YdR2FTHj917TANBgkqhkiG9w0BAQsFADBT\n" +
            "MQswCQYDVQQGEwJCRTEZMBcGA1UEChMQR2xvYmFsU2lnbiBudi1zYTEpMCcGA1UE\n" +
            "AxMgR2xvYmFsU2lnbiBDb2RlIFNpZ25pbmcgUm9vdCBSNDUwHhcNMjAwNzI4MDAw\n" +
            "MDAwWhcNMzAwNzI4MDAwMDAwWjBcMQswCQYDVQQGEwJCRTEZMBcGA1UEChMQR2xv\n" +
            "YmFsU2lnbiBudi1zYTEyMDAGA1UEAxMpR2xvYmFsU2lnbiBHQ0MgUjQ1IEVWIENv\n" +
            "ZGVTaWduaW5nIENBIDIwMjAwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoIC\n" +
            "AQDLIO+XHrkBMkOgW6mKI/0gXq44EovKLNT/QdgaVdQZU7f9oxfnejlcwPfOEaP5\n" +
            "pe0B+rW6k++vk9z44rMZTIOwSkRQBHiEEGqk1paQjoH4fKsvtaNXM9JYe5QObQ+l\n" +
            "kSYqs4NPcrGKe2SS0PC0VV+WCxHlmrUsshHPJRt9USuYH0mjX/gTnjW4AwLapBMv\n" +
            "hUrvxC9wDsHUzDMS7L1AldMRyubNswWcyFPrUtd4TFEBkoLeE/MHjnS6hICf0qQV\n" +
            "Duiv6/eJ9t9x8NG+p7JBMyB1zLHV7R0HGcTrJnfyq20Xk0mpt+bDkJzGuOzMyXua\n" +
            "XsXFJJNjb34Qi2HPmFWjJKKINvL5n76TLrIGnybADAFWEuGyip8OHtyYiy7P2uKJ\n" +
            "NKYfJqCornht7KGIFTzC6u632K1hpa9wNqJ5jtwNc8Dx5CyrlOxYBjk2SNY7Wugi\n" +
            "znQOryzxFdrRtJXorNVJbeWv3ZtrYyBdjn47skPYYjqU5c20mLM3GSQScnOrBLAJ\n" +
            "3IXm1CIE70AqHS5tx2nTbrcBbA3gl6cW5iaLiPcDRIZfYmdMtac3qFXcAzaMbs9t\n" +
            "NibxDo+wPXHA4TKnguS2MgIyMHy1k8gh/TyI5mlj+O51yYvCq++6Ov3pXr+2EfG+\n" +
            "8D3KMj5ufd4PfpuVxBKH5xq4Tu4swd+hZegkg8kqwv25UwIDAQABo4IBrTCCAakw\n" +
            "DgYDVR0PAQH/BAQDAgGGMBMGA1UdJQQMMAoGCCsGAQUFBwMDMBIGA1UdEwEB/wQI\n" +
            "MAYBAf8CAQAwHQYDVR0OBBYEFCWd0PxZCYZjxezzsRM7VxwDkjYRMB8GA1UdIwQY\n" +
            "MBaAFB8Av0aACvx4ObeltEPZVlC7zpY7MIGTBggrBgEFBQcBAQSBhjCBgzA5Bggr\n" +
            "BgEFBQcwAYYtaHR0cDovL29jc3AuZ2xvYmFsc2lnbi5jb20vY29kZXNpZ25pbmdy\n" +
            "b290cjQ1MEYGCCsGAQUFBzAChjpodHRwOi8vc2VjdXJlLmdsb2JhbHNpZ24uY29t\n" +
            "L2NhY2VydC9jb2Rlc2lnbmluZ3Jvb3RyNDUuY3J0MEEGA1UdHwQ6MDgwNqA0oDKG\n" +
            "MGh0dHA6Ly9jcmwuZ2xvYmFsc2lnbi5jb20vY29kZXNpZ25pbmdyb290cjQ1LmNy\n" +
            "bDBVBgNVHSAETjBMMEEGCSsGAQQBoDIBAjA0MDIGCCsGAQUFBwIBFiZodHRwczov\n" +
            "L3d3dy5nbG9iYWxzaWduLmNvbS9yZXBvc2l0b3J5LzAHBgVngQwBAzANBgkqhkiG\n" +
            "9w0BAQsFAAOCAgEAJXWgCck5urehOYkvGJ+r1usdS+iUfA0HaJscne9xthdqawJP\n" +
            "sz+GRYfMZZtM41gGAiJm1WECxWOP1KLxtl4lC3eW6c1xQDOIKezu86JtvE21PgZL\n" +
            "yXMzyggULT1M6LC6daZ0LaRYOmwTSfilFQoUloWxamg0JUKvllb0EPokffErcsEW\n" +
            "4Wvr5qmYxz5a9NAYnf10l4Z3Rio9I30oc4qu7ysbmr9sU6cUnjyHccBejsj70yqS\n" +
            "M+pXTV4HXsrBGKyBLRoh+m7Pl2F733F6Ospj99UwRDcy/rtDhdy6/KbKMxkrd23b\n" +
            "ywXwfl91LqK2vzWqNmPJzmTZvfy8LPNJVgDIEivGJ7s3r1fvxM8eKcT04i3OKmHP\n" +
            "V+31CkDi9RjWHumQL8rTh1+TikgaER3lN4WfLmZiml6BTpWsVVdD3FOLJX48YQ+K\n" +
            "C7r1P6bXjvcEVl4hu5/XanGAv5becgPY2CIr8ycWTzjoUUAMrpLvvj1994DGTDZX\n" +
            "hJWnhBVIMA5SJwiNjqK9IscZyabKDqh6NttqumFfESSVpOKOaO4ZqUmZXtC0NL3W\n" +
            "+UDHEJcxUjk1KRGHJNPE+6ljy3dI1fpi/CTgBHpO0ORu3s6eOFAm9CFxZdcJJdTJ\n" +
            "BwB6uMfzd+jF1OJV0NMe9n9S4kmNuRFyDIhEJjNmAUTf5DMOId5iiUgH2vU=\n" +
            "-----END CERTIFICATE-----";

    // Owner: EMAILADDRESS=arvid.vermote@globalsign.com,
    // CN=GLOBALSIGN NV, O=GLOBALSIGN NV, L=Leuven, ST=Vlaams-Brabant, C=BE,
    // OID.1.3.6.1.4.1.311.60.2.1.3=BE, SERIALNUMBER=0459.134.256, OID.2.5.4.15=Private Organization
    // Issuer: CN=GlobalSign GCC R45 EV CodeSigning CA 2020, O=GlobalSign nv-sa, C=BE
    // Serial number: b66a15877511a3157a07dc0
    // Valid from: Wed Oct 09 06:55:59 PDT 2024 until: Thu Jul 22 03:25:22 PDT 2027
    private static final String VALID = "-----BEGIN CERTIFICATE-----\n" +
            "MIIHozCCBYugAwIBAgIMC2ahWHdRGjFXoH3AMA0GCSqGSIb3DQEBCwUAMFwxCzAJ\n" +
            "BgNVBAYTAkJFMRkwFwYDVQQKExBHbG9iYWxTaWduIG52LXNhMTIwMAYDVQQDEylH\n" +
            "bG9iYWxTaWduIEdDQyBSNDUgRVYgQ29kZVNpZ25pbmcgQ0EgMjAyMDAeFw0yNDEw\n" +
            "MDkxMzU1NTlaFw0yNzA3MjIxMDI1MjJaMIHfMR0wGwYDVQQPDBRQcml2YXRlIE9y\n" +
            "Z2FuaXphdGlvbjEVMBMGA1UEBRMMMDQ1OS4xMzQuMjU2MRMwEQYLKwYBBAGCNzwC\n" +
            "AQMTAkJFMQswCQYDVQQGEwJCRTEXMBUGA1UECBMOVmxhYW1zLUJyYWJhbnQxDzAN\n" +
            "BgNVBAcTBkxldXZlbjEWMBQGA1UEChMNR0xPQkFMU0lHTiBOVjEWMBQGA1UEAxMN\n" +
            "R0xPQkFMU0lHTiBOVjErMCkGCSqGSIb3DQEJARYcYXJ2aWQudmVybW90ZUBnbG9i\n" +
            "YWxzaWduLmNvbTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAMPXvA4a\n" +
            "XN4pITY8KUYggXhKZKPFji17T2pBuS3geKhOQhHKM19kYoKhYbxXtj6n2GMgUvdf\n" +
            "bi+cLzRA5UhWIljEBXz3ojK2YO0/nS+IywRiSLZ0o5NWyEr8ll3/HoncSIDDiUt+\n" +
            "bioFiJQHkTSp/diE44Xvn065Y1crhLjhsi8S3OsvMmFm4O99ap2oSDrd7Udm4BgW\n" +
            "ygp+C8bJYdF26+5vSfsTyYRSFWNNmKG9EojiNhtNStSgYWoGju0aQVsAfWX48Gya\n" +
            "XnK+i4UVXHglSnvwYkPQVfxJiu0W/vujhdo1pRhfbJ9mfIDgzAiXeryBithEn9YC\n" +
            "iTWmn42qDznj2idX5/hBveR4T60JlJIAHx1JFc9U3st0EFkjZaxPhDibxXYfcQiq\n" +
            "lhOPzL869/vtnl+cbdIR8cv0PqeRYqpob8oaXJMSAMrmxrGFkuEH95K0hU+MST65\n" +
            "/EX/W6NzDVU22eor5fOoqYUNq+svHIzLrWYAVyCkF8XtIeAldQgX9rroymO9PCbE\n" +
            "tDj9L4L0W3UpyZU9vzcMgeMLXsh+t6L2fL1fSYC2f7YrXo32X0jMdup+G07wt1dY\n" +
            "jqfAPQkFePteA5I5STguLdBXYBaCGP0jwCdUrWUCBaUbvQOybvyojgI+vDvBJADX\n" +
            "D9PAcGoGE7MP4d3qwZsxGJbTkFuWuLj0o0RfAgMBAAGjggHfMIIB2zAOBgNVHQ8B\n" +
            "Af8EBAMCB4AwgZ8GCCsGAQUFBwEBBIGSMIGPMEwGCCsGAQUFBzAChkBodHRwOi8v\n" +
            "c2VjdXJlLmdsb2JhbHNpZ24uY29tL2NhY2VydC9nc2djY3I0NWV2Y29kZXNpZ25j\n" +
            "YTIwMjAuY3J0MD8GCCsGAQUFBzABhjNodHRwOi8vb2NzcC5nbG9iYWxzaWduLmNv\n" +
            "bS9nc2djY3I0NWV2Y29kZXNpZ25jYTIwMjAwVQYDVR0gBE4wTDBBBgkrBgEEAaAy\n" +
            "AQIwNDAyBggrBgEFBQcCARYmaHR0cHM6Ly93d3cuZ2xvYmFsc2lnbi5jb20vcmVw\n" +
            "b3NpdG9yeS8wBwYFZ4EMAQMwCQYDVR0TBAIwADBHBgNVHR8EQDA+MDygOqA4hjZo\n" +
            "dHRwOi8vY3JsLmdsb2JhbHNpZ24uY29tL2dzZ2NjcjQ1ZXZjb2Rlc2lnbmNhMjAy\n" +
            "MC5jcmwwJwYDVR0RBCAwHoEcYXJ2aWQudmVybW90ZUBnbG9iYWxzaWduLmNvbTAT\n" +
            "BgNVHSUEDDAKBggrBgEFBQcDAzAfBgNVHSMEGDAWgBQlndD8WQmGY8Xs87ETO1cc\n" +
            "A5I2ETAdBgNVHQ4EFgQUkA55eaJ3t8Sxpft/D83QXuAMBwkwDQYJKoZIhvcNAQEL\n" +
            "BQADggIBADkvcCDVy5kxuFjerNEe4cBTkyL13QbP64Wl59Kgr8ALUJ9/vbJtc74Q\n" +
            "TsVVPzdDxQW+wAaDTnsoO72J0C97vM5y9JfP+gL1KMagp/tZeCBcR+W4aFh/O3hu\n" +
            "0DkoSoFF2o0Ae4z+KcAtWmw0dpdIq9JV9pup5Q0v7rc3yidY4K3KVbN5GApsybhH\n" +
            "Hf09FteLHE5mR5WbS7T7DcHlHUWwOZf6l2iCWOJziQVZkfD1x23MR1Sm7abP4Eyt\n" +
            "XSjWQCHpW7vPT0FSkTkZhjdlXq1WEBtyF3Kt0k214colUzGtcanX5lz/Lf69Bejb\n" +
            "1yVEiMKpYMJqkB6OVDysPMoNgELtCzOE9q+HdlTdzfdT5FtENu0NFHEB35lAW+P+\n" +
            "Kmf7N3ALX49eoFxWGe1fNaN3S+cBuOKjg6RgR8fbfdddBB5sNlicFWOoUqaA92Q/\n" +
            "ki94U/dlR8YLgh52FdTTcqjU8fpMy4foJdxmB01pVxh1g0kQM2TxQ8UVETskspzo\n" +
            "915/1lXaWpAqGtk+OyuXMX+mi002jBS8v2sT5qNxDFq60b9EJIK/N4N4mSqABQrE\n" +
            "pABAwFExFteWFO7DNO+AQVLuH98P2CI+hamENgNfedV3MVdm1+UgCz9To9p1Xyw0\n" +
            "Wx/FYzHASt8qNG+9rmNZTwaNGIUNqTdRwAJOlFt5wGvpXDy/ThSU\n" +
            "-----END CERTIFICATE-----";

    // Owner: CN=Guan Clean Technology Company Limited, O=Guan Clean Technology Company Limited,
    // L=Langfang, ST=Hebei, C=CN, OID.1.3.6.1.4.1.311.60.2.1.1=Langfang,
    // OID.1.3.6.1.4.1.311.60.2.1.2=Hebei, OID.1.3.6.1.4.1.311.60.2.1.3=CN,
    // SERIALNUMBER=91131022068126507C, OID.2.5.4.15=Private Organization
    // Issuer: CN=GlobalSign GCC R45 EV CodeSigning CA 2020, O=GlobalSign nv-sa, C=BE
    // Serial number: 5f7a0b47907c8dab52505ad4
    // Valid from: Wed Sep 25 02:40:43 PDT 2024 until: Fri Sep 26 02:40:43 PDT 2025
    private static final String REVOKED = "-----BEGIN CERTIFICATE-----\n" +
            "MIIHsDCCBZigAwIBAgIMX3oLR5B8jatSUFrUMA0GCSqGSIb3DQEBCwUAMFwxCzAJ\n" +
            "BgNVBAYTAkJFMRkwFwYDVQQKExBHbG9iYWxTaWduIG52LXNhMTIwMAYDVQQDEylH\n" +
            "bG9iYWxTaWduIEdDQyBSNDUgRVYgQ29kZVNpZ25pbmcgQ0EgMjAyMDAeFw0yNDA5\n" +
            "MjUwOTQwNDNaFw0yNTA5MjYwOTQwNDNaMIIBFDEdMBsGA1UEDwwUUHJpdmF0ZSBP\n" +
            "cmdhbml6YXRpb24xGzAZBgNVBAUTEjkxMTMxMDIyMDY4MTI2NTA3QzETMBEGCysG\n" +
            "AQQBgjc8AgEDEwJDTjEWMBQGCysGAQQBgjc8AgECEwVIZWJlaTEZMBcGCysGAQQB\n" +
            "gjc8AgEBEwhMYW5nZmFuZzELMAkGA1UEBhMCQ04xDjAMBgNVBAgTBUhlYmVpMREw\n" +
            "DwYDVQQHEwhMYW5nZmFuZzEuMCwGA1UEChMlR3VhbiBDbGVhbiBUZWNobm9sb2d5\n" +
            "IENvbXBhbnkgTGltaXRlZDEuMCwGA1UEAxMlR3VhbiBDbGVhbiBUZWNobm9sb2d5\n" +
            "IENvbXBhbnkgTGltaXRlZDCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIB\n" +
            "AL9H1dXBqEfE8N++quAjMqqAb3IX5LTudRwlteO/8AvL8huCHrWgd+qR6g0/wsjL\n" +
            "Py3FA+CulGuH/oVbjugFYmnq6D/xMIgwX6Ap4Z5oHCkN5G9oIgYYvfR1TYh7l9yh\n" +
            "tkF3gnrVLji+GRQD5Z8eH/Bot7ZfR1CbOyHt3wdP3fCeDXA5cNCZ+LcnT1rVF3D7\n" +
            "6mReMtnSZn/hczgDQ32oI0VSLAztrXcYfO+d9fPzmYgpRGmrZBHDXiZzvcw3sor5\n" +
            "evgcDTbN1mp2CEZ206KFRwFgMCaop6moTU1Eg6aiWhQCSAQUOLJXjYJBUlZPFzrW\n" +
            "2dm/j0uptSw43Mk52d1FqlTob072upBClELB8qtVTdz0mF2msDf221pUam1ncT2G\n" +
            "SCqInTqAZso7K0TJ5fURZkoCu4q9z9o3sBqLlg67mphMQf5OQXazOick1Yk1auGK\n" +
            "B6lkvktSdc2rCMBXeAfMZJ5bPZmDebUKKFcNLVBQb2pW+d+1AisnCskZN53pkf3K\n" +
            "lJfuu3MS2I7lyAOFDlU26KeWrP8dxBw/vaPbzllMMkkVe+saHGM0Y8ZxWuqVVmZ3\n" +
            "5OVFKx3cp4QAljNc/Z+DI5+XDbEzXCxUobex5aNAPAMN3oMM9exSF3h4f4NIPG9Y\n" +
            "U9KZeZe2JRGtf4pOy4qRBotUCLPB67MVAlfP3m0pyVPlAgMBAAGjggG2MIIBsjAO\n" +
            "BgNVHQ8BAf8EBAMCB4AwgZ8GCCsGAQUFBwEBBIGSMIGPMEwGCCsGAQUFBzAChkBo\n" +
            "dHRwOi8vc2VjdXJlLmdsb2JhbHNpZ24uY29tL2NhY2VydC9nc2djY3I0NWV2Y29k\n" +
            "ZXNpZ25jYTIwMjAuY3J0MD8GCCsGAQUFBzABhjNodHRwOi8vb2NzcC5nbG9iYWxz\n" +
            "aWduLmNvbS9nc2djY3I0NWV2Y29kZXNpZ25jYTIwMjAwVQYDVR0gBE4wTDBBBgkr\n" +
            "BgEEAaAyAQIwNDAyBggrBgEFBQcCARYmaHR0cHM6Ly93d3cuZ2xvYmFsc2lnbi5j\n" +
            "b20vcmVwb3NpdG9yeS8wBwYFZ4EMAQMwCQYDVR0TBAIwADBHBgNVHR8EQDA+MDyg\n" +
            "OqA4hjZodHRwOi8vY3JsLmdsb2JhbHNpZ24uY29tL2dzZ2NjcjQ1ZXZjb2Rlc2ln\n" +
            "bmNhMjAyMC5jcmwwEwYDVR0lBAwwCgYIKwYBBQUHAwMwHwYDVR0jBBgwFoAUJZ3Q\n" +
            "/FkJhmPF7POxEztXHAOSNhEwHQYDVR0OBBYEFGsPVTiJd8N/MRHhr3cXr6Fu+BkM\n" +
            "MA0GCSqGSIb3DQEBCwUAA4ICAQAGxym0qZCoFjV0vzMQuoc3Dfen7lTbwcFOuXrl\n" +
            "EcjYyLxlnXb+eFaAZ6D0YAtG7cKEoKy/vakafzzfWJcps0OKUSoxJo/c9Do+TtEW\n" +
            "Vlq45PnmuCxFoPwU+D6OyXZXXZRpz9NDzzkhWp06bHwPWtJz9txpo/ZWIlqRGvK8\n" +
            "qLA+wAsV9wyGGqQG7IHM21/m/RaDCOfRGqMbxgU5qB/lGtSGyDHuAuUeTIyXr8Tr\n" +
            "yscyvrDusprGmV74dVO+uEV5VxaxLV7qVIG9tjczFmusmKpPyYjxrqjw9u+Sly+C\n" +
            "hndBJ5kzYEgN9o8631uYt33cwbWBAwfx6zBFeixM7SkkQTidZ2nQ0KvOpIZRfCd/\n" +
            "bCq+1vrx7yW/pRzIy4zaGFTG/c3HtL8Mcx5wZ2CS+XCCWPciAGTB5XbrhMOMxiL2\n" +
            "iBpdcIr6JRWnUiSg7juG2EohUUfAp4V+2uN29I5TUyRTeTBvxyD/tdXzRp4P+fi2\n" +
            "rlS8rU+cW22OySF5kkfpHDH9RQdDjsoXuS9KLGoqhaY2aOO1CFfgQYgiEE+De8Uk\n" +
            "jzDPx+AU7BCwheFRqyu1rutljuJZn+hCk+oyGKwp9yM/Ze5Bs/ZGBWm45LM8QgjC\n" +
            "+W5Mbk6AQN8WTZ9voMVzBpzQIi8u6mPLztrEayuqGtox2Pv7tOQcRk8scqcKn+eM\n" +
            "F55A5g==\n" +
            "-----END CERTIFICATE-----";

    public static void main(String[] args) throws Exception {

        ValidatePathWithParams pathValidator = new ValidatePathWithParams(null);

        if (args.length >= 1 && "CRL".equalsIgnoreCase(args[0])) {
            pathValidator.enableCRLCheck();
        } else {
            // OCSP check by default
            pathValidator.enableOCSPCheck();
        }

        // Validate valid
        pathValidator.validate(new String[]{VALID, INT},
                ValidatePathWithParams.Status.GOOD, null, System.out);

        // Validate Revoked
        pathValidator.validate(new String[]{REVOKED, INT},
                ValidatePathWithParams.Status.REVOKED,
                "Fri Nov 01 01:20:32 PDT 2024", System.out);
    }
}
