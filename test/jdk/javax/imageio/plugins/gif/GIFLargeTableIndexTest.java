/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7131823
 * @summary Test tableIndex > 4096 decodes correctly
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class GIFLargeTableIndexTest {

    public void testLargeTableGIF() throws IOException {
        ImageInputStream stream = ImageIO.createImageInputStream(new GZIPInputStream(new ByteArrayInputStream(
            Base64.getDecoder().decode(
            "H4sIAAAAAAAAAO3W+VcTBrbA8TrWZ/bUsY5j1TKOdQSSkFp1rEVlHOsICZBxrONYtdSqFbKKgMga"
                    + "EZDsEQER2ffsYd8hIiDZIwKyE9mzh32HvL73H7xf3/Hz0z333J/v+V7w+se3J29+8tknn01/8j8c"
                    + "Dgfyf4ffNn/7nSOOy1dZ4fkZFf3M8uGGNfiTUc/nk/Qik7LKBm6f9e5ZZE+tapc24RBQQmveDM3b"
                    + "NVVdNAdkY4q7BAsMDb56ULIEhjHbx8tWWL7CHkvVGpSrmpqv2+DozEvrTQ74H52/9fyP/0PaC16N"
                    + "vNewDNjjctLrakAYPY1fq+gzrgC/cP0O8yM+nPFSUKfsN62C9iLcsdcIEcx0Yb1qwLwG3oc85X2d"
                    + "GMnKEDWoBy3rkP2o0z43SFHsTHGjZsi6Af3S7YzvT+RoTpakSTts24Q5oT1wfhQqN1sq0+ntDvjX"
                    + "Oa9iPgV/cYr2de7reMje7yIBR/Ja6Z8jfmygHcl/w3bscw9fB3xTIE/8CvvyFP2bQmUK8lpdJPBo"
                    + "kTrtGKG/gX60WJt5KmJ1HXiM9zb3e+YXpxnH+O8KvdO/iwIdF3TxfxD+2Mg4Lnwvvl4fvgH6q6i3"
                    + "9Lbq5WnmX8X9lcSBuijwCclgbbC5v5F5QjrcGLm2ugH+tuRDcxx47xnWt6Wjbay97tGQk2XjimTE"
                    + "tSbWyfJJTYZ7xCbkuwpDRwE2/Qz7u0pTt+hafTTUvcrSV0EYaGK7V9uGGiLWNqGnaqZHWpl7PTin"
                    + "amcn1OnuVNjpunljl/CajHO6ftE6WB/hgJ1pWJ4ZV6V7cM80ri5YBuqpcI+m9ZV584CM6yHb3Fhf"
                    + "W3M44D8/+h3M8+eY//qS9vNjkJv851jYGcDNuN/7et6M/8NPtJtPviDLbyZ8GQ34hfZnjucv9L9k"
                    + "0X5huErkvzDdmgC3WN9oPW+x/zpMu8X5zia/xT2zCbj99O8wr9uJ//iSfvsZxk1xO8n3DPBO8r98"
                    + "ve6k/Psn+p3nP5IVd1J/igb++uIXjtevab9m0X99iZcofk0nNwHvZtzXet3NfDBMv5sVblPczY7e"
                    + "BPrnPIZh/HOffMnwz2O4Kf3zOWdAAQXPfDEBhc9/YgQUvSQrA4qzokF4Xh4Hg+cXZTHwAoFEiRdK"
                    + "mkAEUZkWQxBXDTMIkjqbkiBt2gQRS17DsMTSN18yiWVKh5uKWK49AyZVvPPFkirf/8QkVfWTVaTq"
                    + "4WgwuWaUgyXXTmYxyXUmiYpcb2sCUxpmtVhK4+Iwk9K0alNRZJub4Huvfgf3vtf8X06se69BaPW9"
                    + "FpgHJLD19zjvwLY/+LEC33xBUQe2f0mF3Jf/met9X/GXbNZ9patUfV/lJoMEqb/ReQdp/qpnBWm/"
                    + "s6uDdGcckOC3f4f7BHf8w4kd/A6D1gR3+npAQ7r+hfMJ6f63Hzvk/Y8UTUjPT1Tog95fuD4P+n7N"
                    + "Zj/ox0s1DwbIMmjo4H2dT+jQAz07dDjcrgnVRzugDz88hvs+HHnixHk4ykBrH45xPGBh489wvmET"
                    + "zx1+nLDJlxRt2FQWFRZuyOP6hhuLsjnhJoFUG26WyGARljKdb4S1Ss+JsNXZtRH2Jgcscvo1HBc5"
                    + "88aJGzmrROsi57Qe8Kj5dzhc1MJ7P27UYj9FF7U0TIVHL49ycdErk9nc6FWTVBe9ZpPBqeuzOhx1"
                    + "Y1HPpW6u2nVUx6YD/uoT0L1HnyJztsBprz51vRcDIeZsdQI0AzD3Hn8uytmGpjVD8Pdi91lytnsA"
                    + "Xn/GuBf3FTIXiKO9/lxwLx5JzAX7AVr+qLz35JgoF0qhtewz3Us4ZcmFUwGtfwIF0r5H5u3g0lq/"
                    + "cg2kexPzdmYD2pwxgYwfRHm7pLQ2pAMfyLxuydstA7z5mhHIuo3M36OjvTkmCGQTifl79YD2b5WB"
                    + "nGBR/n47rf2UKZAbacl3cgDkfwPdfxqHLDgAp8u/d72fyCIWHHQCKjwx958liwoOoekKb/z9pAxL"
                    + "wWEPoPKfjPvJBchCFxxd+YPgfoqIWIjwA6r+o7z/vEJUiKLQVddN91MbLIVoKlD9MyjoRSuy6AiX"
                    + "rr7tGpSmJhYdzQZq/DFBL7tERceldA0RH5Q+aCk6IQNq7zGCMsaRxSd1dG2wICjTQix21wN1D5VB"
                    + "WfOi4tN2ui7SFJS9bin2cADfPgIF53yK4p2FM97GuQbnQki8c06gDhom2JH3uZh3Hs3oYOGD8/dZ"
                    + "eRc8QO+eMoILvkLxvXCMd8mC4EIkiY/1A3W+UAYXHRPzfSiMzgxTcPEpKx9HBXXlgEJ436MEF7mM"
                    + "rgLXEL43SXApG9TNw4QIfhALLksZ3SJ8iPC6VXBFBnpfwggR3UYJr+oY7ysEIWIiSXhND+qpUYZI"
                    + "gsXCG3ZGT4MpRBppFfo5QL2vQA9K4lCim3Bmb6vrg1IWSXTLCdwnxzwoSxaL7qCZfWr8g/IMq+iu"
                    + "B7j/LeNBRQFKHIBj9ncJHlSKSGKCH3igV/mgqkIsJlGYA4OmB9UNVjGFCh78AAqtaUVJArnMwXHX"
                    + "0Fq1gyQJygYPGTChdV1iSYiUOWTBh9YPWiWhMvDwNCO0YRwlDdMxh+cFoY0WkjRCD9YvK0Ob5sXS"
                    + "KDtTv24Kla1bpVQH+MMn4IevPnUriYGzPnyKeNgMIZfEOkFGANiHrz+XlMSjWSMQwsOWfbaSBA/I"
                    + "6GfMh61fuZXScazRz4UP25DkUqYfZOyPqodvjklK2RTW2D7zw/ZTtlIuFTL+J3CY/Hu3skQua/wr"
                    + "RJjCm1yWlA2ZcMaGKX+QlKVIWRNIQpjquq0sVQaZ/JoZpr7tVp6mY00eE4ZpiOTydD1k6ltVmDZY"
                    + "Up5pZ02dMofpIm3l2Q6I4W/g8LdxbhWOXDjb8D0ivINFrsh3gho9seHvkiUVhWi20ZsQ3plhqyj2"
                    + "gJr+yQzvKnCr5OPYph+E4d0icqXQD2r+jyr8fYWkUkxhm6+bw3sabJVSKtTyMziit9WtqpTLttxG"
                    + "RPSpyVXl2VCrPzaiv0tSVSllW4mEiIFBW1W1DGq7x4wYHHerrtWxbcHCiCELubpeD7U/VEUMz0uq"
                    + "G+1se6Q5Qr9uq5Y5oNOPwJEfPkXXNMM503GIyBEIpabFCTZDw0aOfi6taUNzZliEyLF99pp2D9js"
                    + "U2bk+FfoWgWOM5ssjJxAUmpVfrC5F6rIyWPSWg2FM5dhjpw6Za/97f3A5nPAUYbv0XUdXM58ASLK"
                    + "6E2p68yGLfCwUaYfpHXdUs6CiBBlvm6v65HBFkuYUZbb6Po+HWexQhhlJVLqB/SwpRpVlC1YWj9k"
                    + "5yw1mKPskfZ6vQO2/AocPR2HbhiBc5dbEdEzLErDmBN8RY6Nnk2WNkyguStqQvRchr1hygO++pYZ"
                    + "PV+AbjTiuKtdwugFEaXR7Adf61VFL1ZIG60U7tqgOXqpwd5op8LXP4Cpy63ophkud30cQV1RU5rm"
                    + "suEbBix1tUvatCDlblgI1LVBe9OSDL45zaSuj6NlKzru5ryQumGhyNb0cMeyiro5L5Vt2LkOx7qZ"
                    + "6li3y37rgEdbnB9t9Xy0zf/RdtojIO8RWP4IangEB8TscI7Z6Rmzyz9mNy1mDy9mrzxmvyHGCfD4"
                    + "gPPjg56PD/k/Pkx77MJ7jJA/RhkeowGxR5xjj3rGHvePPUGLPcmLdZfHnjbEegDizjrHnfOMO+8f"
                    + "d4EW58WLw8rjfAxxOED8Ref4S57xl/3jr9Dir/Lir8njbxji/QBPbjo/ueX55I7/k7u0JwG8JwT5"
                    + "E5LhCQWQEOicEOSZEOKfEEpLCOMlRMgTogwJVAAtxpkW60mL96cl0Gh0Ho0pp7ENNC6AnuhMT/Kk"
                    + "p/jTU2n0NB49XU7PNNCzAYxcZ0a+w5NR6M8opjH4PIZQzhAbGFIAs9SZWe7JrPRnVtOYtTxmvZzZ"
                    + "aGDKAKxmZ1aLJ6vNn9VOYyl4LJWcpTGwdAB2hzO705Pd7c/uobH7eOwBOXvIwNYDOCPOnDFPzoQ/"
                    + "Z4rGMfI4ZjnHauDYAdwZZ+6cJ3fBn7tE467wuGty7oaB6wA83eLydKvX020BT7fTnwL5T8GKp1Dj"
                    + "UzgwcYdL4k6vxF0BibvpiXv4iXsVifuNiU7AZwdcnh30enYo4Nlh+jMX/jOE4hnK+AwNTDriknTU"
                    + "K+l4QNIJetJJfpK7Ium0MckDmHzWJfmcV/L5gOQL9GQvfjJWkezjMCbjgCkXXVIueaVcDki5Qk+5"
                    + "yk+5pki5YUzxAz6/6fL8ltfzOwHP79KfB/CfExTPScbnFGBqoEtqkFdqSEBqKD01jJ8aoUiNMqZS"
                    + "gS9iXF7Eer2ID3iRQH9B579gKl6wjS+4wLREl7Qkr7SUgLRUeloaPy1dkZZpTMsGvsx1eZnv9bIw"
                    + "4GUx/SWf/1KoeCk2vpQC00td0su90isD0qvp6bX89HpFeqMxXQbMaHbJaPHKaAvIaKdnKPgZKkWG"
                    + "xpihA2Z2uGR2emV2B2T20DP7+JkDiswhY6YemDXikjXmlTURkDVFzzLys8yKLKsxyw7MnnHJnvPK"
                    + "XgjIXnLQs1f42WuK7A1jtgOYs8U1ZysmZxs+ZzsjByjIAStzoKYcOCh3h2vuTkzuLnzubkbuHkHu"
                    + "XmXuflOuEyjvgGveQUzeIXzeYUaeiyAPocxDmfLQoPwjrvlHMfnH8fknGPknBfnuyvzTpnwPUMFZ"
                    + "14JzmILz+IILjAIvQQFWWeBjKsCBCi+6Fl7CFF7GF15hFF4VFF5TFt4wFfqBim66Ft3CFN3BF91l"
                    + "FAUIigjKIpKpiAIqDnQtDsIUh+CLQxnFYYLiCGVxlKmYCuLFuPJiMbx4PC+BwaMLeEwlj23icUH8"
                    + "RFd+EoafguenMvhpAn66kp9p4meDBLkOV0E+RlCIFxQzBHyBQKgUiH9LP5Cw1FVYjhFW4oXVDGGt"
                    + "QFivFDaahDKQqNlV1IIRteFF7QyRQiBSKUUak0gHEne4ijsx4m68uIch7hOIB5TiIZNYD5KMuErG"
                    + "MJIJvGSKITEKJGalxGqS2EHSGVfpHEa6gJcuMaQrAumaUrphkjpAJVsQJVuxJdsIJduZJUBhCVhV"
                    + "AjWXwMGlOxClO7Gluwilu5mle4Sle1Wl+82lTuCyA4iyg9iyQ4Syw8wyF2EZQlWGMpehweVHEOVH"
                    + "seXHCeUnmOUnheXuqvLT5nIPcMVZRMU5bMV5QsUFZoWXsALrUFX4mCtw4MqLiMpL2MrLhMorzMqr"
                    + "wsprqsob5ko/cNVNRNUtbNUdQtVdZlWAsIqgqiKZqyjg6kBEdRC2OoRQHcqsDhNWR6iqo8zVVHBN"
                    + "DKImFlsTT6hJYNbQhTVMVQ3bXMMF1yYiapOwtSmE2lRmbZqwNl1Vm2muzQbX5SLq8rF1hYS6YmYd"
                    + "X1gnVNWJzXVScH0por4cW19JqK9m1tcK6+tV9Y3mehm4oRnR0IJtaCM0tDMbFMIGlapBY27QgRs7"
                    + "EI2d2MZuQmMPs7FP2DigahwyN+rBTSOIpjFs0wShaYrZZBQ2mVVNVnOTHSybQcjmsLIFB0G2xJSt"
                    + "CGVrKtmGWeYAv9qCfLXV+9U24qvtrFdA0Suw+hXU8goOad6BbN7p3byL2Lyb1bxH1LxX3bzf0uwE"
                    + "eX0A+fqg9+tDxNeHWa9dRK8R6tcoy2s0pOUIsuWod8txYssJVstJUYu7uuW0pcUD0noW2XrOu/U8"
                    + "sfUCq9VL1IpVt/pYWnGQtovItkvebZeJbVdYbVdFbdfUbTcsbX6QNzeRb255v7lDfHOX9SZA9Iag"
                    + "fkOyvKFA2gOR7UHe7SHE9lBWe5ioPULdHmVpp0LkMUh5rLc8nihPYMnpIjlTLWdb5FyIIhGpSPJW"
                    + "pBAVqSxFmkiRrlZkWhS/lZAyF6nM91YWEpXFLCVfpBSqlWKLUgpRlSJV5d6qSqKqmqWqFanq1apG"
                    + "i0oGUTcj1S3e6jaiup2lVojUKrVaY1HrIJoOpKbTW9NN1PSwNH0izYBaM2TR6CHaEaR2zFs7QdRO"
                    + "sbRGkdas1lotWjtEN4PUzXnrFoi6JZZuRaRbU+s2LDoH5O0W1NutPm+3kd5uZ78Fit+CNW+h1rdw"
                    + "aMcOVMdOn45dpI7d7I494o69mo791g4n6LsDqHcHfd4dIr07zH7nIn6H0LxDWd+hoZ1HUJ1HfTqP"
                    + "kzpPsDtPijvdNZ2nrZ0e0K6zqK5zPl3nSV0X2F1eDnEXVtPlY+3CQbsvorov+XRfJnVfYXdfFXdf"
                    + "03TfsHb7Qd/fRL2/5fP+Dun9Xfb7APF7guY9yfqeAu0JRPUE+fSEkHpC2T1h4p4ITU+UtYcK7Y1B"
                    + "9cb69MaTehPYvXRxL1PTy7b2cqF9iai+JJ++FFJfKrsvTdyXrunLtPZlQ/tzUf35Pv2FpP5idj9f"
                    + "3C/U9Iut/VLoQClqoNxnoJI0UM0eqBUP1GsGGq0DMuhgM2qwxWewjTTYzh5UiAdVmkGNdVAHHepA"
                    + "DXX6DHWThnrYQ33ioQHN0JB1SA8dHkENj/kMT5CGp9jDRvGwWTNstQ7bofoZlH7O4aNfIOmX2PoV"
                    + "sX5No9+w6h3QD1vcPmz1/bCN/GE75wNQ8gGs/QC1fYDDRna4jez0HdlFHtnNGdkjGdmrHdlvG3GC"
                    + "jR5wGz3oO3qIPHqYM+oiGUVoR1G2UTRs7Ijb2FHfsePksROcsZOSMXft2GnbmAds/Kzb+Dnf8fPk"
                    + "8QuccS/JOFY77mMbx8EmLrpNXPKduEyeuMKZuCqZuKaduGGb8INN3nSbvOU7eYc8eZczGSCZJGgn"
                    + "SbZJCmwq0G0qyHcqhDwVypkKk0xFaKeibFNUmCHGzRDra4gnGxI4BrrEwNQa2DYDF2ZMdDMm+RpT"
                    + "yMZUjjFNYkzXGjMdNmM2zJTrZsr3NRWSTcUcE19iEmpNYptJCjOXupnLfc2VZHM1x1wrMddrzY02"
                    + "swxmaXaztPha2siWdo5FIbGotBaNzaKDWTvcrJ2+1m6ytYdj7ZNYB7TWIZtVD7ONuNnGfG0TZNsU"
                    + "x2aU2Mxam9Vms8PsM272OV/7Atm+xLGvSOxrWvuGze6ATW9BT2/FTW+jTG/nTgOl02DdNNQ+DYfP"
                    + "7EDP7MTN7KLM7ObO7JHO7NXN7LfPOMFnD6BnD+JmD1FmD3NnXaSzCN0syj6Lhs8dQc8dxc0dp8yd"
                    + "4M6dlM656+ZO2+c84PNn0fPncPPnKfMXHNx5L+k8VjfvY5/HwRcuohcu4RYuUxaucBeuSheu6RZu"
                    + "2Bf84Is30Yu3cIt3KIt3uYsB0kWCbpFkX6TAlwLRS0G4pRDKUih3KUy6FKFbirIvUeHLMejlWNxy"
                    + "PGU5gbtMly4zdcts+zIXvpKIXknCraRQVlK5K2nSlXTdSqZ9JRu+motezcetFlJWi7mrfOmqULcq"
                    + "tq9K4Wul6LVy3FolZa2au1YrXavXrTXa12Tw9Wb0egtuvY2y3s5dV0jXVbp1jX1dB9/oQG904ja6"
                    + "KRs93I0+6caAbmPIvqGHb46gN8dwmxOUzSnuplG6adZtWu2bdrhjxoF2zOEcCxTHEtexInWs6Rwb"
                    + "dsdHH3300UcfffTRRx999P8S+v9yvP+fn3zi/t+BrTq2UCIAAA=="))));

        ImageReader reader = ImageIO.getImageReadersByFormatName("GIF").next();

        System.out.println(stream);
        reader.setInput(stream, false, true);
        reader.read(0, null);
    }

    public static void main(String[] args) throws IOException {
        GIFLargeTableIndexTest tests = new GIFLargeTableIndexTest();
        tests.testLargeTableGIF();
    }
}
