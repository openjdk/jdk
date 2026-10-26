/*
 * @test /nodynamiccopyright/
 * @bug 8375572
 * @summary Functional descriptors can be computed for partially inferred targets
 * @compile MethodRefStuck3.java
 */

class MethodRefStuck3 {
    interface Value<ValueType> {
        interface Factory<Impl extends Value<ValueType>, ValueType> {
            Value<ValueType> create(ValueType value);
        }
    }

    record StringValue(String value, int otherValue) implements Value<String> {
        StringValue(String value) {
            this(value, -1);
        }

        static StringValue create(String value) {
            return new StringValue(value);
        }

        static StringValue create(String value, int otherValue) {
            return new StringValue(value, otherValue);
        }
    }

    interface Entry<Impl extends Value<ValueType>, ValueType> {}

    record EntryImpl<Impl extends Value<ValueType>, ValueType>(
            Class<Impl> implementation, Value.Factory<Impl, ValueType> factory)
            implements Entry<Impl, ValueType> {}

    Entry<StringValue, String> constructorReference() {
        return new EntryImpl<>(StringValue.class, StringValue::new);
    }

    Entry<StringValue, String> methodReference() {
        return new EntryImpl<>(StringValue.class, StringValue::create);
    }

    Entry<StringValue, String> implicitLambda() {
        return new EntryImpl<>(StringValue.class, value -> new StringValue(value));
    }

    Entry<StringValue, String> explicitLambda() {
        return new EntryImpl<>(StringValue.class, (String value) -> new StringValue(value));
    }
}
