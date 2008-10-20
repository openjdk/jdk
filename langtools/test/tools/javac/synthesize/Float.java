package java.lang;

public class Float extends Number
{
    public static Float valueOf(float v) {
        return new Float(v);
    }

    public Float(float v) {
        value = v;
    }

    public float floatValue() {
        return value;
    }

    private float value;
}
