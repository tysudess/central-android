package br.com.principaiscapas;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NewspaperRepository {
    public static List<NewspaperSource> load(Context context) throws Exception {
        try (InputStream in = context.getAssets().open("covers_newspapers.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            String json = out.toString(StandardCharsets.UTF_8.name());
            JSONArray array = new JSONArray(json);
            List<NewspaperSource> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                NewspaperSource s = new NewspaperSource();
                s.name = o.getString("name");
                s.type = NewspaperSource.Type.valueOf(o.getString("type").toUpperCase());
                s.url = o.optString("url", "");
                JSONArray urls = o.optJSONArray("urls");
                if (urls != null) {
                    for (int k = 0; k < urls.length(); k++) {
                        String u = urls.optString(k, "").trim();
                        if (!u.isEmpty()) s.urls.add(u);
                    }
                }
                if (s.urls.isEmpty() && !s.url.trim().isEmpty()) s.urls.add(s.url);
                s.enabled = o.optBoolean("enabled", true);
                s.followSelector = o.optString("followSelector", "");
                s.followText = o.optString("followText", "");
                s.imageSelector = o.optString("imageSelector", "");
                s.pdfMarginPercent = Math.max(0, Math.min(8, o.optInt("pdfMarginPercent", 0)));
                JSONArray kw = o.optJSONArray("keywords");
                if (kw != null) {
                    for (int k = 0; k < kw.length(); k++) s.keywords.add(kw.getString(k));
                }
                JSONArray mh = o.optJSONArray("mastheads");
                if (mh != null) {
                    for (int k = 0; k < mh.length(); k++) s.mastheads.add(mh.getString(k));
                }
                result.add(s);
            }
            return result;
        }
    }
}
