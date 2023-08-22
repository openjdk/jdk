// inspired by this code snippet:
// https://github.com/openjdk/jdk/blob/42758cb889a5cf1d7f4c4b468a383b218baa1b27/src/jdk.compiler/share/classes/com/sun/tools/javac/code/Symbol.java#L1318
class GetFieldIsAlias {
    static class Type {
        private int value;
        public GetFieldIsAlias parent;

        public Type(int value, GetFieldIsAlias parent) {
            this.value = value;
            this.parent = parent;
        }
    }

    public Type type;
    public GetFieldIsAlias() {
        this.type = new Type(42, null);
        // 'this.type' is supposed to be the fix pattern: AddP-LoadN-DecodeN-CastPP.
        // We need to ensure this.type is alias with the object just created, or PEA assumes that this.type is 'a global variable'
        // and we have to materialize 'this'.
        this.type.parent = this;
    }

    public static void main(String[] args) {
        for (int i = 0; i< 200_000; ++i) {
            var obj = new GetFieldIsAlias();
            if (obj.type.parent != obj) {
                throw new RuntimeException("wrong answer");
            }
        }
    }
}
