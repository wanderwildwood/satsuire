package protect.card_locker;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import net.lingala.zip4j.io.inputstream.ZipInputStream;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class ZipUtils {
    private static final int MAX_TEXT_CHARS = 10 * 1024 * 1024;

    public static Bitmap readImage(ZipInputStream zipInputStream) {
        return BitmapFactory.decodeStream(zipInputStream);
    }

    public static JSONObject readJSON(ZipInputStream zipInputStream) throws IOException, JSONException {
        return new JSONObject(read(zipInputStream));
    }

    public static String read(ZipInputStream zipInputStream) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        Reader reader = new BufferedReader(new InputStreamReader(zipInputStream, StandardCharsets.UTF_8));
        int c;
        while ((c = reader.read()) != -1) {
            // A pass's JSON is a few kilobytes; anything this long is not one.
            if (stringBuilder.length() >= MAX_TEXT_CHARS) {
                throw new IOException("Entry too large");
            }
            stringBuilder.append((char) c);
        }
        return stringBuilder.toString();
    }
}
