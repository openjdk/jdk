/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation, and proper error handling, might not be present in
 * this sample code.
 */

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Generates password of desired length. See {@link #usage} method
 * for instructions and command line parameters. This sample shows usages of:
 * <ul>
 * <li>Method references.</li>
 * <li>Lambda and bulk operations. A stream of random integers is mapped to
 * chars, limited by desired length and printed in standard output as password
 * string.</li>
 * </ul>
 *
 */
public class PasswordGenerator {

    private static void usage() {
        System.out.println("Usage: PasswordGenerator LENGTH");
        System.out.println(
                "Password Generator produces password of desired LENGTH.");
    }

    private static final List<Integer> PASSWORD_CHARS = new ArrayList<>();

    //Valid symbols.
    static {
        IntStream.rangeClosed('0', '9').forEach(PASSWORD_CHARS::add);    // 0-9
        IntStream.rangeClosed('A', 'Z').forEach(PASSWORD_CHARS::add);    // A-Z
        IntStream.rangeClosed('a', 'z').forEach(PASSWORD_CHARS::add);    // a-z
    }

    /**
     * The main method for the PasswordGenerator program. Run program with empty
     * argument list to see possible arguments.
     *
     * @param args the argument list for PasswordGenerator.
     */
    public static void main(String[] args) {

        if (args.length != 1) {
            usage();
            return;
        }

        long passwordLength;
        try {
            passwordLength = Long.parseLong(args[0]);
            if (passwordLength < 1) {
                printMessageAndUsage("Length has to be positive");
                return;
            }
        } catch (NumberFormatException ex) {
            printMessageAndUsage("Unexpected number format" + args[0]);
            return;
        }
        /*
         * Stream of random integers is created containing Integer values
         * in range from 0 to PASSWORD_CHARS.size().
         * The stream is limited by passwordLength.
         * Valid chars are selected by generated index.
         */
        new SecureRandom().ints(passwordLength, 0, PASSWORD_CHARS.size())
                .map(PASSWORD_CHARS::get)
                .forEach(i -> System.out.print((char) i));
    }

    private static void printMessageAndUsage(String message) {
        System.err.println(message);
        usage();
    }

}
