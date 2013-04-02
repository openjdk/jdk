package jdk.nashorn.internal.runtime.regexp.joni.bench;

public class BenchRailsRegs extends AbstractBench {
    public static void main(String[] args) throws Exception {
        final String[][] regexps = {{"a.*?[b-z]{2,4}aaaaaa","afdgdsgderaabxxaaaaaaaaaaaaaaaaaaaaaaaa"},
                                    {"://","/shop/viewCategory.shtml?category=DOGS"},
                                    {"^\\w+\\://[^/]+(/.*|$)$","/shop/viewCategory.shtml?category=DOGS"},
                                    {"\\A/?\\Z","/shop/viewCategory.shtml"},
                                    {"\\A/shop/signonForm\\.shtml/?\\Z","/shop/viewCategory.shtml"},
                                    {"\\A/shop/newAccountForm\\.shtml/?\\Z","/shop/viewCategory.shtml"},
                                    {"\\A/shop/newAccount\\.shtml/?\\Z","/shop/viewCategory.shtml"},
                                    {"\\A/shop/viewCart\\.shtml/?\\Z","/shop/viewCategory.shtml"},
                                    {"\\A/shop/index\\.shtml/?\\Z","/shop/viewCategory.shtml"},
                                    {"\\A/shop/viewCategory\\.shtml/?\\Z","/shop/viewCategory.shtml"},
                                    {"\\A(?:::)?([A-Z]\\w*(?:::[A-Z]\\w*)*)\\z","CategoriesController"},
                                    {"\\Ainsert","SELECT * FROM sessions WHERE (session_id = '1b341ffe23b5298676d535fcabd3d0d7')  LIMIT 1"},
                                    {"\\A\\(?\\s*(select|show)","SELECT * FROM sessions WHERE (session_id = '1b341ffe23b5298676d535fcabd3d0d7')  LIMIT 1"},
                                    {".*?\n","1b341ffe23b5298676d535fcabd3d0d7"},
                                    {"^find_(all_by|by)_([_a-zA-Z]\\w*)$","find_by_string_id"},
                                    {"\\.rjs$","categories/show.rhtml"},
                                    {"^[-a-z]+://","petstore.css"},
                                    {"^get$",""},
                                    {"^post$",""},
                                    {"^[^:]+","www.example.com"},
                                    {"(=|\\?|_before_type_cast)$", "updated_on"},
                                    {"^(.*?)=(.*?);","_petstore_session_id=1b341ffe23b5298676d535fcabd3d0d7; path=/"}};
        for(String[] reg : regexps) {
            new BenchRailsRegs().benchBestOf(reg[0],reg[1],10,1000000);
        }
    }
}
