package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static compiler.lib.test_generator.InputTemplate.*;

public class Template4 extends Template{
    public Template4(){}
    public String getTemplate(List<String> values){
        String statics= """
                MyValue $nonNull = new MyValue(\\{val1});
                MyValue $val = null;
                int $zero = \\{val2};
                """;
        String nes_template="""
                int $limit = \\{val3};
                    for (; $limit < \\{limit1}; $limit \\{arithm}= \\{stride});
                    for (int $i = \\{init}; $i < $limit; i++) {
                        $val = nonNull;
                        $zero = 0;
                    }
                    // 'val' is always non-null here but that's only known after CCP
                    // 'zero' is always 0 here but that's only known after CCP
            """;
        String template_com=avoid_conflict(reassemble(statics,nes_template),2);
        /*StringBuilder res=new StringBuilder();
        int count=0;
        for (int i=0;i<template_com.length();i++) {
            char c=template_com.charAt(i);
            if (c=='%'&&count<values.size()) {
                res.append(values.get(count));
                count++;
            }
            else {
                res.append(c);
            }
        }
         */

        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(integerValues);
        String val2 = getRandomValueAsString(integerValues);
        String val3 = getRandomValueAsString(integerValues);
        String init = getRandomValueAsString(integerValues);
        String limit1 = getRandomValueAsString(integerValues);
        String stride = getRandomValueAsString(integerValuesNonZero);
        String arithm = getRandomValue(new String[]{"*", "/"});

        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("init", init);

        replacements.put("limit1", limit1);
        replacements.put("arithm", arithm);
        replacements.put("stride", stride);


        return doReplacements(template_com,replacements);
    }
}
