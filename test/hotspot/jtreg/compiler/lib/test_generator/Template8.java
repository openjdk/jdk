package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

import static compiler.lib.test_generator.InputTemplate.*;

public class Template8 extends Template{
    public Template8(){}
    public String getTemplate(String c){
        String statics= """
                int $iFld=\\{val3}, $iFld2;
                boolean $flag=\\{bool};
                int $zero = \\{val1};
                int $limit = \\{val2};             
                """;
        String nes_template="""
                for (; $limit < \\{limit1}; $limit \\{arithm}= \\{stride});
                    for (int $i = \\{var}; $i < $limit; $i++) {
                        $zero = 0;
                    }
                \s
                    for (int $i = \\{var}; $i < \\{limit2}; $i++) {
                        if ($flag) { // 1) Triggers Loop Peeling
                            $iFld = $zero;
                        }
                \s
                        int $k = $iFld + $i * $zero; // Loop variant before CCP
                \s
                        if ($k == \\{val1}) { // 2) After CCP: Loop invariant -> triggers Loop Unswitching             \s
                            $iFld2 = \\{val1};
                        }
                    }            
                    """;
        String template_com=avoid_conflict(reassemble(statics,nes_template),1);

        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(integerValues);
        String val2 = getRandomValueAsString(integerValues);
        String val3 = getRandomValueAsString(integerValues);
        String limit1 = getRandomValueAsString(integerValues);
        String limit2 = getRandomValueAsString(integerValues);
        String stride = getRandomValueAsString(integerValuesNonZero);
        String arithm = getRandomValue(new String[]{"+", "-"});
        String bool = getRandomValue(new String[]{"false", "true"});
        replacements.put("val1", val1);
        replacements.put("val3", val3);
        replacements.put("stride", stride);
        replacements.put("arithm", arithm);
        replacements.put("bool", bool);
        replacements.put("val2", val2);
        replacements.put("limit1", limit1);
        replacements.put("limit2", limit2);
        replacements.put("var", c);
        return doReplacements(template_com,replacements);
    }
}
