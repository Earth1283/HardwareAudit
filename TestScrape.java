
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class TestScrape {
    public static void main(String[] args) {
        try {
            // Try to search for a common CPU
            URL url = new URL("https://www.cpubenchmark.net/cpu_list.php");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

            System.out.println("Response Code: " + con.getResponseCode());
            if (con.getResponseCode() == 200) {
                System.out.println("Success! We can read the list.");
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println(inputLine);
                }
                in.close();
            } else {
                System.out.println("Failed: " + con.getResponseMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
