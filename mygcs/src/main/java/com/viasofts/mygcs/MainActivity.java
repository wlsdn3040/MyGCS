package com.viasofts.mygcs;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.naver.maps.map.util.FusedLocationSource;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.MissionApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes;
import com.o3dr.services.android.lib.drone.companion.solo.SoloState;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.mission.Mission;
import com.o3dr.services.android.lib.drone.mission.item.spatial.Waypoint;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.GuidedState;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;
import com.o3dr.services.android.lib.util.MathUtils;
import com.viasofts.mygcs.activites.helpers.BluetoothDevicesActivity;
import com.viasofts.mygcs.utils.TLogUtils;
import com.viasofts.mygcs.utils.prefs.DroidPlannerPrefs;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener, OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static Context MapsContext;

    private Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private final Handler handler = new Handler();
    private static final int DEFAULT_UDP_PORT = 14550;
    private static final int DEFAULT_USB_BAUD_RATE = 57600;
    private NaverMap mNaverMap;
    private MapView mapView;
    private Marker droneloc = new Marker();
    private boolean goal = false;
    private Marker mGuidedMarker = new Marker();
    private LatLng mGuidePoint;
    private ArrayList<String> textInRecyclerView = new ArrayList<>();
    private ArrayList<LatLng> mLocationCollection = new ArrayList<LatLng>();
    private RecyclerView mRecyclerView;
    private PolylineOverlay mPolyline = new PolylineOverlay();
    private double alt = 3; //altitude_setting
    private CameraUpdate droneUpdate;
    private double latitude;
    private double longitude;
    private boolean camMove = false;
    private double FW = 3; // Flight Width Setting
    private double AB = 10; // AB_Distance_Setting

    private PolylineOverlay abPolyline = new PolylineOverlay();
    private ArrayList<LatLng> points = new ArrayList<>();
    private Marker A = new Marker();
    private Marker B = new Marker();
    private double degree;
    private double distance;
    private LatLong Ap;
    private LatLong Bp;

    LatLng droneposition;

    private static final int PERMISSION_REQUEST_CODE = 1000;
    private static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };
    private FusedLocationSource mLocationSource;

    private Spinner modeSelector;

    ConnectionParameter connParams;

    Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_main);

        mapView = (MapView) findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        mLocationSource =  new FusedLocationSource(this, PERMISSION_REQUEST_CODE);

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

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

        mainHandler = new Handler(getApplicationContext().getMainLooper());

        mRecyclerView = findViewById(R.id.stateText) ;
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this)) ;
    }

    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);
        updateVehicleModesForType(this.droneType);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
           // updateConnectedButton(false);
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                addRecyclerViewText("?????? ?????????");

                updateConnectedButton(this.drone.isConnected());
                updateArmButton();

                checkSoloState();

                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                addRecyclerViewText("??????????????????");

                updateConnectedButton(this.drone.isConnected());
                updateArmButton();

                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:

                updateArmButton();
                updateBtnAltitude();

                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();
                    updateVehicleModesForType(this.droneType);
                }
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;

            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();
                break;

            case AttributeEvent.BATTERY_UPDATED:
                updateVoltage();
                break;

            case AttributeEvent.ATTITUDE_UPDATED:
                updateYAW();
                break;

            case AttributeEvent.GPS_COUNT:
                updateSatellite();
                break;

            case AttributeEvent.HOME_UPDATED:
//                updateDistanceFromHome();

                break;
            case AttributeEvent.GPS_POSITION:

                updateDroneLocation();

                break;
            case AttributeEvent.MISSION_SENT:
                addRecyclerViewText("??????????????????");
                MissionApi.getApi(drone).startMission(true,true,null);
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
        addRecyclerViewText(errorMsg);
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

    protected void updateVehicleModesForType(int droneType) {

        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter();
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    public void addRecyclerViewText(String msg){
        textInRecyclerView.add(msg);
        recyclerViewText adapter = new recyclerViewText(textInRecyclerView) ;
        mRecyclerView.setAdapter(adapter) ;
    }

//    public class RecyclerViewDecoration extends RecyclerView.ItemDecoration {
//
//        private final int divHeight;
//
//        public RecyclerViewDecoration(int divHeight)
//        {
//            this.divHeight = divHeight;
//        }
//
//        @Override
//        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state)
//        {
//            super.getItemOffsets(outRect, view, parent, state);
//            outRect.top = divHeight;
//        }
//    }

    // Helper methods
    // ==========================================================

    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }

    private void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }


    public void onBtnConnectTap(View view) {
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        } else {
            new AlertDialog.Builder(this).setTitle("?????? ?????? ??????")
                    .setMessage("").setPositiveButton("UDP", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    Toast.makeText(getApplicationContext(), "UDP ??????", Toast.LENGTH_SHORT).show();
                    ConnectionParameter connectionParams = ConnectionParameter.newUdpConnection(null);
                    drone.connect(connectionParams);
                }
            }).setNegativeButton("BLUETOOTH", new DialogInterface.OnClickListener() {
                public void onClick (DialogInterface dialog,int whichButton){
                    Toast.makeText(getApplicationContext(), "???????????? ??????", Toast.LENGTH_SHORT).show();
                    drone.connect(connParams);
                }
            }).show();
        }
    }

    protected boolean GuideMode(final LatLong point) {

        State vehicleState = this.drone.getAttribute(AttributeType.STATE);

        if (vehicleState.isFlying()) {
            goal = true;
            ControlApi.getApi(drone).goTo(point, true, null);
            if (vehicleState.getVehicleMode() != VehicleMode.COPTER_GUIDED){
                AlertDialog.Builder alt_bld = new AlertDialog.Builder(MainActivity.MapsContext);
                alt_bld.setMessage("????????? ???????????? ?????????????????? ????????? ????????? ???????????????.").setCancelable(false).setPositiveButton("??????", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_GUIDED, new AbstractCommandListener() {
                            @Override
                            public void onSuccess() {
                                ControlApi.getApi(drone).goTo(point, true, null);
                                addRecyclerViewText("????????? ?????????????????? ???????????????");
                            }

                            @Override
                            public void onError(int executionError) {
                            }

                            @Override
                            public void onTimeout() {
                            }
                        });
                    }
                }).setNegativeButton("??????", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
                AlertDialog alert = alt_bld.create();
                alert.setTitle("????????? ??????");
                alert.show();
            }
            else {
                ControlApi.getApi(drone).goTo(point, true, null);
            }
            return true;
        }
        return  false;
    }

    protected boolean CheckGoal(LatLng recentLatLng){
        GuidedState guidedState = drone.getAttribute(AttributeType.GUIDED_STATE);
        LatLng target = new LatLng(guidedState.getCoordinate().getLatitude(), guidedState.getCoordinate().getLongitude());

        return target.distanceTo(recentLatLng) <=1;
    }

    public void tapTestBTN(View view){

        DroidPlannerPrefs mPrefs = DroidPlannerPrefs.getInstance(getApplicationContext());

        String btAddress = mPrefs.getBluetoothDeviceAddress();
        final @ConnectionType.Type int connectionType = mPrefs.getConnectionParameterType();

        Uri tlogLoggingUri = TLogUtils.getTLogLoggingUri(getApplicationContext(),
                connectionType, System.currentTimeMillis());

        final long EVENTS_DISPATCHING_PERIOD = 200L; //MS

        if (TextUtils.isEmpty(btAddress)) {
            connParams = null;
            startActivity(new Intent(getApplicationContext(), BluetoothDevicesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else {
            connParams = ConnectionParameter.newBluetoothConnection(btAddress,
                    tlogLoggingUri, EVENTS_DISPATCHING_PERIOD);
        }
    }
    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Vehicle mode change successful.");
                addRecyclerViewText("?????? ?????? ??????");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Vehicle mode change failed: " + executionError);
                addRecyclerViewText("?????? ?????? ??????");
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out.");
                addRecyclerViewText("?????? ?????? ?????? ??????");
            }
        });
    }

    public void onArmButtonTap(View view) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);

        if (vehicleState.isFlying()) {

            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {

                @Override
                public void onSuccess() {
                    alertUser("?????? ???");
                    addRecyclerViewText("?????? ??????");
                }
                @Override
                public void onError(int executionError) {
                    alertUser("Unable to land the vehicle.");
                    addRecyclerViewText("??????:?????? ????????????");
                }
                @Override
                public void onTimeout() {
                    alertUser("Unable to land the vehicle.");
                    addRecyclerViewText("????????????:?????? ?????? ??????");
                }
            });
        }
        else if (vehicleState.isArmed()) {

            new AlertDialog.Builder(this).setTitle("???????????? ??????!!")
                    .setMessage("????????? ?????????????????? ???????????????.").setPositiveButton("??????", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    ControlApi.getApi(drone).takeoff(alt, new AbstractCommandListener() {

                        @Override
                        public void onSuccess() {
                            alertUser("Taking off...");
                            addRecyclerViewText("?????? ??????");
                        }

                        @Override
                        public void onError(int i) {
                            alertUser("Unable to take off.");
                            addRecyclerViewText("??????:?????? ?????? ??????");
                        }

                        @Override
                        public void onTimeout() {
                            alertUser("Unable to take off.");
                            addRecyclerViewText("????????????:?????? ?????? ??????");
                        }
                    });
                }
            }).setNegativeButton("??????", new DialogInterface.OnClickListener() {
                public void onClick (DialogInterface dialog,int whichButton){
                    Toast.makeText(getApplicationContext(), "????????????", Toast.LENGTH_SHORT).show();
                }
            }).show();
        }

        else if (!vehicleState.isConnected()) {
            alertUser("Connect to a drone first");
        } else {
            new AlertDialog.Builder(this).setTitle("????????? ?????????????????????????")
                    .setMessage("?????? : ????????? ???????????? ???????????????.").setPositiveButton("??????", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {

                    VehicleApi.getApi(drone).arm(true, false, new SimpleCommandListener() {
                        @Override
                        public void onSuccess() {
                            alertUser("Arm vehicle");
                            addRecyclerViewText("?????? ??????");
                        }
                        @Override
                        public void onError(int executionError) {
                            alertUser("Unable to arm vehicle.");
                            addRecyclerViewText("??????: ?????? ?????? ??????");
                        }
                        @Override
                        public void onTimeout() {
                            alertUser("Arming operation timed out.");
                            addRecyclerViewText("????????????: ?????? ?????? ??????");
                        }
                    });
                }
            }).setNegativeButton("??????", new DialogInterface.OnClickListener() {
                public void onClick (DialogInterface dialog,int whichButton){
                    Toast.makeText(getApplicationContext(), "?????? ??????", Toast.LENGTH_SHORT).show();
                }
            }).show();
        }
    }
    public void onMapBtnTap(View view){

        Button basic = (Button) findViewById(R.id.basic);
        Button satellite = (Button) findViewById(R.id.satellite);
        Button terrain = (Button) findViewById(R.id.terrain);

        if (basic.getVisibility() == view.GONE){
            basic.setVisibility(view.VISIBLE);
            satellite.setVisibility(view.VISIBLE);
            terrain.setVisibility(view.VISIBLE);
        }
        else{
            basic.setVisibility(view.GONE);
            satellite.setVisibility(view.GONE);
            terrain.setVisibility(view.GONE);
        }
    }

    public void onBasicBtnTap(View view){
        Button selectMap = (Button) findViewById(R.id.selectMap);

        mNaverMap.setMapType(NaverMap.MapType.Basic);
        selectMap.setText("????????????");
    }

    public void onSatelliteBtnTap(View view){
        Button selectMap = (Button) findViewById(R.id.selectMap);

        mNaverMap.setMapType(NaverMap.MapType.Satellite);
        selectMap.setText("????????????");
    }

    public void onTerrainBtnTap(View view){
        Button selectMap = (Button) findViewById(R.id.selectMap);

        mNaverMap.setMapType(NaverMap.MapType.Terrain);
        selectMap.setText("?????????");
    }

    public void onAltitudeBtnTap(View view){

        Button add = (Button) findViewById(R.id.add0_5);
        Button sub = (Button) findViewById(R.id.sub0_5);

        if (sub.getVisibility()==view.GONE){
            sub.setVisibility(view.VISIBLE);
            add.setVisibility(view.VISIBLE);
        }
        else{
            sub.setVisibility(view.GONE);
            add.setVisibility(view.GONE);
        }
    }

    public double onAddBtnTap(View view){

        Button btnAltitude = (Button) findViewById(R.id.btnAltitude);

        if (alt>9.9)
        {
            alertUser("Can't set altitude higher than 10m");
        }
        else {
            alt +=0.5;
            btnAltitude.setText(alt + "m");
        }
        return alt;
    }

    public double onSubBtnTap(View view){

        Button btnAltitude = (Button) findViewById(R.id.btnAltitude);

        if (alt<3.5)
        {
            alertUser("Can't set altitude lower than 3m");
        }
        else {
            alt -=0.5;
            btnAltitude.setText(alt + "m");
        }
        return alt;

    }

    public void onHoldMoveBtnTap(View view){

        Button btnHold = (Button) findViewById(R.id.holdMapBtn);
        Button btnMove = (Button) findViewById(R.id.moveMapBtn);

        if (btnHold.getVisibility() == view.GONE){
            btnHold.setVisibility(view.VISIBLE);
            btnMove.setVisibility(view.VISIBLE);
        }
        else{
            btnHold.setVisibility(view.GONE);
            btnMove.setVisibility(view.GONE);
        }
    }

    public void onHoldBtnTap(View view){
        Button btnHoldMove = (Button) findViewById(R.id.holdMoveMap);
        camMove = true;
        btnHoldMove.setText("?????????");
    }

    public void onMoveBtnTap(View view){
        Button btnHoldMove = (Button) findViewById(R.id.holdMoveMap);
        camMove = false;
        btnHoldMove.setText("?????????");
    }

    public void onCadastralBtnTap(View view){

        Button btnOn = (Button) findViewById(R.id.cadastralOn);
        Button btnOff = (Button) findViewById(R.id.cadastralOff);

        if (btnOn.getVisibility() == view.GONE){
            btnOn.setVisibility(view.VISIBLE);
            btnOff.setVisibility(view.VISIBLE);
        }
        else{
            btnOn.setVisibility(view.GONE);
            btnOff.setVisibility(view.GONE);
        }
    }

    public void onCadastralOnBtnTap(View view){

        Button btnSelOnOff = (Button) findViewById(R.id.cadastralMap);
        mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);
        btnSelOnOff.setText("?????????On");
    }

    public void onCadastralOffBtnTap(View view){

        Button btnSelOnOff = (Button) findViewById(R.id.cadastralMap);
        mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
        btnSelOnOff.setText("?????????Off");
    }

    protected void updateSpeed() {
        TextView speedValueTextView = (TextView) findViewById(R.id.speedValueTextView);
        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
        speedValueTextView.setText(String.format("%3.1f", droneSpeed.getGroundSpeed()) + "m/s");
    }

    protected void updateAltitude() {
        TextView altitudeValueTextView = (TextView) findViewById(R.id.altitudeValueTextView);
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        altitudeValueTextView.setText(String.format("%3.1f", droneAltitude.getAltitude()) + "m");
    }

    protected void updateVoltage(){
        TextView voltageValueTextView = (TextView) findViewById(R.id.voltageValueTextView);
        Battery droneVoltage = this.drone.getAttribute(AttributeType.BATTERY);
        voltageValueTextView.setText(String.format("%3.1f", droneVoltage.getBatteryVoltage()) + "V");
    }

    protected void updateYAW(){
        TextView YAWValueTextView = (TextView) findViewById(R.id.YAWValueTextView);
        Attitude droneYAW = this.drone.getAttribute(AttributeType.ATTITUDE);
        YAWValueTextView.setText(String.format("%3.1f", droneYAW.getYaw()+180) + "deg");
    }

    protected void updateSatellite(){
        TextView satelliteValueTextView = (TextView) findViewById(R.id.satelliteValueTextView);
        Gps droneSatellite = this.drone.getAttribute(AttributeType.GPS);
        satelliteValueTextView.setText(String.format("%d", droneSatellite.getSatellitesCount()));
    }

    protected void updateDroneLocation(){
        Gps droneLocation = this.drone.getAttribute(AttributeType.GPS);
        Attitude droneYAW = this.drone.getAttribute(AttributeType.ATTITUDE);

        droneloc.setMap(null);

        double yaw = (double) (droneYAW.getYaw()+180);

        latitude = droneLocation.getPosition().getLatitude();
        longitude = droneLocation.getPosition().getLongitude();

        droneposition = new LatLng(latitude, longitude);

        droneloc.setPosition(new LatLng(latitude, longitude));
        droneloc.setAngle((int)yaw);
        mLocationCollection.add(droneposition);

        if(camMove == true){
            droneUpdate.scrollTo(new LatLng(droneposition.latitude, droneposition.longitude));
            mNaverMap.moveCamera(droneUpdate);
        }

        if (mLocationCollection.size() > 2){
            drawPolyline();
        }

        droneloc.setMap(mNaverMap);

        if(CheckGoal(droneposition)&&goal){
            addRecyclerViewText("????????? ??????????????? ??????????????????.");
            mGuidedMarker.setMap(null);
            goal = false;
            VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_LOITER,
                    new AbstractCommandListener() {
                        @Override
                        public void onSuccess() {
                        }
                        @Override
                        public void onError(int i) {
                        }
                        @Override
                        public void onTimeout() {
                        }
                    });
        }
    }

    protected void drawPolyline(){
        mPolyline.setCoords(mLocationCollection);
        mPolyline.setCapType(PolylineOverlay.LineCap.Round);
        mPolyline.setJoinType(PolylineOverlay.LineJoin.Round);
        mPolyline.setColor(Color.WHITE);
        mPolyline.setMap(mNaverMap);
    }

    protected void updateConnectedButton(Boolean isConnected) {
        Button connectButton = (Button) findViewById(R.id.btnConnect);
        if (isConnected) {
            connectButton.setText("Disconnect");
        } else {
            connectButton.setText("Connect");
        }
    }

    protected void updateArmButton() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button armButton = (Button) findViewById(R.id.btnArmTakeOff);

        if (!this.drone.isConnected()) {
            armButton.setVisibility(View.INVISIBLE);
        } else {
            armButton.setVisibility(View.VISIBLE);
        }

        if (vehicleState.isFlying()) {
            // Land
            armButton.setText("LAND");
        } else if (vehicleState.isArmed()) {
            // Take off
            armButton.setText("TAKE OFF");
        } else if (vehicleState.isConnected()) {
            // Connected but not Armed
            armButton.setText("ARM");
        }
    }

    protected void updateBtnAltitude(){
        Button btnAltitude = (Button) findViewById(R.id.btnAltitude);

        btnAltitude.setText(alt + "m");
    }


    class recyclerViewText extends RecyclerView.Adapter<recyclerViewText.ViewHolder> {

        private ArrayList<String> mData = null ;


        public class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView1 ;

            ViewHolder(View itemView) {
                super(itemView) ;

                textView1 = itemView.findViewById(R.id.text1) ;
            }
        }

        recyclerViewText(ArrayList<String> list) {
            mData = list ;
        }

        @Override
        public recyclerViewText.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Context context = parent.getContext() ;
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) ;

            View view = inflater.inflate(R.layout.recycler_item, parent, false) ;
            recyclerViewText.ViewHolder vh = new recyclerViewText.ViewHolder(view) ;

            return vh ;
        }

        @Override
        public void onBindViewHolder(recyclerViewText.ViewHolder holder, int position) {
            String text = mData.get(position) ;
            holder.textView1.setText(text) ;
        }

        @Override
        public int getItemCount() {
            return mData.size() ;
        }
    }

        @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,  @NonNull int[] grantResults) {
        if (mLocationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            if (!mLocationSource.isActivated()) { // ?????? ?????????
                mNaverMap.setLocationTrackingMode(LocationTrackingMode.None);
            }
            return;
        }
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);
    }

    public void onClearBtnTap(View view){

        mPolyline.setMap(null);
        mLocationCollection.clear();
        textInRecyclerView.clear();
        recyclerViewText adapter = new recyclerViewText(textInRecyclerView) ;
        mRecyclerView.setAdapter(adapter);
        mGuidedMarker.setMap(null);
        ControlApi.getApi(drone).pauseAtCurrentLocation(null);
        A.setMap(null);
        B.setMap(null);
        points.clear();
        abPolyline.setMap(null);
    }

    public void onMissionBtnTap(View view){
        Button btnAB = (Button) findViewById(R.id.btnAB);
        Button btnPolygon = (Button) findViewById(R.id.btnPolygon);
        Button btnCancel = (Button) findViewById(R.id.btnCancel);

        if (btnAB.getVisibility() == view.GONE){
            btnAB.setVisibility(view.VISIBLE);
            btnPolygon.setVisibility(view.VISIBLE);
            btnCancel.setVisibility(view.VISIBLE);
        }
        else{
            btnAB.setVisibility(view.GONE);
            btnPolygon.setVisibility(view.GONE);
            btnCancel.setVisibility(view.GONE);
        }
    }

    public void onFlightWidthBtn(View view) {
        Button btnAddW = (Button) findViewById(R.id.btnFlightWidthAdd0_5);
        Button btnSubW = (Button) findViewById(R.id.btnFlightWidthSub0_5);

        if (btnAddW.getVisibility() == view.GONE) {
            btnAddW.setVisibility(view.VISIBLE);
            btnSubW.setVisibility(view.VISIBLE);
        } else {
            btnAddW.setVisibility(view.GONE);
            btnSubW.setVisibility(view.GONE);
        }
    }

    public void onABDistanceBtnTap(View view){
            Button btnADD10 = (Button) findViewById(R.id.btnABDistanceAdd10);
            Button btnSUB10 = (Button) findViewById(R.id.btnABDistanceSub10);

            if (btnADD10.getVisibility() == view.GONE) {
                btnADD10.setVisibility(view.VISIBLE);
                btnSUB10.setVisibility(view.VISIBLE);
            } else {
                btnADD10.setVisibility(view.GONE);
                btnSUB10.setVisibility(view.GONE);
            }
        }

    public void onABBtnTap(View view){
        Button btnMission = (Button) findViewById(R.id.btnMission);
        Button abMission = (Button) findViewById(R.id.abMissionBtn);

        btnMission.setText("AB");

        if (abMission.getVisibility() == view.GONE) {
            abMission.setVisibility(view.VISIBLE);
        } else {
            abMission.setVisibility(view.GONE);
        }

        double angle = getAngle(B.getPosition(),A.getPosition());

        mNaverMap.setOnMapClickListener((point, coord) ->{
            if (abMission.getText().equals("A????????????")){
                A.setPosition(coord);
                A.setMap(mNaverMap);
                A.setCaptionText("A");
                abMission.setText("B????????????");
            }
            else if(abMission.getText().equals("B????????????")){
                B.setPosition(coord);
                B.setMap(mNaverMap);
                B.setCaptionText("B");

                drawLine();
                abMission.setText("????????????");
            }
        });
    }

    public void onABMissiontap(View view){
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button abMission = (Button) findViewById(R.id.abMissionBtn);

        if (abMission.getText().equals("????????????")){

            abMission.setText("????????????");
        }
        else if (abMission.getText().equals("????????????")) {

            Mission mission = new Mission();
            ArrayList<Waypoint> abWay = new ArrayList<>();
            for (int i=0; i<points.size(); i++){
                abWay.add(new Waypoint());
                abWay.get(i).setCoordinate(new LatLongAlt(points.get(i).latitude , points.get(i).longitude,FW));
                abWay.get(i).setDelay(1);
                mission.addMissionItem(i,abWay.get(i));
            }
            MissionApi.getApi(drone).setMission(mission,true);
            abMission.setText("????????????");
        }
        else if(abMission.getText().equals("????????????")){
            abMission.setText("????????????");
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LOITER, new AbstractCommandListener() {
                @Override
                public void onSuccess() {
                    alertUser("????????? ????????? ??????");
                }
                @Override
                public void onError(int executionError) {
                }
                @Override
                public void onTimeout() {
                }
            });
        }
    }

    public double getAngle(LatLng position, LatLng position1) {

        double dx = position1.longitude - position.longitude;
        double dy = position1.latitude - position.latitude;

        double rad= Math.atan2(dx, dy);
        degree = (rad*180)/Math.PI ;

        return degree;
    }

    public void drawLine(){

        Ap = new LatLong(A.getPosition().latitude, A.getPosition().longitude);
        Bp = new LatLong(B.getPosition().latitude, B.getPosition().longitude);

        LatLong a1;
        LatLong b1;

        int num = 2;

        double degree1 = MathUtils.getHeadingFromCoordinates(Ap, Bp);

        Collections.addAll(points, new LatLng(Ap.getLatitude(), Ap.getLongitude()), new LatLng(Bp.getLatitude(), Bp.getLongitude()));

        b1 = MathUtils.newCoordFromBearingAndDistance(Bp, 90+degree1, FW);
        a1 = MathUtils.newCoordFromBearingAndDistance(Ap, 90+degree1, FW);

        Collections.addAll(points, new LatLng(b1.getLatitude(), b1.getLongitude()), new LatLng(a1.getLatitude(), a1.getLongitude()));

        int p = (int)(AB/FW);

        while (p>=0){

            if (num%2==0){
                a1 = MathUtils.newCoordFromBearingAndDistance(Ap, 90+degree1, FW*num);
                Collections.addAll(points, new LatLng(a1.getLatitude(), a1.getLongitude()));
                b1 = MathUtils.newCoordFromBearingAndDistance(Bp, 90+degree1, FW*num);
                Collections.addAll(points, new LatLng(b1.getLatitude(), b1.getLongitude()));
            }
            else if(num%2==1){
                b1 = MathUtils.newCoordFromBearingAndDistance(Bp, 90+degree1, FW*num);
                Collections.addAll(points, new LatLng(b1.getLatitude(), b1.getLongitude()));
                a1 = MathUtils.newCoordFromBearingAndDistance(Ap, 90+degree1, FW*num);
                Collections.addAll(points, new LatLng(a1.getLatitude(), a1.getLongitude()));
            }

            num++;
            p--;
        }
        abPolyline.setCoords(points);
        abPolyline.setWidth(10);
        abPolyline.setColor(Color.BLACK);
        abPolyline.setMap(mNaverMap);
    }


    public void onPolyBtnTap(View view){
        Button btnMission = (Button) findViewById(R.id.btnMission);

        btnMission.setText("?????????");
    }

    public void onCancelBtnTap(View view){
        Button btnMission = (Button) findViewById(R.id.btnMission);

        btnMission.setText("??????");
    }

    public double onFwAddBtnTap(View view){
        Button btnFw = (Button) findViewById(R.id.btnFlightWidth);

        if (FW>9.9){
            alertUser("Can't set FlightWidth longer than 10m");
        }
        else{
            FW += 0.5;
            btnFw.setText("?????????\n" + FW + "m");
        }
        return FW;
    }

    public double onFwSubBtnTap(View view){
        Button btnFw = (Button) findViewById(R.id.btnFlightWidth);

        if (FW<3.1){
            alertUser("Can't set FlightWidth shorter than 3m");
        }
        else{
            FW -= 0.5;
            btnFw.setText("?????????\n" + FW + "m");
        }
        return FW;
    }

    public double onDisAddBtnTap(View view){
        Button btnDis= (Button) findViewById(R.id.btnABDistance);

        if (AB>99){
            alertUser("Can't set AtoB longer than 100m");
        }
        else{
            AB += 10;
            btnDis.setText("AB??????\n" + AB + "m");
        }
        return AB;
    }

    public double onDisSubBtnTap(View view){
        Button btnDis= (Button) findViewById(R.id.btnABDistance);

        if (AB<11){
            alertUser("Can't set AtoB shorter than 10m");
        }
        else{
            AB -= 10;
            btnDis.setText("AB??????\n" + AB + "m");
        }
        return AB;
    }
    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {

        this.mNaverMap = naverMap;

        naverMap.setLocationSource(mLocationSource);

        naverMap.setLocationTrackingMode(LocationTrackingMode.None);
        ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST_CODE);

        naverMap.setOnMapLongClickListener((point, coord) -> {
            mGuidePoint = new LatLng(coord.latitude, coord.longitude);
            mGuidedMarker.setPosition(mGuidePoint);
            mGuidedMarker.setMap(naverMap);
            if (GuideMode(new LatLong(coord.latitude, coord.longitude))) {
                addRecyclerViewText("????????? ?????????????????? ???????????????");
            }
            else {
                addRecyclerViewText("?????? ?????? ??????");
                mGuidedMarker.setMap(null);
            }
        }
        );
    }
}


