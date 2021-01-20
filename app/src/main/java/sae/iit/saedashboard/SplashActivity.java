package sae.iit.saedashboard;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.VideoView;

public class SplashActivity extends Activity {
	VideoView videoView;

	@Override
	public void onCreate(Bundle savedInstanceState) {

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
		}

		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.activity_splash);
		videoView = findViewById(R.id.videoView);

		View decorView = getWindow().getDecorView();
		int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
		decorView.setSystemUiVisibility(uiOptions);

		Uri video = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.startup);
		videoView.setVideoURI(video);
		videoView.setOnCompletionListener(mp -> startNextActivity());

		videoView.start();
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		videoView.stopPlayback();
		startNextActivity();
		return super.dispatchTouchEvent(ev);
	}

	private void startNextActivity() {
		if (isFinishing())
			return;
		startActivity(new Intent(this, MainActivity.class));
		finish();
	}
}