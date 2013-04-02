package jdk.nashorn.internal.runtime.regexp.joni.bench;

public class BenchSeveralRegexps extends AbstractBench {
    public static void main(String[] args) throws Exception {
        int BASE = 1000000;

        new BenchSeveralRegexps().benchBestOf("a"," a",10,4*BASE);

        new BenchSeveralRegexps().benchBestOf(".*?=","_petstore_session_id=1b341ffe23b5298676d535fcabd3d0d7; path=/",10,BASE);

        new BenchSeveralRegexps().benchBestOf("^(.*?)=(.*?);","_petstore_session_id=1b341ffe23b5298676d535fcabd3d0d7; path=/",10,BASE);

        new BenchSeveralRegexps().benchBestOf(".*_p","_petstore_session_id=1b341ffe23b5298676d535fcabd3d0d7; path=/",10,4*BASE);

        new BenchSeveralRegexps().benchBestOf(".*=","_petstore_session_id=1b341ffe23b5298676d535fcabd3d0d7; path=/",10,4*BASE);
    }
}
