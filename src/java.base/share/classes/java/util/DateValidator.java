public class DateValidator {
    public static boolean isValidDateOrNot(String Date){
        String regex = "([0-2][0-9]|[3][01])/([0-9]|[1][0-2])/([12].{2}[0-9])";
        return Date.matches(regex);
    }
}
