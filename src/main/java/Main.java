import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {
        Pattern pattern = Pattern.compile("number\\((\\d*[.]\\d|\\d+)\\)");
        Matcher matcher = pattern.matcher("number(123)");
        System.out.println(pattern.matcher("number(123").group(1));
    }
}
