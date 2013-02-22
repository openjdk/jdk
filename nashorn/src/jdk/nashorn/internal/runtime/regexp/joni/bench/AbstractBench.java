package jdk.nashorn.internal.runtime.regexp.joni.bench;

import jdk.nashorn.internal.runtime.regexp.joni.Option;
import jdk.nashorn.internal.runtime.regexp.joni.Regex;
import jdk.nashorn.internal.runtime.regexp.joni.Syntax;

public abstract class AbstractBench {
    protected void bench(String _reg, String _str, int warmup, int times) throws Exception {
        char[] reg = _reg.toCharArray();
        char[] str = _str.toCharArray();

        Regex p = new Regex(reg,0,reg.length,Option.DEFAULT,Syntax.DEFAULT);

        System.err.println("::: /" + _reg + "/ =~ \"" + _str + "\", " + warmup + " * " + times + " times");

        for(int j=0;j<warmup;j++) {
            long before = System.currentTimeMillis();
            for(int i = 0; i < times; i++) {
                p.matcher(str, 0, str.length).search(0, str.length, Option.NONE);
            }
            long time = System.currentTimeMillis() - before;
            System.err.println(":  " + time + "ms");
        }
    }

    protected void benchBestOf(String _reg, String _str, int warmup, int times) throws Exception {
        char[] reg = _reg.toCharArray();
        char[] str = _str.toCharArray();

        Regex p = new Regex(reg,0,reg.length,Option.DEFAULT,Syntax.DEFAULT);

        System.err.println("::: /" + _reg + "/ =~ \"" + _str + "\", " + warmup + " * " + times + " times");

        long best = Long.MAX_VALUE;

        for(int j=0;j<warmup;j++) {
            long before = System.currentTimeMillis();
            for(int i = 0; i < times; i++) {
                p.matcher(str, 0, str.length).search(0, str.length, Option.NONE);
            }
            long time = System.currentTimeMillis() - before;
            if(time < best) {
                best = time;
            }
            System.err.print(".");
        }
        System.err.println(":  " + best + "ms");
    }
}
