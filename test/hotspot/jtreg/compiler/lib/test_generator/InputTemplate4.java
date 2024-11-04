package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate4 extends InputTemplate {
    public InputTemplate4() {}

    @Override
    public CodeSegment getTemplate() {

        String imports= """
                """;

        String statics = """
                //InputTemplate4
                public static int foo = \\{fooVar1};
                public static int bar = \\{barVar1};
                """;
        String call = """
                int[] array_\\{uniqueId} = new int[\\{size}];
                test_\\{uniqueId}(array_\\{uniqueId});
                """;

        String method = """
             public static int test_\\{uniqueId}(int[] array) {
                 int result = 0;
                 int[] iArr = new int[\\{size}];
                 for (int i = \\{init1}; i < array.length; i++) {
                     for (int j = \\{init2}; j < i; j++) {
                         if (foo == \\{fooVar2}) {
                             bar = \\{barVar2};
                         }
                         \\{template1}
                         iArr[j] += array[j];
                         result += array[j];
                     }
                     \\{template2}
                 }
                 return result;
             }
             """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Template template1 = new Template1();
        Template template2 = new Template4();
        String template_nes1= template1.getTemplate("i");
        String template_nes2= template2.getTemplate("i");
        Map<String, String> replacements = new HashMap<>();
        String fooVar1 = getRandomValueAsString(INTEGER_VALUES);
        String fooVar2 = getRandomValueAsString(INTEGER_VALUES);
        String barVar1 = getRandomValueAsString(INTEGER_VALUES);
        String size = getRandomValueAsString(ARRAY_SIZES);
        String barVar2 = getRandomValueAsString(INTEGER_VALUES);
        String init1 = getRandomValueAsString(INTEGER_VALUES);
        String init2 = getRandomValueAsString(INTEGER_VALUES);
        String uniqueId = String.valueOf(numTest);;
        replacements.put("fooVar1", fooVar1);
        replacements.put("fooVar2", fooVar2);
        replacements.put("barVar1", barVar1);
        replacements.put("barVar2", barVar2);
        replacements.put("init1", init1);
        replacements.put("init2", init2);
        replacements.put("template1", template_nes1);
        replacements.put("template2", template_nes2);
        replacements.put("size", size);
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
