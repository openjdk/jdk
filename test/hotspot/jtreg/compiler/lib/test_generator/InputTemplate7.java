package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate7 extends InputTemplate {
    public InputTemplate7() {}

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
                static int x, y;
                static boolean flag;
                """;

        String call = "flag=\\{boole};\n"+
                "     test_\\{uniqueId}();\n";


        String method = """
                 // Use non-const values for assignment of 'a' for more interesting cases
                public static int test() {
                     int a;
                     if (flag) {
                         a = \\{val1};
                     } else {
                         a = \\{val2};
                     }
                     // y = 34; // Make it more interesting
                     if (a > \\{val2}) {
                         x = \\{val1};
                     } else {
                         x = \\{val3};
                     }
                     return a;
                 }
                """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Map<String, String> replacements = new HashMap<>();

        String val1 = getRandomValueAsString(integerValues);
        String val2 = getRandomValueAsString(integerValues);
        String val3 = getRandomValueAsString(integerValues);
        //String stride = getRandomValueAsString(integerValuesNonZero);
        String boole = getRandomValue(new String[]{"false", "true"});
        //String thing = getRandomValue(new String[]{"", "synchronized (new Object()) { }"});
        String uniqueId = getUniqueId();

        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
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

    @Override
    public int getNumberOfTestMethods() {
        return 4;
    }

}
