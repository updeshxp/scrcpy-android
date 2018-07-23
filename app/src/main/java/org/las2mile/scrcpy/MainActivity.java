package org.las2mile.scrcpy;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;


public class MainActivity extends Activity implements Scrcpy.ServiceCallbacks, SensorEventListener {

    //    private static final String TAG = "MainActivity";
    private static final String PREFERENCE_KEY = "default";
    private static final String PREFERENCE_SPINNER_RESOLUTION = "spinner_resolution";
    private static final String PREFERENCE_SPINNER_BITRATE = "spinner_bitrate";
    private static int screenWidth;
    private static int screenHeight;
    private static boolean landscape = false;
    private static boolean first_time = true;
    private static boolean resultofRotation = false;
    private static boolean serviceBound = false;
    private static boolean nav = false;
    SensorManager sensorManager;
    private static SendCommands sendCommands;
    private int videoBitrate;
    private String localip;
    private Context context;
    private String serverAdr = null;
    private InputStream inputStream;
    private SurfaceView surfaceView;
    private Surface surface;
    private Scrcpy scrcpy;
    private long timestamp = 0;
    private byte[] fileBase64;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            scrcpy = ((Scrcpy.MyServiceBinder) iBinder).getService();
            scrcpy.setServiceCallbacks(MainActivity.this);
            if (first_time) {
                scrcpy.start(surface, serverAdr, screenHeight, screenWidth);
            } else {
                scrcpy.setParms(surface, screenWidth, screenHeight);
            }
            first_time = false;
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (first_time) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            setContentView(R.layout.activity_main);
            final Button startButton = (Button) findViewById(R.id.button_start);
            AssetManager assetManager = getAssets();
            try {
                inputStream = assetManager.open("scrcpy-server.jar");
                byte[] buffer = new byte[inputStream.available()];
                inputStream.read(buffer);
                fileBase64 = Base64.encode(buffer, 2);
            } catch (IOException e) {
                Log.e("Asset Manager", e.getMessage());
            }
            sendCommands = new SendCommands();
            startButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    localip = wifiIpAddress();
                    getAttributes();
                    if (!serverAdr.isEmpty()) {
                        if (sendCommands.SendAdbCommands(context, fileBase64, serverAdr, localip, videoBitrate, Math.max(screenHeight, screenWidth)) == 0) {
                            if (nav) {
                                startwithNav();
                            } else {
                                startwithoutNav();
                            }
                        } else {
                            Toast.makeText(context, "Network OR ADB connection failed", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(context, "Server Address Empty", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            this.context = this;
            final EditText editTextServerHost = (EditText) findViewById(R.id.editText_server_host);
            final Switch aSwitch = findViewById(R.id.switch1);
            editTextServerHost.setText(context.getSharedPreferences(PREFERENCE_KEY, 0).getString("Server Address", ""));
            aSwitch.setChecked(context.getSharedPreferences(PREFERENCE_KEY, 0).getBoolean("Nav Switch", false));
            setSpinner(R.array.options_resolution_keys, R.id.spinner_video_resolution, PREFERENCE_SPINNER_RESOLUTION);
            setSpinner(R.array.options_bitrate_keys, R.id.spinner_video_bitrate, PREFERENCE_SPINNER_BITRATE);
        } else {
            this.context = this;
            if (nav) {
                startwithNav();
            } else {
                startwithoutNav();
            }

        }

        sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        Sensor proximity;
        proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);

    }

    private void setSpinner(final int textArrayOptionResId, final int textViewResId, final String preferenceId) {

        final Spinner spinner = (Spinner) findViewById(textViewResId);
        ArrayAdapter<CharSequence> arrayAdapter = ArrayAdapter.createFromResource(this, textArrayOptionResId, android.R.layout.simple_spinner_item);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(arrayAdapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                context.getSharedPreferences(PREFERENCE_KEY, 0).edit().putInt(preferenceId, position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                context.getSharedPreferences(PREFERENCE_KEY, 0).edit().putInt(preferenceId, 0).apply();
            }
        });
        spinner.setSelection(context.getSharedPreferences(PREFERENCE_KEY, 0).getInt(preferenceId, 0));
    }

    private void getAttributes() {

        final EditText editTextServerHost = (EditText) findViewById(R.id.editText_server_host);
        serverAdr = editTextServerHost.getText().toString();
        context.getSharedPreferences(PREFERENCE_KEY, 0).edit().putString("Server Address", serverAdr).apply();
        final Spinner videoResolutionSpinner = (Spinner) findViewById(R.id.spinner_video_resolution);
        final Spinner videoBitrateSpinner = (Spinner) findViewById(R.id.spinner_video_bitrate);
        final Switch aSwitch = findViewById(R.id.switch1);
        nav = aSwitch.isChecked();
        context.getSharedPreferences(PREFERENCE_KEY, 0).edit().putBoolean("Nav Switch", nav).apply();


        final String[] videoResolutions = getResources().getStringArray(R.array.options_resolution_values)[videoResolutionSpinner.getSelectedItemPosition()].split(",");
        screenHeight = Integer.parseInt(videoResolutions[0]);
        screenWidth = Integer.parseInt(videoResolutions[1]);
        videoBitrate = getResources().getIntArray(R.array.options_bitrate_values)[videoBitrateSpinner.getSelectedItemPosition()];

    }


    private void swapDimensions() {
        int temp = screenHeight;
        screenHeight = screenWidth;
        screenWidth = temp;
    }


    @SuppressLint("ClickableViewAccessibility")
    private void startwithoutNav() {
        setContentView(R.layout.surface_no_nav);
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        surfaceView = (SurfaceView) findViewById(R.id.decoder_surface);
        surface = surfaceView.getHolder().getSurface();
        startScrcpyservice();
        DisplayMetrics metrics = new DisplayMetrics();

        if (ViewConfiguration.get(context).hasPermanentMenuKey()) {
            getWindowManager().getDefaultDisplay().getMetrics(metrics);


        } else {
            final Display display = getWindowManager().getDefaultDisplay();
            display.getRealMetrics(metrics);
        }
        final int height = metrics.heightPixels;
        final int width = metrics.widthPixels;


        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return scrcpy.touchevent(event, width, height);
            }
        });


    }

    @SuppressLint("ClickableViewAccessibility")
    private void startwithNav() {

        setContentView(R.layout.surface_nav);
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        final Button backButton = (Button) findViewById(R.id.back_button);
        final Button homeButton = (Button) findViewById(R.id.home_button);
        final Button appswitchButton = (Button) findViewById(R.id.appswitch_button);

        surfaceView = (SurfaceView) findViewById(R.id.decoder_surface);
        surface = surfaceView.getHolder().getSurface();
        startScrcpyservice();
        DisplayMetrics metrics = new DisplayMetrics();
        int offset = 0;

        if (ViewConfiguration.get(context).hasPermanentMenuKey()) {
            final Display display = getWindowManager().getDefaultDisplay();
            display.getRealMetrics(metrics);
            offset = 100;

        } else {
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
        }

        final int height = metrics.heightPixels - offset;
        final int width = metrics.widthPixels;
        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return scrcpy.touchevent(event, width, height);

            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scrcpy.sendKeyevent(4);

            }
        });

        homeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scrcpy.sendKeyevent(3);

            }
        });

        appswitchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scrcpy.sendKeyevent(187);

            }
        });


    }


    protected String wifiIpAddress() {
//https://stackoverflow.com/questions/6064510/how-to-get-ip-address-of-the-device-from-code
        try {
            InetAddress ipv4 = null;
            InetAddress ipv6 = null;

            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf
                        .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (inetAddress instanceof Inet6Address) {
                        ipv6 = inetAddress;
                        continue;
                    }
                    if (inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        ipv4 = inetAddress;
                        continue;
                    }
                    return inetAddress.getHostAddress();
                }
            }
            if (ipv6 != null) {
                return ipv6.getHostAddress();
            }
            if (ipv4 != null) {
                return ipv4.getHostAddress();
            }
            return null;

        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;

    }


    private void startScrcpyservice() {
        Intent intent = new Intent(this, Scrcpy.class);
        startService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }


    @Override
    public void loadNewRotation() {
        unbindService(serviceConnection);
        serviceBound = false;
        resultofRotation = true;
        landscape = !landscape;
        swapDimensions();
        if (landscape) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

    }


    @Override
    protected void onPause() {
        super.onPause();
        if (serviceBound) {
            scrcpy.pause();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (!first_time && !resultofRotation) {
            final View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            if (serviceBound) {
                scrcpy.resume();
            }
        }
        resultofRotation = false;
    }

    @Override
    public void onBackPressed() {
        if (timestamp == 0) {
            timestamp = SystemClock.uptimeMillis();
            Toast.makeText(context, "Press again to exit", Toast.LENGTH_SHORT).show();
        } else {
            long now = SystemClock.uptimeMillis();
            if (now < timestamp + 1000) {
                timestamp = 0;
                if (serviceBound) {
                    scrcpy.StopService();
                    unbindService(serviceConnection);
                }
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
            timestamp = 0;
        }

    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            if (sensorEvent.values[0] == 0) {
                if (serviceBound) {
                    scrcpy.sendKeyevent(28);
                }
            } else {
                if (serviceBound) {
                    scrcpy.sendKeyevent(29);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
