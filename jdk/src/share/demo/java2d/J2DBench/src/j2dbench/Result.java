/*
 * Copyright (c) 2002, 2005, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package j2dbench;

import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;
import java.io.PrintWriter;

public class Result {
    public static final int UNITS_WHOLE      = 0;
    public static final int UNITS_THOUSANDS  = 1;
    public static final int UNITS_MILLIONS   = 2;
    public static final int UNITS_AUTO       = 3;

    public static final int SECONDS_WHOLE = 0;
    public static final int SECONDS_MILLIS = 1;
    public static final int SECONDS_MICROS = 2;
    public static final int SECONDS_NANOS  = 3;
    public static final int SECONDS_AUTO  = 4;

    public static int unitScale = UNITS_WHOLE;
    public static int timeScale = SECONDS_WHOLE;
    public static boolean useUnits = true;
    public static boolean invertRate = false;

    String unitname = "unit";
    Test test;
    int repsPerRun;
    int unitsPerRep;
    Vector times;
    Hashtable modifiers;
    Throwable error;

    public Result(Test test) {
        this.test = test;
        this.repsPerRun = 1;
        this.unitsPerRep = 1;
        times = new Vector();
    }

    public void setReps(int reps) {
        this.repsPerRun = reps;
    }

    public void setUnits(int units) {
        this.unitsPerRep = units;
    }

    public void setUnitName(String name) {
        this.unitname = name;
    }

    public void addTime(long time) {
        if (J2DBench.printresults.isEnabled()) {
            System.out.println(test+" took "+time+"ms for "+
                               getRepsPerRun()+" reps");
        }
        times.addElement(new Long(time));
    }

    public void setError(Throwable t) {
        this.error = t;
    }

    public void setModifiers(Hashtable modifiers) {
        this.modifiers = modifiers;
    }

    public Throwable getError() {
        return error;
    }

    public int getRepsPerRun() {
        return repsPerRun;
    }

    public int getUnitsPerRep() {
        return unitsPerRep;
    }

    public long getUnitsPerRun() {
        return ((long) getRepsPerRun()) * ((long) getUnitsPerRep());
    }

    public Hashtable getModifiers() {
        return modifiers;
    }

    public long getNumRuns() {
        return times.size();
    }

    public long getTime(int index) {
        return ((Long) times.elementAt(index)).longValue();
    }

    public double getRepsPerSecond(int index) {
        return (getRepsPerRun() * 1000.0) / getTime(index);
    }

    public double getUnitsPerSecond(int index) {
        return (getUnitsPerRun() * 1000.0) / getTime(index);
    }

    public long getTotalReps() {
        return getRepsPerRun() * getNumRuns();
    }

    public long getTotalUnits() {
        return getUnitsPerRun() * getNumRuns();
    }

    public long getTotalTime() {
        long totalTime = 0;
        for (int i = 0; i < times.size(); i++) {
            totalTime += getTime(i);
        }
        return totalTime;
    }

    public double getAverageRepsPerSecond() {
        return (getTotalReps() * 1000.0) / getTotalTime();
    }

    public double getAverageUnitsPerSecond() {
        return (getTotalUnits() * 1000.0) / getTotalTime();
    }

    public String getAverageString() {
        double units = (useUnits ? getTotalUnits() : getTotalReps());
        double time = getTotalTime();
        if (invertRate) {
            double rate = time / units;
            String prefix = "";
            switch (timeScale) {
            case SECONDS_WHOLE:
                rate /= 1000;
                break;
            case SECONDS_MILLIS:
                prefix = "m";
                break;
            case SECONDS_MICROS:
                rate *= 1000.0;
                prefix = "u";
                break;
            case SECONDS_NANOS:
                rate *= 1000000.0;
                prefix = "n";
                break;
            case SECONDS_AUTO:
                rate /= 1000.0;
                if (rate < 1.0) {
                    rate *= 1000.0;
                    prefix = "m";
                    if (rate < 1.0) {
                        rate *= 1000.0;
                        prefix = "u";
                        if (rate < 1.0) {
                            rate *= 1000.0;
                            prefix = "n";
                        }
                    }
                }
                break;
            }
            return rate+" "+prefix+"secs/"+(useUnits ? unitname : "op");
        } else {
            double rate = units / (time / 1000.0);
            String prefix = "";
            switch (unitScale) {
            case UNITS_WHOLE:
                break;
            case UNITS_THOUSANDS:
                rate /= 1000.0;
                prefix = "K";
                break;
            case UNITS_MILLIONS:
                rate /= 1000000.0;
                prefix = "M";
                break;
            case UNITS_AUTO:
                if (rate > 1000.0) {
                    rate /= 1000.0;
                    prefix = "K";
                    if (rate > 1000.0) {
                        rate /= 1000.0;
                        prefix = "M";
                    }
                }
                break;
            }
            return rate+" "+prefix+(useUnits ? unitname : "op")+"s/sec";
        }
    }

    public void summarize() {
        if (error != null) {
            System.out.println(test+" skipped due to "+error);
            error.printStackTrace(System.out);
        } else {
            System.out.println(test+" averaged "+getAverageString());
        }
        if (true) {
            Enumeration enum_ = modifiers.keys();
            System.out.print("    with");
            String sep = " ";
            while (enum_.hasMoreElements()) {
                Modifier mod = (Modifier) enum_.nextElement();
                Object v = modifiers.get(mod);
                System.out.print(sep);
                System.out.print(mod.getAbbreviatedModifierDescription(v));
                sep = ", ";
            }
            System.out.println();
        }
    }

    public void write(PrintWriter pw) {
        pw.println("  <result "+
                   "num-reps=\""+getRepsPerRun()+"\" "+
                   "num-units=\""+getUnitsPerRep()+"\" "+
                   "name=\""+test.getTreeName()+"\">");
        Enumeration enum_ = modifiers.keys();
        while (enum_.hasMoreElements()) {
            Modifier mod = (Modifier) enum_.nextElement();
            Object v = modifiers.get(mod);
            String val = mod.getModifierValueName(v);
            pw.println("    <option "+
                       "key=\""+mod.getTreeName()+"\" "+
                       "value=\""+val+"\"/>");
        }
        for (int i = 0; i < getNumRuns(); i++) {
            pw.println("    <time value=\""+getTime(i)+"\"/>");
        }
        pw.println("  </result>");
    }
}
