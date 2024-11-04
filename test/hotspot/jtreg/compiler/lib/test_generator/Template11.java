package compiler.lib.test_generator;

import static compiler.lib.test_generator.TemplateGenerator.performReplacements;
import java.util.HashMap;
import java.util.Map;

import static compiler.lib.test_generator.InputTemplate.*;

public class Template11 extends Template {
    public Template11() {}
    public String getTemplate(String variable){
        String statics= """
                boolean $flag=\\{bool};
                long $lFld=\\{var};
                """;
        String method="""
                long $l1 = \\{val1};
                long $l2 = \\{val2};
                int $zero = \\{val1};
                int $limit = \\{val3};
                int [] iArr=new int[\\{size}];
                for (; $limit < \\{limit1}; $limit \\{arithm}= \\{stride});
                for (int $i = \\{init1}; $i < $limit; $i++) {
                    $zero = 0;
                }
                for (int $i = \\{init2}; $i < \\{limit2}; $i++) {
                    iArr[$i] = \\{val1}; // Just a reason to pre/main/post (trigger more loop opts)
                    if ($flag) { // Triggers Loop Peeling before CCP
                        return;
                    }
                    if ($zero > $i) { // Folded away after CCP.
                        // DivLs add 30 to the loop body count and we hit LoopUnrollLimit. Add more
                        // statements/DivLs if you want to use a higher LoopUnrollLimit value.
                        // After CCP, these statements are folded away and we can unroll this loop.
                        $l1 /= $lFld;
                        $l2 /= $lFld;
                    }
                }
                """;
        String template=statics+method;
        String template_com= avoidConflict(template);
        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(INTEGER_VALUES);
        String val2 = getRandomValueAsString(INTEGER_VALUES);
        String val3 = getRandomValueAsString(INTEGER_VALUES);
        String init1 = getRandomValueAsString(INTEGER_VALUES);
        String limit1 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String init2 = getRandomValueAsString(INTEGER_VALUES);
        String limit2 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String size = getRandomValueAsString(ARRAY_SIZES);
        String stride = getRandomValueAsString(INTEGER_VALUES_NON_ZERO);
        String bool = getRandomValue(new String[]{"true", "false"});
        String arithm = getRandomValue(new String[]{"*", "/"});
        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("size", size);
        replacements.put("limit1", limit1);
        replacements.put("init1", init1);
        replacements.put("init2", init2);
        replacements.put("limit2", limit2);
        replacements.put("bool", bool);
        replacements.put("stride", stride);
        replacements.put("arithm", arithm);
        replacements.put("var", variable);
        return performReplacements(template_com,replacements);
    }
}
