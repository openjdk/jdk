package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate14 extends InputTemplate {
    public InputTemplate14() {}

    @Override
    public CodeSegment getTemplate() {
        String template_nes = """
            int a1 = 77;
            int b1 = 0;
            do {
                a1--;
                b1++;
            } while (a1 > 0);
            """;
        String imports= """
                """;

        String statics = """
                static int N = \\{val1};
                    static int iFld;
                    static int[] zeros = new int[N];            
                    static boolean sinkB;
                    static int sinkI1;
                """;

        String call = "for (int i = \\{init1}; i < \\{limit1}; i++) {\n" +
                "            test_\\{uniqueId}();\n" +
                "        }";

        String method = """
                 public static void test() {
                         int xxx = \\{val2};
                         boolean flag = \\{boole};
                         for (; xxx < \\{limit1}; xxx++) {
                             iFld = xxx; // seems to sometimes show 149, bad
                 
                             if (flag) {} // required
                 
                             for (int ddd = \\{init1}; ddd > \\{limit2}; ddd--) {
                                 for (int ccc = xxx; ccc < \\{limit3}; ) {
                                 }
                             }
                 
                             for (int aaa = \\{init2}; aaa < \\{limit4}; aaa++) {
                                 for (long l = \\{init3}; l < \\{limit5}; ++l) {
                                     for (int bbb = \\{init4}; bbb < \\{limit6}; ++bbb) {
                                         try {
                                             bbb = (209 / zeros[xxx]);
                                         } catch (ArithmeticException a_e) {
                                         }
                                     }
                                 }
                             }
                 
                             for (int j = \\{init5}; j < \\{limit6}; j++) {} // empty loop
                         }
                         sinkB = flag;
                         sinkI1 = xxx;
                         System.out.println("iFld = " + iFld);
                     }
                """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements() {
        Map<String, String> replacements = new HashMap<>();

        String init1 = getRandomValueAsString(integerValues);
        String val1 = getRandomValueAsString(integerValues);
        String val2 = getRandomValueAsString(integerValues);
        String init2 = getRandomValueAsString(integerValues);
        String init3 = getRandomValueAsString(integerValues);
        String init4 = getRandomValueAsString(integerValues);
        String init5 = getRandomValueAsString(integerValues);
        String limit1 = getRandomValueAsString(integerValues);
        String limit2 = getRandomValueAsString(integerValues);
        String limit3 = getRandomValueAsString(integerValues);
        String limit4 = getRandomValueAsString(integerValues);
        String limit5 = getRandomValueAsString(integerValues);
        String limit6 = getRandomValueAsString(integerValues);
        //String stride = getRandomValueAsString(integerValuesNonZero);
        String boole = getRandomValue(new String[]{"false", "true"});
        //String thing = getRandomValue(new String[]{"", "synchronized (new Object()) { }"});
        String uniqueId = getUniqueId();

        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("init1", init1);
        replacements.put("init2", init2);
        replacements.put("init3", init3);
        replacements.put("init4", init4);
        replacements.put("init5", init5);
        replacements.put("limit1", limit1);
        replacements.put("limit2", limit2);
        replacements.put("limit3", limit3);
        replacements.put("limit4", limit4);
        replacements.put("limit5", limit5);
        replacements.put("limit6", limit6);
        replacements.put("boole", boole);
        //replacements.put("stride", stride);
        //replacements.put("thing", thing);
        replacements.put("uniqueId", uniqueId);
        return replacements;
    }

    @Override
    public String[] getCompileFlags() {
        return new String[0];
    }

    @Override
    public int getNumberOfTests() {
        return 10;
    }
}
