/*
 * @test
 * @bug 8173585
 * @summary Test intrinsification of StringLatin1.indexOf(char). Note that
 * differing code paths are taken contingent upon the length of the input String.
 * Hence we must test against differing string lengths in order to validate
 * correct functionality. We also ensure the strings are long enough to trigger
 * the looping conditions of the individual code paths.
 *
 * Run with varing levels of AVX and SSE support, also without the intrinsic at all
 *
 * @library /compiler/patches /test/lib
 * @run main/othervm -Xbatch -XX:Tier4InvocationThreshold=200 -XX:CompileThreshold=100 compiler.intrinsics.string.TestStringLatin1IndexOfChar
 * @run main/othervm -Xbatch -XX:Tier4InvocationThreshold=200 -XX:CompileThreshold=100 -XX:+UnlockDiagnosticVMOptions -XX:DisableIntrinsic=_indexOfL_char compiler.intrinsics.string.TestStringLatin1IndexOfChar
 * @run main/othervm -Xbatch -XX:Tier4InvocationThreshold=200 -XX:CompileThreshold=100 -XX:UseSSE=0 compiler.intrinsics.string.TestStringLatin1IndexOfChar
 * @run main/othervm -Xbatch -XX:Tier4InvocationThreshold=200 -XX:CompileThreshold=100 -XX:UseAVX=1 compiler.intrinsics.string.TestStringLatin1IndexOfChar
 * @run main/othervm -Xbatch -XX:Tier4InvocationThreshold=200 -XX:CompileThreshold=100 -XX:UseAVX=2 compiler.intrinsics.string.TestStringLatin1IndexOfChar
 * @run main/othervm -Xbatch -XX:Tier4InvocationThreshold=200 -XX:CompileThreshold=100 -XX:UseAVX=3 compiler.intrinsics.string.TestStringLatin1IndexOfChar
 */

package compiler.intrinsics.string;

import jdk.test.lib.Asserts;

public class TestStringLatin1IndexOfChar{
    private final static int MAX_LENGTH = 2048;//future proof for AVX-512 instructions

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 1_000; ++i) {//repeat such that we enter into C2 code...
            maintest();
            testEmpty();
        }
    }

    private static void testEmpty(){
        Asserts.assertEQ("".indexOf('a'), -1);
    }

    private final static char SEARCH_CHAR = 'z';
    private final static char INVERLEAVING_CHAR = 'a';
    private final static char MISSING_CHAR = 'd';

    private static void maintest(){
        //progressivly move through string checking indexes and starting offset correctly processed
        //string is of form azaza, aazaazaa, aaazaaazaaa, etc
        //we find n s.t. maxlength = (n*3) + 2
        int maxaInstances = (MAX_LENGTH-2)/3;

        for(int aInstances = 5; aInstances < MAX_LENGTH; aInstances++){
            String totest = makeCandidateStringLatin1(aInstances);

            int startoffset;
            {
                int intri = totest.indexOf(SEARCH_CHAR);
                int nonintri = indexOfCharNonIntrinsic(totest, SEARCH_CHAR, 0);

                Asserts.assertEQ(intri, nonintri);
                startoffset = intri+1;
            }

            {
                int intri = totest.indexOf(SEARCH_CHAR, startoffset);
                int nonintri = indexOfCharNonIntrinsic(totest, SEARCH_CHAR, startoffset);

                Asserts.assertEQ(intri, nonintri);
                startoffset = intri+1;
            }

            Asserts.assertEQ(totest.indexOf(SEARCH_CHAR, startoffset), -1);//only two SEARCH_CHAR per string
            Asserts.assertEQ(totest.indexOf(MISSING_CHAR), -1);
        }
    }

    private static String makeCandidateStringLatin1(int aInstances){
        StringBuilder sb = new StringBuilder((aInstances*3) + 2);
        for(int n =0; n < aInstances; n++){
            sb.append(INVERLEAVING_CHAR);
        }

        sb.append(SEARCH_CHAR);

        for(int n =0; n < aInstances; n++){
            sb.append(INVERLEAVING_CHAR);
        }

        sb.append(SEARCH_CHAR);

        for(int n =0; n < aInstances; n++){
            sb.append(INVERLEAVING_CHAR);
        }
        return sb.toString();
    }

    private static int indexOfCharNonIntrinsic(String value, int ch, int fromIndex) {
        //non intrinsic version of indexOfChar
        byte c = (byte)ch;
        for (int i = fromIndex; i < value.length(); i++) {
            if (value.charAt(i) == c) {
               return i;
            }
        }
        return -1;
    }
}
