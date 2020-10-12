package sae.iit.saedashboard;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.viewpager.widget.ViewPager;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.google.android.material.tabs.TabLayout;

public class MainActivity extends AppCompatActivity {
	//Initializing variable
    private MainTab mainTab = (MainTab) getSupportFragmentManager().findFragmentById(R.id.mainTab);
    private SecondaryTab secondaryTab = (SecondaryTab) getSupportFragmentManager().findFragmentById(R.id.secondaryTab);
    private TroubleshootTab troubleshootTab = (TroubleshootTab) getSupportFragmentManager().findFragmentById(R.id.troubleshootTab);
    public final String ACTION_USB_PERMISSION = "com.hariharan.arduinousb.USB_PERMISSION";
    private MyPagerAdapter pagerAdapter;
    private boolean shown = false;
	private ViewPager viewPager;
    private TabLayout tabLayout;
	private String line;
	Button startButton, closeButton, clearButton, clearTButton;
    UsbDeviceConnection connection;
    UsbSerialDevice serialPort;
    UsbManager usbManager;
	ImageButton SHbutton;
	TextView sconsole;
	UsbDevice device;

	@SuppressLint("WrongViewCast")
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		//Hides the status bar.
		View decorView = getWindow().getDecorView();

		int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
		decorView.setSystemUiVisibility(uiOptions);
		//Setting up ViewPager, Adapter, and TabLayout

		viewPager = findViewById(R.id.viewPager);
		pagerAdapter = new MyPagerAdapter(getSupportFragmentManager());
		tabLayout = findViewById(R.id.tabLayout);
		viewPager.setAdapter(pagerAdapter);
		tabLayout.setupWithViewPager(viewPager);
		tabLayout.setTabTextColors(getResources().getColor(R.color.white), getResources().getColor(R.color.white));
		startButton = findViewById(R.id.Start);
		closeButton = findViewById(R.id.Close);
		clearButton = findViewById(R.id.ClearF);
		clearTButton = findViewById(R.id.Clear);
		SHbutton = findViewById(R.id.Show);
		//Makes corner Buttons invisible
		startButton.setVisibility(View.INVISIBLE);
		closeButton.setVisibility(View.INVISIBLE);
		clearButton.setVisibility(View.INVISIBLE);
		clearTButton.setVisibility(View.INVISIBLE);
		sconsole = findViewById(R.id.textView7);
		sconsole.setVisibility(View.INVISIBLE);
		setUiEnabled(false);
		usbManager = (UsbManager) getSystemService(USB_SERVICE);
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		registerReceiver(broadcastReceiver, filter);
		ConsoleLog( "Console Start!");

		//Background update Function. MOST IMPORTANT
		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						try {

							mainTab.setSpeedometer(TeensyMsg.ADD.SPEED.getValue());
							mainTab.setPowerGauge(TeensyMsg.ADD.ANOTHERVAL.getValue());
							mainTab.setBatteryLife(0);
							mainTab.setBatImage(0);
							secondaryTab.setLeftMotorTemp("0");
							secondaryTab.setRightMotorTemp("0");
							secondaryTab.setLeftMotorContTemp("0");
							secondaryTab.setRightMotorContTemp("0");
							secondaryTab.setActiveAeroPos("0");
							secondaryTab.setDCBusCurrent("0");

						} catch (NullPointerException e) {return;}
					}
				});
			}

		}, 0, 50);
	}

	public void onClickStart(View view) {
		HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
		if (!usbDevices.isEmpty()) {
			boolean keep = true;
			for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
				device = entry.getValue();
				int deviceVID = device.getVendorId();
				if (deviceVID == 5824) {
					PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
					usbManager.requestPermission(device, pi);
					keep = false;
				} else {
					ConsoleLog(String.valueOf(deviceVID));
					connection = null;
					device = null;
				}

				if (!keep)
					break;
			}
		}
	}

	public void onClickStop(View view) {
		setUiEnabled(false);
		serialPort.close();
		ConsoleLog( "Serial Connection Closed!");
	}

	private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			try {
				switch (intent.getAction()) {
					case ACTION_USB_PERMISSION:
						ConsoleLog("We Good 1");
						boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
						if (granted) {
							ConsoleLog("We Good 2");
							connection = usbManager.openDevice(device);
							serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
							if (serialPort != null) {
								ConsoleLog("We Good 3");
								if (serialPort.open()) {
									setUiEnabled(true);
									serialPort.setBaudRate(9600);
									serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
									serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
									serialPort.setParity(UsbSerialInterface.PARITY_NONE);
									serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
									serialPort.read(mCallback);
									ConsoleLog("Serial Connection Opened!");
									setUiEnabled(true);
								} else {
									ConsoleLog("Not good PORT");
								}
							} else {
								ConsoleLog("Not good no serial");
							}
						} else {
							ConsoleLog("not good at all");
						}
						break;
					case UsbManager.ACTION_USB_DEVICE_ATTACHED:
						ConsoleLog("connected");
						onClickStart(startButton);
						break;
					case UsbManager.ACTION_USB_DEVICE_DETACHED:
						onClickStop(closeButton);
						break;
				}
			} catch (NullPointerException e){return;}
		}
	};

    /**
     * Log a string to test Console using tvAppend
     *
     * @param msg Message String
     */
    public void ConsoleLog(String msg){
        tvAppend(sconsole, msg + "\n");
    }

	private void tvAppend(TextView tv, CharSequence text) {
		final TextView ftv = tv;
		final CharSequence ftext = text;
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ftv.append(ftext);
			}
		});
	}

	public void setUiEnabled(boolean bool) {
		startButton.setEnabled(!bool);
		closeButton.setEnabled(bool);
	}
	public void onClickFault() {
		serialPort.write("0000".getBytes());
	}

	public void onClickSH(View view){
		if(!shown){
			startButton.setVisibility(View.VISIBLE);
			closeButton.setVisibility(View.VISIBLE);
			clearButton.setVisibility(View.VISIBLE);
			clearTButton.setVisibility(View.VISIBLE);
			sconsole.setVisibility(View.VISIBLE);
			shown = true;
		} else {
			startButton.setVisibility(View.INVISIBLE);
			closeButton.setVisibility(View.INVISIBLE);
			clearButton.setVisibility(View.INVISIBLE);
			clearTButton.setVisibility(View.INVISIBLE);
			sconsole.setVisibility(View.INVISIBLE);
			shown = false;
		}
	}
	public void onClickClear(View view) {
		sconsole.setText(" ");
	}

	UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
		@RequiresApi(api = Build.VERSION_CODES.KITKAT)
        public void onReceivedData(byte[] arg0) {

            TeensyMsg.setData(arg0);

            String data;

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				data = new String(arg0, StandardCharsets.UTF_8);
			} else {
				data = new String(arg0, Charset.defaultCharset());
            }
			line = data;
			ConsoleLog(line);
		}
    };

	/*protected void onResume(Bundle savedInstanceState) {
		//Hides the status bar.
		View decorView = getWindow().getDecorView();
		int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
		decorView.setSystemUiVisibility(uiOptions);
	}*/

	//Updates display on tablet
	//Will receive an array
	static public void update() {
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(broadcastReceiver);
		super.onDestroy();
	}

	//CSV File Writer
	@RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void export(View view) {
		try {
				//saving the file into device
				FileOutputStream out = openFileOutput("data.csv", Context.MODE_PRIVATE);
				out.write((TeensyMsg.dataString()).getBytes());
				out.close();
				//exporting
				Context context = getApplicationContext();
				File filelocation = new File(getFilesDir(), "data.csv");
				Uri path = FileProvider.getUriForFile(context, "com.example.exportcsv.fileprovider", filelocation);
				Intent fileIntent = new Intent(Intent.ACTION_SEND);
				fileIntent.setType("text/csv");
				fileIntent.putExtra(Intent.EXTRA_SUBJECT, "Data");
				fileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
				fileIntent.putExtra(Intent.EXTRA_STREAM, path);
				startActivity(Intent.createChooser(fileIntent, "Send mail"));
			} catch (Exception e) {
				e.printStackTrace();
			}
	}
}