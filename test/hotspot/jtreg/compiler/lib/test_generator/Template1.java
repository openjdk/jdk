package compiler.lib.test_generator;
import java.util.HashMap;
import java.util.Map;
import static compiler.lib.test_generator.InputTemplate.*;
public class Template1 extends Template {
    public String getTemplate(String c){
        String statics= """
                int $a =\\{val};
                long $b = \\{var};
                """;
        String nes_template="""
            do {
                $a--;
                $b++;
            } while ($a > 0);
            """;
        String template_com=avoid_conflict(reassemble(statics,nes_template),1);

        Map<String, String> replacements = new HashMap<>();
        String val = getRandomValueAsString(integerValues);
        replacements.put("val", val);
        replacements.put("var", c);


        return doReplacements(template_com,replacements);
    }
}
