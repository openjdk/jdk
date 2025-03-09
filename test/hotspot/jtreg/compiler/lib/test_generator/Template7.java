package compiler.lib.test_generator;

import static compiler.lib.test_generator.TemplateGenerator.performReplacements;
import java.util.HashMap;
import java.util.Map;

import static compiler.lib.test_generator.InputTemplate.*;

public class Template7 extends Template{
    public Template7(){}
    public String getTemplate(String variable){
        String statics= """
                int $iFld=\\{val4}, $iFld2, $iFld3;
                int $x=\\{val1};
                int $y=\\{val2};
                """;
        String method="""
            for (int $i = \\{var}; $i < \\{limit1}; $i++) {
                    // Single iteration loop prevents Parallel IV for outer loop and splitting MulI thru phi
                for (int $j = \\{init}; j < \\{limit2}; $j++) { // (**)
                    $y++;
                }
                 // MulI "23 * (y - 1)" has 4 uses (1-4) outside of the loop (all uses have get_loop() == _ltree_root)
                 // while its get_ctrl() is inside the loop at the loop exit projection of (**). We can therefore sink
                 // MulI on all paths in try_sink_out_of_loop().
                int $toSink = \\{val3} * ($y - 1);
                try {
                    // Usage of 'i' prevents Loop Predication
                    $x = \\{val1} / ($i + $iFld);     // (1)
                    $x = \\{val1} / ($i + 1 + $iFld); // (2)
                } catch (Exception e) {}
                $x = $toSink; // Make sure that MulI is stored in Safepoints of (1) and (2) and has
            }
            $iFld2 = \\{val3} * ($y - 1); // (3)
            $iFld3 = \\{val3} * ($y - 1); // (4

            """;
        String template=statics+method;
        String template_com= avoidConflict(template);
        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(INTEGER_VALUES);
        String val2 = getRandomValueAsString(INTEGER_VALUES);
        String val3 = getRandomValueAsString(INTEGER_VALUES);
        String val4 = getRandomValueAsString(INTEGER_VALUES);
        String init = getRandomValueAsString(INTEGER_VALUES);
        String limit1 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String limit2 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("val4", val4);
        replacements.put("init", init);
        replacements.put("limit1", limit1);
        replacements.put("limit2", limit2);
        replacements.put("var", variable);
        return performReplacements(template_com,replacements);
    }

}
