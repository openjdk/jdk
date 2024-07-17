package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static compiler.lib.test_generator.InputTemplate.*;

public class Template4 extends Template{
    public Template4(){}
    public String getTemplate(List<String> values){
        String statics= """
                 int x, y;
                 boolean flag=\\{bool};
                """;
        String nes_template="""
                int a;
                
                if (flag) {
                    a = \\{val1};
                } else {
                    a = \\{val2};
                }
                    \s
                // y = 34; // Make it more interesting
                    \s
                if (a > \\{val2}) {
                    x = \\{val3};
                } else {
                    x = \\{val4};
                }
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
        String val4 = getRandomValueAsString(integerValues);

        String bool = getRandomValue(new String[]{"false", "true"});

        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("val4", val4);


        replacements.put("bool", bool);



        return doReplacements(template_com,replacements);
    }
}
