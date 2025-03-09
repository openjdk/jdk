package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate7 extends InputTemplate {
    public InputTemplate7() {}

    @Override
    public CodeSegment getTemplate() {
        String imports= """
                """;

        String statics = """
                //InputTemplate7
                static int x, y;
                static boolean flag;
                """;

        String call = """
                flag=\\{boole};
                test_\\{uniqueId}();
                """;


        String method = """
                 // Use non-const values for assignment of 'a' for more interesting cases
                public static int test_\\{uniqueId}() {
                     int a;
                     if (flag) {
                         a = \\{val1};
                         \\{template1}
                     } else {
                         a = \\{val2};
                     }
                     // y = 34; // Make it more interesting
                     if (a > \\{val2}) {
                         x = \\{val1};
                     } else {
                         x = \\{val3};
                         \\{template2}
                     }
                     return a;
                 }
                """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Template template1 = new Template2();
        Template template2 = new Template10();
        String template_nes1= template1.getTemplate("j");
        String template_nes2= template2.getTemplate("x");
        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(INTEGER_VALUES);
        String val2 = getRandomValueAsString(INTEGER_VALUES);
        String val3 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String boole = getRandomValue(new String[]{"false", "true"});
        String uniqueId = String.valueOf(numTest);
        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("boole", boole);
        replacements.put("template1", template_nes1);
        replacements.put("template2", template_nes2);
        replacements.put("uniqueId", uniqueId);
        return replacements;
    }

    @Override
    public String[] getCompileFlags() {
        return new String[0];
    }

    @Override
    public int getNumberOfTests() {
        return 1;
    }

    @Override
    public int getNumberOfTestMethods() {
        return 1;
    }

}
