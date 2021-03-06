package sae.iit.saedashboard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.google.zxing.qrcode.QRCodeReader;

import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class JSONQR {
    private final Activity MainActivity;
    private IntentIntegrator scanIntegrator;

    private static SortedMap<Byte, byte[]> qrByteMap = new TreeMap<>();
    private static byte expected = -1;

    static final int REQUEST_VIDEO_CAPTURE = 123;
    Reader reader = new QRCodeReader();

    private QRCallback callback;

    public interface QRCallback {
        void run(String jsonData);
    }

    public JSONQR(Activity MainActivity) {
        this.MainActivity = MainActivity;
        scanIntegrator = new IntentIntegrator(MainActivity);
        scanIntegrator.setPrompt("Scan qrJSON.gif until max");
        scanIntegrator.setBeepEnabled(false);
    }

    public void initiate(QRCallback callback) {
        this.callback = callback;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            clear();
            scanIntegrator.initiateScan(Collections.singletonList(IntentIntegrator.QR_CODE));
        } else {
            clear();
            recordQRGif();
        }
    }

    static byte[] joinArray(Collection<byte[]> arrays) {
        int length = 0;
        for (byte[] array : arrays) {
            length += array.length;
        }

        final byte[] result = new byte[length];

        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }

        return result;
    }

    public String getData() {
        try {
            List<byte[]> data = new ArrayList<>();
            for (Map.Entry<Byte, byte[]> e : qrByteMap.entrySet()) {
                data.add(e.getValue());
            }
            byte[] rawBytes = joinArray(data);
            int size = (int) ByteSplit.getUnsignedInt(rawBytes);
            byte[] rawData = Arrays.copyOfRange(rawBytes, 4, rawBytes.length);

            Inflater decompressor = new Inflater();
            decompressor.setInput(rawData);
            byte[] result = new byte[size + 1];
            int resultLength = decompressor.inflate(result);
            decompressor.end();
            return new String(result, 0, resultLength, StandardCharsets.UTF_8);
        } catch (DataFormatException | BufferUnderflowException e) {
            e.printStackTrace();
            Toaster.showToast("Failed to decode QR data", Toaster.STATUS.ERROR);
        }
        return "";
    }

    private void recordQRGif() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        if (takeVideoIntent.resolveActivity(MainActivity.getPackageManager()) != null) {
            MainActivity.startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
        }
    }

    private void clear() {
        qrByteMap.clear();
        expected = -1;
    }

    private byte currentByte = -1;

    private boolean ingestQRResult(byte[] ISO_8859_1_Bytes) {
        currentByte = -1;
        if (ISO_8859_1_Bytes == null)
            return false;
        byte[] bytes = android.util.Base64.decode(ISO_8859_1_Bytes, android.util.Base64.DEFAULT);
        currentByte = bytes[1];
        byte[] raw_bytes = Arrays.copyOfRange(bytes, 2, bytes.length);

        expected = bytes[0];
        qrByteMap.put(bytes[1], raw_bytes);

        byte last = -1;
        Set<Byte> keys = qrByteMap.keySet();
        for (byte b : keys) {
            if (last == -1) {
                if (b != 0) { // Numbering must start at 0
                    break;
                }
            } else if (b - last != 1) {
                break;
            }
            last = b;
        }
        if (last == expected)
            Toaster.showToast("Done with QR", false, true, Toaster.STATUS.SUCCESS);
        return last == expected;
    }

    private byte[] decodeQRImage(Bitmap bMap) {
        byte[] decoded = null;
        reader.reset();

        int[] intArray = new int[bMap.getWidth() * bMap.getHeight()];
        bMap.getPixels(intArray, 0, bMap.getWidth(), 0, 0, bMap.getWidth(), bMap.getHeight());
        LuminanceSource source = new RGBLuminanceSource(bMap.getWidth(), bMap.getHeight(), intArray);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        try {
            Result result = reader.decode(bitmap);
            decoded = result.getText().getBytes(StandardCharsets.ISO_8859_1);
        } catch (NotFoundException | ChecksumException | FormatException ignored) {
        }
        return decoded;
    }

//    private static Bitmap scaleBitMap(Bitmap realImage, float maxImageSize, boolean filter) {
//        float ratio = Math.min(
//                (float) maxImageSize / realImage.getWidth(),
//                (float) maxImageSize / realImage.getHeight());
//        int width = Math.round((float) ratio * realImage.getWidth());
//        int height = Math.round((float) ratio * realImage.getHeight());
//
//        return Bitmap.createScaledBitmap(realImage, width, height, filter);
//    }

    private void processVideo(Uri videoUri) {
        MediaMetadataRetriever mediaMetadata = new MediaMetadataRetriever();
        mediaMetadata.setDataSource(MainActivity.getApplicationContext(), videoUri);

        Bitmap frame;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            long duration = Long.parseLong(mediaMetadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)); // ms
            double frameRate = 30.0;
            String frameR = mediaMetadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
            if (frameR != null)
                frameRate = Double.parseDouble(frameR);
            long totalFrames = (long) (frameRate * duration / 1000);
            for (int currentFrame = 0; currentFrame < totalFrames; currentFrame++) {
                double micro = (((double) currentFrame / totalFrames) * (duration * 1000));
                frame = mediaMetadata.getFrameAtTime((long) micro, MediaMetadataRetriever.OPTION_CLOSEST);
                if (frame != null) {
//                    frame = scaleBitMap(frame, 500, true);
                    byte[] decoded = decodeQRImage(frame);
                    if (ingestQRResult(decoded)) {
                        break;
                    }
                }
            }
        } else {
            for (int currentFrame = 0; currentFrame < Integer.parseInt(mediaMetadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)); currentFrame++) {
                frame = mediaMetadata.getFrameAtIndex(currentFrame);
                byte[] decoded = decodeQRImage(frame);
                if (ingestQRResult(decoded)) {
                    break;
                }
            }
        }

        if (callback != null)
            callback.run(getData());
    }

    private void processIntentResult(IntentResult scanningResult) {
        if (scanningResult == null || scanningResult.getRawBytes() == null) {
            Toaster.showToast("JSON QR Cancelled", Toaster.STATUS.WARNING);
            return;
        }
        byte[] content = scanningResult.getContents().getBytes(StandardCharsets.ISO_8859_1);

        Set<Byte> keys = qrByteMap.keySet();
        StringBuilder dataMap = new StringBuilder();
        dataMap.append("Data:");
        for (byte i = 0; i <= expected; i++) {
            if (i == currentByte)
                dataMap.append('█');
            else if (keys.contains(i))
                dataMap.append('■');
            else
                dataMap.append(" ").append(i).append(" ");
        }

        Toaster.showToast(dataMap.toString(), false, true, Toaster.STATUS.INFO);
        if (!ingestQRResult(content))
            scanIntegrator.initiateScan(Collections.singletonList(IntentIntegrator.QR_CODE));
        else if (callback != null)
            callback.run(getData());
    }

    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == REQUEST_VIDEO_CAPTURE && resultCode == -1)
            processVideo(resultData.getData());
        if (requestCode == IntentIntegrator.REQUEST_CODE)
            processIntentResult(IntentIntegrator.parseActivityResult(requestCode, resultCode, resultData));

    }
}
