package com.viasofts.mygcs;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.android.client.utils.video.MediaCodecManager;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes;
import com.o3dr.services.android.lib.drone.companion.solo.SoloState;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private final Handler handler = new Handler();

    private static final int DEFAULT_UDP_PORT = 14550;
    private static final int DEFAULT_USB_BAUD_RATE = 57600;

    //private Spinner modeSelector;

    Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

/*
        this.modeSelector = (Spinner) findViewById(R.id.modeSelect);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onFlightModeSelected(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
 */
        mainHandler = new Handler(getApplicationContext().getMainLooper());
    }

    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);
        //updateVehicleModesForType(this.droneType);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
            //updateConnectedButton(false);
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
/*
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
 */
                checkSoloState();

                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
/*
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
 */
                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
/*
                updateArmButton();
 */
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();
/*
                    updateVehicleModesForType(this.droneType);
 */
                }
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
//                updateVehicleMode();
                break;

            case AttributeEvent.SPEED_UPDATED:
//                updateSpeed();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
//                updateAltitude();
                break;

            case AttributeEvent.HOME_UPDATED:
//                updateDistanceFromHome();
                break;

            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    private void checkSoloState() {
        final SoloState soloState = drone.getAttribute(SoloAttributes.SOLO_STATE);
        if (soloState == null){
            alertUser("Unable to retrieve the solo state.");
        }
        else {
            alertUser("Solo state is up to date.");
        }
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    @Override
    public void onLinkStateUpdated(@NonNull LinkConnectionStatus connectionStatus) {

    }

    @Override
    public void onTowerConnected() {
        alertUser("DroneKit-Android Connected");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser("DroneKit-Android Interrupted");
    }

    // Helper methods
    // ==========================================================

    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }

    private void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }


}
