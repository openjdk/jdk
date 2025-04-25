/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

/*
 * @test
 * @bug 8351113
 * @summary check for negative offset
 * @library /test/lib
 */

import jdk.test.lib.Utils;
import javax.crypto.spec.RC2ParameterSpec;

public class InvalidArrayIndex {

    public static void main(String[] args) throws Exception {
        Utils.runAndCheckException(() -> new RC2ParameterSpec(0, new byte[20],
                Integer.MIN_VALUE), ArrayIndexOutOfBoundsException.class);
    }
}
