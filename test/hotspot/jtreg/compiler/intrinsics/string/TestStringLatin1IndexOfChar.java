/*
 * @test
 * @bug 8173585
 * @summary Test intrinsification of StringLatin1.indexOf(char). Note that
 * differing code paths are taken contingent upon the length of the input String.
 * Hence we must test against differing string lengths in order to validate
 * correct functionality
 *
 * @library /compiler/patches /test/lib
 * @run main/othervm -Xbatch -XX:CompileThreshold=100 -XX:+UnlockDiagnosticVMOptions -XX:DisableIntrinsic=_indexOfL_char compiler.intrinsics.string.TestStringLatin1IndexOfChar
 * @run main/othervm -Xbatch -XX:CompileThreshold=100 compiler.intrinsics.string.TestStringLatin1IndexOfChar
 */

package compiler.intrinsics.string;

import jdk.test.lib.Asserts;
import java.util.HashMap;
import java.util.Map;

public class TestStringLatin1IndexOfChar{
    private final static int MAX_LENGTH = 513;//future proof for AVX-512 instructions

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 100_0; ++i) {//repeat such that we enter into C2 code...
            maintest();
            testEmpty();
        }
    }

    private static void testEmpty(){
        Asserts.assertEQ("".indexOf('a'), -1);
    }

    private static final char[] alphabet = new char[]{'a', 'b', 'c'};//Latin1 are made of these

    private static void maintest(){
        //progressivly move through string checking indexes and starting offset correctly processed
        for(int strLength = alphabet.length; strLength < MAX_LENGTH; strLength++){
            String totest = makeCandidateStringLatin1(strLength);

            //track starting offset here
            Map<Character, Integer> lastIndexOf = new HashMap<Character, Integer>();
            for(Character c : alphabet){
                lastIndexOf.put(c, 0);
            }

            for(int alphaidx = 0; ; alphaidx++){
                char wanted = alphabet[alphaidx % alphabet.length];
                int lastInst = lastIndexOf.get(wanted);

                int intri = totest.indexOf(wanted, lastInst);
                int nonintri = indexOfChar(totest, wanted, lastInst);

                Asserts.assertEQ(intri, nonintri);
                if(intri == -1 || intri == strLength-1){
                    break;
                }
                lastIndexOf.put(wanted, intri+1);
            }

            Asserts.assertEQ(totest.indexOf('d'), -1);
        }
    }

    private static String makeCandidateStringLatin1(int strLength){
        StringBuilder sb = new StringBuilder(strLength);
        for(int n =0; n < strLength; n++){//only 1 byte elements...
            sb.append(alphabet[n % alphabet.length]);
        }
        return sb.toString();
    }

    private static int indexOfChar(String value, int ch, int fromIndex) {
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