package sae.iit.saedashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class DataLogTab extends Fragment implements SideControlSize {
    private Button showButton;
    private Button upButton;
    private Button downButton;
    private Button deleteButton;
    private Button exportButton;
    private Button updateButton;
    private Button minBtn;
    private Button plsBtn;
    private ScrollView fileListScroller, logScroller;
    private LinearLayout fileLayout, SidePanelLayout;
    private Runnable confirm_run;
    private AlertDialog confirm_dialog;
    private TextView confirm_text;
    private LogFileIO loggingIO;
    private Activity activity;
    private final ArrayList<Pair<LogFileIO.LogFile, TextView>> fileList = new ArrayList<>();
    private int selectedFile = -1;
    private TextView LogViewer;
    private final Timer updateTimer = new Timer();
    private Thread colorThread;
    private ProgressBar logWait;
    private final float[] viewTextSize = {12};

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.data_tab, container, false);
        LogViewer = rootView.findViewById(R.id.LogViewer);
        fileLayout = rootView.findViewById(R.id.FileListLayout);
        fileListScroller = rootView.findViewById(R.id.FileListScroller);
        logScroller = rootView.findViewById(R.id.logScroller);
        showButton = rootView.findViewById(R.id.showButton);
        upButton = rootView.findViewById(R.id.upButton);
        downButton = rootView.findViewById(R.id.downButton);
        deleteButton = rootView.findViewById(R.id.deleteButton);
        exportButton = rootView.findViewById(R.id.exportButton);
        updateButton = rootView.findViewById(R.id.updateButton);
        SidePanelLayout = rootView.findViewById(R.id.SidePanelLayout);
        minBtn = rootView.findViewById(R.id.minBtn);
        plsBtn = rootView.findViewById(R.id.plsBtn);
        logWait = rootView.findViewById(R.id.logWait);
        showButton.setOnClickListener(v -> onClickShowFile());
        upButton.setOnClickListener(v -> onClickUp());
        upButton.setOnLongClickListener(this::onLongClickUp);
        downButton.setOnClickListener(v -> onClickDown());
        downButton.setOnLongClickListener(this::onLongClickDown);
        deleteButton.setOnClickListener(v -> onClickDelete());
        deleteButton.setOnLongClickListener(v -> onLongClickDelete());
        exportButton.setOnClickListener(v -> onClickExport());
        updateButton.setOnClickListener(v -> updateFiles());
        minBtn.setOnClickListener(v -> {
            if (logScroller.getVisibility() != View.GONE) {
                viewTextSize[0] = viewTextSize[0] - 1;
                LogViewer.setTextSize(viewTextSize[0]);
            }
        });
        plsBtn.setOnClickListener(v -> {
            if (logScroller.getVisibility() != View.GONE) {
                viewTextSize[0] = viewTextSize[0] + 1;
                LogViewer.setTextSize(viewTextSize[0]);
            }
        });

        logScroller.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY % LogViewer.getLineHeight() <= 1) {
                showLine();
            }
        });
        return rootView;
    }

    private void onClickExport() {
        if (selectedFile < 0 || selectedFile >= fileList.size()) {
            Toaster.showToast("No file selected");
            return;
        }
//        loggingIO.export(TeensyStream.interpretLogFile(fileList.get(selectedFile).first));
        PasteAPI.uploadPaste(TeensyStream.stringifyLogFile(fileList.get(selectedFile).first));
    }

    private void createConfirmDialog() {
        AlertDialog.Builder mBuilder = new AlertDialog.Builder(activity);
        View mView = activity.getLayoutInflater().inflate(R.layout.confirm_delete_dialog, null);

        Button yes = mView.findViewById(R.id.Yesbtn);
        Button no = mView.findViewById(R.id.Nobtn);
        confirm_text = mView.findViewById(R.id.confirmText);

        mBuilder.setView(mView);
        confirm_dialog = mBuilder.create();

        yes.setOnClickListener(v -> Toaster.showToast("Hold to confirm", Toaster.STATUS.INFO));
        yes.setOnLongClickListener(v -> {
            confirm_run.run();
            confirm_dialog.dismiss();
            return true;
        });

        no.setOnClickListener(v -> confirm_dialog.dismiss());

    }

    public void onClickShowFile() {
        if (colorThread != null && colorThread.isAlive()) {
            Toaster.showToast("Must wait for previous query", Toaster.STATUS.WARNING);
            return;
        }
        if (logScroller.getVisibility() != View.GONE) {
            activity.runOnUiThread(() -> {
                showButton.setText(R.string.show);
                LogViewer.setText("");
                SimpleAnim.animView(activity, logScroller, View.GONE, "fade");
            });
        } else {
            if (selectedFile >= 0 && selectedFile < fileList.size()) {
                showFile(fileList.get(selectedFile).first);
                activity.runOnUiThread(() -> {
                    SimpleAnim.animView(activity, logScroller, View.VISIBLE, "fade");
                    showButton.setText(R.string.hide);
                });
            } else {
                Toaster.showToast("No file selected");
            }
        }
    }

    public void onClickUp() {
        if (logScroller.getVisibility() != View.GONE) {
            logScroller.smoothScrollBy(0, (int) (LogViewer.getHeight() / -8d));
            return;
        }
        selectFile(selectedFile - 1);
    }

    public void onClickDown() {
        if (logScroller.getVisibility() != View.GONE) {
            logScroller.smoothScrollBy(0, (int) (LogViewer.getHeight() / 8d));
            return;
        }
        selectFile(selectedFile + 1);
    }

    TimerTask updateLineTask;

    private void setLineTask(boolean direction, Button check) {
        if (updateLineTask != null)
            updateLineTask.cancel();
        updateTimer.purge();
        if (check == null)
            return;
        if (logScroller.getVisibility() != View.GONE) {
            updateLineTask = new TimerTask() {
                @Override
                public void run() {
                    if (check.isPressed()) {
                        activity.runOnUiThread(() -> logScroller.smoothScrollBy(0, (int) (LogViewer.getHeight() / 16d) * (direction ? 1 : -1)));
                    } else {
                        setLineTask(direction, null);
                    }
                }
            };
            updateTimer.schedule(updateLineTask, 0, 50);
        }
    }

    public boolean onLongClickDown(View v) {
        setLineTask(true, (Button) v);
        return false;
    }

    public boolean onLongClickUp(View v) {
        setLineTask(false, (Button) v);
        return false;
    }

    public void onClickDelete() {
        if (logScroller.getVisibility() != View.GONE) {
            Toaster.showToast("Can't delete while viewing log", Toaster.STATUS.WARNING);
            return;
        }
        if (selectedFile < 0 || selectedFile >= fileList.size()) {
            Toaster.showToast("No file selected");
            return;
        }
        confirm(this::deleteSelected, "Delete File?");
    }

    public boolean onLongClickDelete() {
        if (logScroller.getVisibility() != View.GONE) {
            Toaster.showToast("Can't delete while viewing log", Toaster.STATUS.WARNING);
            return true;
        }
        confirm(this::deleteAll, "Delete All Files?");
        return true;
    }

    private void deleteSelected() {
        Pair<LogFileIO.LogFile, TextView> p = fileList.get(selectedFile);
        if (p.first.delete()) {
            fileList.remove(selectedFile);
            if (fileLayout != null)
                fileLayout.removeView(p.second);
            onClickDown();
        } else {
            Toaster.showToast("Failed to delete file", Toaster.STATUS.ERROR);
            updateFiles();
        }
    }

    private void deleteAll() {
        for (Pair<LogFileIO.LogFile, TextView> p : fileList) {
            if (!p.first.delete())
                Log.w("Data", "Failed to delete file" + p.first.getName());
            else if (fileLayout != null)
                fileLayout.removeView(p.second);
        }
        fileList.clear();
        updateFiles();
    }

    private void confirm(Runnable run, String confirmText) {
        confirm_run = run;
        if (confirmText == null) {
            createConfirmDialog();
        }
        if (!confirm_dialog.isShowing()) {
            activity.runOnUiThread(() -> {
                confirm_text.setText(confirmText);
                confirm_dialog.show();
            });
        }
    }

    private TextView listFile(LogFileIO.LogFile file, int pos) {
        String name = file.getFormattedName();
        TextView textView = new TextView(activity);
        textView.setPadding(10, 10, 10, 10);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        textView.setOnClickListener(v -> selectFile(file));
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String KB = String.valueOf(file.length() / 1000);
        int color = loggingIO.isActiveFile(file) ? activity.getColor(R.color.colorAccent) : activity.getColor(R.color.backgroundText);
        sb.append(TeensyStream.getColoredString(String.format(Locale.US, "%1$3s  ", pos).replace(" ", "  "), color));
        sb.append(name);
        sb.append(TeensyStream.getColoredString("  -  " + KB + " kb", color));
        activity.runOnUiThread(() -> {
                    textView.setText(sb);
                    if (fileLayout != null)
                        fileLayout.addView(textView);
                }
        );
        return textView;
    }

    private void selectFile(LogFileIO.LogFile file) {
        int i = 0;
        int b = -1;
        for (Pair<LogFileIO.LogFile, TextView> ignored : fileList) {
            if (fileList.get(i).first.equals(file)) {
                b = i;
            } else {
                i++;
            }
        }
        newSelectedPos(b);
    }

    private void selectFile(int pos) {
        newSelectedPos(pos);
    }

    private void newSelectedPos(int pos) {
        int size = fileList.size();
        if (size == 0) {
            selectedFile = -1;
            return;
        }
        if (pos < 0)
            pos = size - 1;
        else if (pos >= size)
            pos = 0;
        if (selectedFile > -1 && selectedFile < size)
            fileList.get(selectedFile).second.setBackgroundColor(Color.TRANSPARENT);
        TextView view = fileList.get(pos).second;
        view.setBackgroundColor(activity.getColor(R.color.translucentBlack));
        int finalPos = pos;
        int height = view.getHeight();
        selectedFile = pos;
        if (fileListScroller != null)
            fileListScroller.postDelayed(() -> fileListScroller.smoothScrollTo(0, (selectedFile * height) + (height * (selectedFile < finalPos ? 4 : -4))), 10);
    }

    private static final List<Spannable> loadedLines = new ArrayList<>();

    private void showLine() {
        activity.runOnUiThread(() -> {
            for (int i = 0; i < 25; i++) {
                if (!loadedLines.isEmpty()) {
                    LogViewer.append(loadedLines.get(0));
                    loadedLines.remove(0);
                }
            }
        });
    }

    private void showFile(LogFileIO.LogFile file) {
        if (colorThread == null || !colorThread.isAlive()) {
            activity.runOnUiThread(() -> logWait.setVisibility(View.VISIBLE));
            colorThread = new Thread(() -> {
                String[] strLines = TeensyStream.interpretLogFile(file).split("\n");
                loadedLines.clear();
                for (String line : strLines) {
                    loadedLines.add(TeensyStream.colorMsgString(line));
                }
                Toaster.showToast("Done interpreting");
                activity.runOnUiThread(() -> {
                    LogViewer.setText("");
                    logWait.setVisibility(View.GONE);
                });
                for (int i = 0; i < 4; i++) {
                    showLine();
                }
            });
            colorThread.start();
        } else {
            Toaster.showToast("Canceling previous selection", Toaster.STATUS.WARNING);
            if (colorThread.isAlive())
                colorThread.interrupt();
            showFile(file);
        }
    }

    public void updateFiles() {
        if (logScroller != null && logScroller.getVisibility() != View.GONE) {
            Toaster.showToast("Can't update while viewing log", Toaster.STATUS.WARNING);
            return;
        }
        if (fileLayout != null)
            fileLayout.removeAllViewsInLayout();
        fileList.clear();
        int i = 0;
        List<LogFileIO.LogFile> filesList = loggingIO.listFiles();
        filesList.sort(Comparator.comparingLong(LogFileIO.LogFile::lastModified).reversed());
        for (LogFileIO.LogFile file : filesList) {
            fileList.add(new Pair<>(file, listFile(file, i++)));
        }
        selectFile(Math.max(selectedFile, 0));
    }

    private void updateFileLayout() {
        fileLayout.removeAllViewsInLayout();
        for (Pair<LogFileIO.LogFile, TextView> file : fileList) {
            fileLayout.addView(file.second);
        }
    }

    public void setTeensyStream(TeensyStream stream) {
        this.loggingIO = stream.getLoggingIO();
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    @Override
    public int getPanelSize() {
        return SidePanelLayout.getWidth() + 16;
    }
}
