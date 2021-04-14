package sae.iit.saedashboard;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

public class PasteAPI {

    private static final String API_URL = "https://api.paste.ee/v1/pastes";

    private static String getJSONAPIKey() {
        return new String(android.util.Base64.decode("dVE4NWZCOVVLanRhSnFBazlKVEExaGVVc3J2QURnZVBIejc5RXhKMlo=", android.util.Base64.DEFAULT));
    }

    private static String getLOGAPIKey() {
        return new String(android.util.Base64.decode("dTBXUXZabUNsdVFkZWJycUlUNjZSRHJoR1paTlVXaXE3U09LTVlPUE8==", android.util.Base64.DEFAULT));
    }

    public interface responseCallback {
        void run(String response);
    }

    public static boolean checkInternetConnection(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
        }
        return false;
    }

    private static void checkConn(HttpsURLConnection conn) throws IOException {
        if (conn.getResponseCode() / 100 != 2) { // 2xx code means success
            StringBuilder response = new StringBuilder();
            BufferedReader _reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            String _line;
            while ((_line = _reader.readLine()) != null) {
                response.append(_line);
            }
            Toaster.showToast(response.toString());
            throw new IOException("Non 2XX response code");
        }
    }

    private static String getResponse(HttpsURLConnection conn) throws IOException {
        checkConn(conn);
        StringBuilder response = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        Log.i("Paste", conn.getResponseCode() + " " + conn.getResponseMessage());
        return response.toString();
    }

    private static class NoPastesUploadedException extends Exception {
        NoPastesUploadedException() {
            super();
        }
    }

    public static void uploadPaste(byte[] data) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            HttpsURLConnection listConn = null;
            try {
                URL url = new URL(API_URL);
                listConn = (HttpsURLConnection) url.openConnection();

                listConn.setDoInput(true);
                listConn.setRequestMethod("POST");
                listConn.setRequestProperty("X-Auth-Token", getLOGAPIKey());
                listConn.setDoOutput(true);
                OutputStream wr = listConn.getOutputStream();
                wr.write(data);
                wr.flush();
                wr.close();

                getResponse(listConn);

                listConn.disconnect();

            } catch (IOException e) {
                Toaster.showToast("Failed to communicate with API");
                e.printStackTrace();
            } finally {
                if (listConn != null)
                    listConn.disconnect();
            }
        });
    }


    public static void getLastJSONPaste(responseCallback responseCallback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            HttpsURLConnection listConn = null;
            HttpsURLConnection getConn = null;
            try {
                URL url = new URL(API_URL);
                listConn = (HttpsURLConnection) url.openConnection();

                listConn.setDoInput(true);
                listConn.setRequestMethod("GET");
                listConn.setRequestProperty("X-Auth-Token", getJSONAPIKey());

                JSONObject jObject = new JSONObject(getResponse(listConn));
                JSONArray jData = jObject.getJSONArray("data");
                if (jData.length() == 0)
                    throw new NoPastesUploadedException();
                JSONObject jPaste = (JSONObject) jData.get(0);
                String ID = jPaste.getString("id");

                listConn.disconnect();

                url = new URL(API_URL + "/" + ID);
                getConn = (HttpsURLConnection) url.openConnection();

                getConn.setDoInput(true);
                getConn.setRequestMethod("GET");
                getConn.setRequestProperty("X-Auth-Token", getJSONAPIKey());

                jObject = new JSONObject(getResponse(getConn));
                jObject = jObject.getJSONObject("paste");
                jData = jObject.getJSONArray("sections");
                jPaste = (JSONObject) jData.get(0);
                String content = jPaste.getString("contents");
                responseCallback.run(content);

            } catch (NoPastesUploadedException e) {
                Toaster.showToast("API showed no pastes");
            } catch (IOException | JSONException e) {
                Toaster.showToast("Failed to communicate with API");
                e.printStackTrace();
            } finally {
                if (listConn != null)
                    listConn.disconnect();
                if (getConn != null)
                    getConn.disconnect();
            }
        });
    }

}
