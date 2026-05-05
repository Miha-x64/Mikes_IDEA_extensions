import okhttp3.OkHttpClient;

public class TestTest {
    public static void main(String[] args) {
        var client = new OkHttpClient(); // uncachedAlloc is skipped in tests
    }
}
