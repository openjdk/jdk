/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
import jdk.test.lib.cli.CommandLineOptionTest;
import predicate.AESSupportPredicate;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

public abstract class AESIntrinsicsBase extends CommandLineOptionTest {
    public static final BooleanSupplier AES_SUPPORTED_PREDICATE
            = new AESSupportPredicate();
    public static final String CIPHER_INTRINSIC = "com\\.sun\\.crypto\\"
            + ".provider\\.CipherBlockChaining::"
            + "(implEncrypt|implDecrypt) \\([0-9]+ bytes\\)\\s+\\(intrinsic[,\\)]";
    public static final String AES_INTRINSIC = "com\\.sun\\.crypto\\"
            + ".provider\\.AESCrypt::(implEncryptBlock|implDecryptBlock) \\([0-9]+ "
            + "bytes\\)\\s+\\(intrinsic[,\\)]";
    public static final String USE_AES = "UseAES";
    public static final String USE_AES_INTRINSICS = "UseAESIntrinsics";
    public static final String USE_SSE = "UseSSE";
    public static final String USE_VIS = "UseVIS";
    public static final String[] TEST_AES_CMD
            = {"-XX:+IgnoreUnrecognizedVMOptions", "-XX:+PrintFlagsFinal",
            "-Xbatch","-XX:+UnlockDiagnosticVMOptions",
            "-XX:+PrintIntrinsics", "-DcheckOutput=true", "-Dmode=CBC",
            "TestAESMain"};

    protected AESIntrinsicsBase(BooleanSupplier predicate) {
        super(predicate);
    }

    /**
     * Prepares command for TestAESMain execution.
     * @param args flags that must be added to command
     * @return command for TestAESMain execution
     */
    public static String[] prepareArguments(String... args) {
        String[] command = Arrays.copyOf(args, TEST_AES_CMD.length
                + args.length);
        System.arraycopy(TEST_AES_CMD, 0, command, args.length,
                TEST_AES_CMD.length);
        return command;
    }
}
