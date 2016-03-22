package java.lang;

public class Double extends Number
{
    public static Double valueOf(double v) {
        return new Double(v);
    }

    public Double(double v) {
        value = v;
    }

    public double doubleValue() {
        return value;
    }

    private double value;
}
