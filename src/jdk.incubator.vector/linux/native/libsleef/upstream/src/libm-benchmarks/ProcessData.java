import java.util.*;
import java.io.*;

public class ProcessData {
    static final int DP = 64, SP = 32;

    static LinkedHashMap<String, Integer> funcNameOrder = new LinkedHashMap<String, Integer>();

    static class Key {
        final String funcName;

        final int prec, bits;
        final ArrayList<Double> range = new ArrayList<Double>();
        final double ulps;

        Key(String s) {
            String[] a = s.split(",");

            funcName = a[0].trim();
            if (funcNameOrder.get(funcName) == null) {
                funcNameOrder.put(funcName, funcNameOrder.size());
            }

            prec =
                a[1].trim().equals("DP") ? DP :
                a[1].trim().equals("SP") ? SP :
                0;

            bits = Integer.parseInt(a[2].trim());

            int c;

            for(c = 3;;c++) {
                if (a[c].trim().endsWith("ulps")) break;
                range.add(Double.parseDouble(a[c]));
            }

            ulps = Double.parseDouble(a[c].trim().replace("ulps", ""));
        }

        public int hashCode() {
            int h = funcName.hashCode();
            h ^= prec ^ bits;
            return h;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            Key k = (Key) o;
            if (funcName.compareTo(k.funcName) != 0) return false;
            if (prec != k.prec) return false;
            if (bits != k.bits) return false;
            if (range.size() != k.range.size()) return false;
            for(int i=0;i<range.size();i++) {
                if ((double)range.get(i) != (double)k.range.get(i)) return false;
            }

            if (ulps != k.ulps) return false;
            return true;
        }

        public String toString() {
            String s = funcName + " ";
            s += prec == DP ? "DP " : "SP ";
            s += bits + "bit ";
            s += String.format(" %.0fulp ", ulps);
            for(int i=0;i<range.size();i+=2) {
                s += "[" + String.format("%.3g", range.get(i)) + ", " + String.format("%.3g", range.get(i+1)) + "]";
                if (i + 2 < range.size()) s += " ";
            }
            return s;
        }
    }

    static class KeyComparator implements Comparator<Key> {
        public int compare(Key d0, Key d1) {
            if (d0 == d1) return 0;
            if (d0.prec < d1.prec) return  1;
            if (d0.prec > d1.prec) return -1;
            if (d0.ulps > d1.ulps) return  1;
            if (d0.ulps < d1.ulps) return -1;

            int fc = (int)funcNameOrder.get(d0.funcName) - (int)funcNameOrder.get(d1.funcName);
            if (fc != 0) return fc;

            if (d0.bits > d1.bits) return  1;
            if (d0.bits < d1.bits) return -1;

            if (d0.range.size() > d1.range.size()) return  1;
            if (d0.range.size() < d1.range.size()) return -1;

            for(int i=0;i<d0.range.size();i++) {
                if (d0.range.get(i) > d1.range.get(i)) return  1;
                if (d0.range.get(i) < d1.range.get(i)) return -1;
            }

            return 0;
        }
    }

    public static void main(String[] args) throws Exception {
        LinkedHashMap<Key, LinkedHashMap<String, Double>> allData = new LinkedHashMap<Key, LinkedHashMap<String, Double>>();
        TreeSet<Key> allKeys = new TreeSet<Key>(new KeyComparator());
        LinkedHashSet<String> allColumnTitles = new LinkedHashSet<String>();
        double maximum = 0;

        for(int i=0;i<args.length;i++) {
            LineNumberReader lnr = new LineNumberReader(new FileReader(args[i]));

            String columnTitle = lnr.readLine();
            allColumnTitles.add(columnTitle);

            for(;;) {
                String s = lnr.readLine();
                if (s == null) break;

                Key key = new Key(s);
                allKeys.add(key);

                LinkedHashMap<String, Double> v = allData.get(key);
                if (v == null) {
                    v = new LinkedHashMap<String, Double>();
                    allData.put(key, v);
                }
                String[] a = s.split(",");

                double time = Double.parseDouble(a[a.length-1]);
                v.put(columnTitle, time);
                maximum = Math.max(maximum, time);
            }

            lnr.close();
        }

        PrintStream ps = new PrintStream("data.out");

        for(Key k : allKeys) {
            ps.print("\"" + k + "\" ");

            LinkedHashMap<String, Double> v = allData.get(k);

            for(String s : allColumnTitles) {
                Double d = v.get(s);
                if (d != null) ps.print(d);
                if (d == null) ps.print("0");
                ps.print("\t");
            }
            ps.println();
        }

        ps.close();

        ps = new PrintStream("script.out");

        ps.println("set terminal pngcairo size 1280, 800 font \",10\"");
        ps.println("set output \"output.png\"");

        ps.println("color00 = \"#FF5050\";"); // red
        ps.println("color01 = \"#0066FF\";"); // blue
        ps.println("color02 = \"#00FF00\";"); // green
        ps.println("color03 = \"#FF9900\";"); // orange
        ps.println("color04 = \"#CC00CC\";"); // purple
        ps.println("color05 = \"#880000\";"); // brown
        ps.println("color06 = \"#003300\";"); // dark green
        ps.println("color07 = \"#000066\";"); // dark blue

        ps.println("set style data histogram");
        ps.println("set style histogram cluster gap 1");
        ps.println("set style fill solid 1.00");
        ps.println("set boxwidth 0.9");
        ps.println("set xtics format \"\"");
        ps.println("set xtics rotate by -90");
        ps.println("set grid ytics");

        ps.println("set ylabel \"Execution time in micro sec.\"");
        ps.println("set yrange [0:*]");
        ps.println("set bmargin 24");

        ps.println("set title \"Single execution time in micro sec.\"");
        ps.print("plot");

        int i = 0;
        for(String s : allColumnTitles) {
            ps.print("\"data.out\" using " + (i+2) + ":xtic(1) title \"" + s +
                     "\" linecolor rgb color" + String.format("%02d", i));
            if (i != allColumnTitles.size()-1) ps.print(", ");
            i++;
        }
        ps.println();

        ps.close();
    }
}
