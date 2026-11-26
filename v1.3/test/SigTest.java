import com.zippy.ear.Sig;
import java.nio.charset.StandardCharsets;

public class SigTest {
    public static void main(String[] a) {
        String secret = a[0];
        long t = Long.parseLong(a[1]);
        byte[] body = a[2].getBytes(StandardCharsets.UTF_8);
        System.out.println(Sig.header(secret, t, body));
    }
}
