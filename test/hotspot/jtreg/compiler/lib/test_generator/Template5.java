package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

import static compiler.lib.test_generator.InputTemplate.*;

public class Template5 extends Template {
    public Template5() {}
    public String getTemplate(String c){
        String statics= """
                class MyValue{
                    int nonNull;
                    public MyValue(int $nonNull) {}
                }
                MyValue nonNull = new MyValue(\\{val1});
                MyValue $val = null;
                int $zero = \\{val2};
                
                """;
        String nes_template="""
            int $limit = \\{val3};
            for (; $limit < 4; $limit *= 2);
            for (int $i = \\{var}; $i < $limit; $i++) {
                $val = $nonNull;
                $zero = 0;
            }            
            """;
        String template_com=avoid_conflict(reassemble(statics,nes_template),2);

        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(integerValues);
        String val2 = getRandomValueAsString(integerValues);
        String val3 = getRandomValueAsString(integerValues);


        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("var", c);

        return doReplacements(template_com,replacements);
    }
}
