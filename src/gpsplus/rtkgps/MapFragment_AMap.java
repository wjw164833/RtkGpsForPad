package gpsplus.rtkgps;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.nfc.Tag;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;


import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.CoordinateConverter;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.services.core.ServiceSettings;


import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import gpsplus.rtkgps.view.GTimeView;
import gpsplus.rtkgps.view.SolutionView;
import gpsplus.rtkgps.view.StreamIndicatorsView;
import gpsplus.rtklib.RtkCommon;
import gpsplus.rtklib.RtkCommon.Position3d;
import gpsplus.rtklib.RtkControlResult;
import gpsplus.rtklib.RtkServerStreamStatus;
import gpsplus.rtklib.Solution;
import gpsplus.rtklib.constants.SolutionStatus;

import static junit.framework.Assert.assertNotNull;

public class MapFragment_AMap extends Fragment {

    private static final boolean DBG = BuildConfig.DEBUG & true;
    static final String TAG = MapFragment_AMap.class.getSimpleName();

    private static final String SHARED_PREFS_NAME = "map";
    private static final String PREFS_TITLE_SOURCE = "title_source";
    private static final String PREFS_SCROLL_X = "scroll_x";
    private static final String PREFS_SCROLL_Y = "scroll_y";
    private static final String PREFS_ZOOM_LEVEL = "zoom_level";


    private Timer mStreamStatusUpdateTimer;
    private RtkServerStreamStatus mStreamStatus;


    private RtkControlResult mRtkStatus;

    @BindView(R.id.streamIndicatorsView) StreamIndicatorsView mStreamIndicatorsView;
    @BindView(R.id.map_container) ViewGroup mMapViewContainer;
    @BindView(R.id.gtimeView) GTimeView mGTimeView;
    @BindView(R.id.solutionView) SolutionView mSolutionView;

    public static final LatLng BEIJING = new LatLng(39.90403, 116.407525);// ??????????????????
    public static final LatLng ZHONGGUANCUN = new LatLng(39.983456, 116.3154950);// ???????????????????????????
    public static final LatLng SHANGHAI = new LatLng(31.238068, 121.501654);// ??????????????????
    public static final LatLng FANGHENG = new LatLng(39.989614, 116.481763);// ???????????????????????????
    public static final LatLng CHENGDU = new LatLng(30.679879, 104.064855);// ??????????????????
    public static final LatLng XIAN = new LatLng(34.341568, 108.940174);// ??????????????????
    public static final LatLng ZHENGZHOU = new LatLng(34.7466, 113.625367);// ??????????????????

    private MapView mMapView;
    private AMap aMap;
    private Polyline mPathOverlay;

    public MapFragment_AMap() {
        mStreamStatus = new RtkServerStreamStatus();
        mRtkStatus = new RtkControlResult();

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
      }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final Context context;
        final DisplayMetrics dm;

        View v = inflater.inflate(R.layout.fragment_amap, container, false);
        ButterKnife.bind(this, v);

        context = inflater.getContext();
        dm = context.getResources().getDisplayMetrics();

        final int actionBarHeight;
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true))
        {
            actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data,getResources().getDisplayMetrics());
        }else {
            actionBarHeight = 48;
        }
//ON WORK
        //modified provider for setting User-Agent to Android wich is mandatory for geoportail
        //also trust all certificates for communicating via https
        //????????????????????????
        ServiceSettings.updatePrivacyShow(context, true, true);
        ServiceSettings.updatePrivacyAgree(context,true);
        mMapView = (MapView) v.findViewById(R.id.map);
        //???activity??????onCreate?????????mMapView.onCreate(savedInstanceState)???????????????
        mMapView.onCreate(savedInstanceState);
        mapinit();

        return v;
    }

    List<LatLng> mPolylinelatLngs;
    private void mapinit(){
        //??????????????????????????????
        if (aMap == null) {
            aMap = mMapView.getMap();
        }
        //????????????????????????
        aMap.moveCamera(
                CameraUpdateFactory.newCameraPosition(new CameraPosition(XIAN
                        , 18, 0, 0)));
        //????????????
        aMap.showIndoorMap(true);
//        aMap.setMyLocationEnabled(true);
//        aMap.getUiSettings().setMyLocationButtonEnabled(true);// ????????????????????????????????????
        //?????????????????????
        aMap.getUiSettings().setCompassEnabled(true);
        aMap.getUiSettings().setScaleControlsEnabled(true);
        aMap.setMaxZoomLevel(25);
        //????????????
        mPolylinelatLngs = new ArrayList<LatLng>();
        mPathOverlay =aMap.addPolyline(new PolylineOptions().
                addAll(mPolylinelatLngs).width(10).color(Color.argb(255, 255, 45, 1)));
    }

    @Override
    public void onStart() {
        super.onStart();

        // XXX
        mStreamStatusUpdateTimer = new Timer();
        mStreamStatusUpdateTimer.scheduleAtFixedRate(
                new TimerTask() {
                    Runnable updateStatusRunnable = new Runnable() {
                        @Override
                        public void run() {
                            MapFragment_AMap.this.updateStatus();
                        }
                    };
                    @Override
                    public void run() {
                        Activity a = getActivity();
                        if (a == null) return;
                        a.runOnUiThread(updateStatusRunnable);
                    }
                }, 200, 1000);
    }

    @Override
    public void onPause() {
        super.onPause();
        saveMapPreferences();
        //???activity??????onPause?????????mMapView.onPause ()????????????????????????
        mMapView.onPause();


    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_amap, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final int checked;

        if (mMapView == null ) return;
        if (aMap == null) {
            aMap = mMapView.getMap();
        }

        switch (aMap.getMapType()){
            case AMap.MAP_TYPE_NORMAL:
                checked = R.id.menu_amap_mode_basicmap;
                break;
            case AMap.MAP_TYPE_SATELLITE:
                checked = R.id.menu_amap_mode_rsmap;
                break;
            case AMap.MAP_TYPE_NIGHT:
                checked = R.id.menu_amap_mode_nightmap;
                break;
            case AMap.MAP_TYPE_NAVI:
                checked = R.id.menu_amap_mode_navimap;
                break;
            default:
                checked = R.id.menu_amap_mode_basicmap;
                break;
        }

        menu.findItem(checked).setChecked(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadMapPreferences();
        //???activity??????onResume?????????mMapView.onResume ()???????????????????????????
        mMapView.onResume();
    }

    @Override
    public void onStop() {
        super.onStop();
        //mPathOverlay.clearPath();
        mStreamStatusUpdateTimer.cancel();
        mStreamStatusUpdateTimer = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        //???activity??????onDestroy?????????mMapView.onDestroy()???????????????
        mMapView.onDestroy();
        mMapView = null;

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //???activity??????onSaveInstanceState?????????mMapView.onSaveInstanceState (outState)??????????????????????????????
        mMapView.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (aMap == null) {
            aMap = mMapView.getMap();
        }

        switch (item.getItemId()) {
        case R.id.menu_amap_mode_basicmap: //????????????
            aMap.setMapType(AMap.MAP_TYPE_NORMAL);// ??????????????????
            break;
        case R.id.menu_amap_mode_rsmap://????????????
            aMap.setMapType(AMap.MAP_TYPE_SATELLITE);// ??????????????????
            break;
        case R.id.menu_amap_mode_nightmap://????????????
            aMap.setMapType(AMap.MAP_TYPE_NIGHT);//??????????????????
            break;
        case R.id.menu_amap_mode_navimap://????????????
            aMap.setMapType(AMap.MAP_TYPE_NAVI);//??????????????????
            break;
        default:
            return super.onOptionsItemSelected(item);
        }


        return true;
    }

    void updateStatus() {
        MainActivity ma;
        RtkNaviService rtks;
        int serverStatus;

        // XXX
        ma = (MainActivity)getActivity();

        if (ma == null) return;

        rtks = ma.getRtkService();
        if (rtks == null) {
            serverStatus = RtkServerStreamStatus.STATE_CLOSE;
            mStreamStatus.clear();
        }else {
            rtks.getStreamStatus(mStreamStatus);
            rtks.getRtkStatus(mRtkStatus);
            serverStatus = rtks.getServerStatus();
//            appendSolutions(rtks.readSolutionBuffer());
            setMarkerPos(mRtkStatus.getSolution());
            mGTimeView.setTime(mRtkStatus.getSolution().getTime());
            mSolutionView.setStats(mRtkStatus);
        }

        assertNotNull(mStreamStatus.mMsg);

        mStreamIndicatorsView.setStats(mStreamStatus, serverStatus);
    }

    private void saveMapPreferences() {

        getActivity()
            .getSharedPreferences(SHARED_PREFS_NAME,Context.MODE_PRIVATE)
            .edit()
//            .putString(PREFS_TITLE_SOURCE, getTileSourceName())
            .putInt(PREFS_SCROLL_X, mMapView.getScrollX())
            .putInt(PREFS_SCROLL_Y, mMapView.getScrollY())
//            .putFloat(PREFS_ZOOM_LEVEL, aMap.getCameraPosition().zoom)
            .commit();


    }

    private void loadMapPreferences() {
        SharedPreferences prefs = getActivity().getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);


//        mMapView.getController().setZoom(prefs.getInt(PREFS_ZOOM_LEVEL, 1));

        mMapView.scrollTo(
                prefs.getInt(PREFS_SCROLL_X, 0),
                prefs.getInt(PREFS_SCROLL_Y, 0)
                );
    }


    private void appendSolutions(Solution solutions[]) {
        LatLng desLatLng = null;
        for (Solution s:solutions) {
            desLatLng = SolutionToLocation(s);
            if (desLatLng != null) {
                mPolylinelatLngs.add(desLatLng);
                mPathOverlay.setPoints(mPolylinelatLngs);
//                Log.e("aMap_appendSolutions", mPathOverlay.isVisible() ? "??????":"?????????");
            }
        }

    }

    private LatLng SolutionToLocation(Solution s){
        Position3d pos;
        if (MainActivity.getDemoModeLocation().isInDemoMode() && RtkNaviService.mbStarted) {
            pos=MainActivity.getDemoModeLocation().getPosition();
            if (pos == null)
                return null;
        }else{
            if (s.getSolutionStatus() == SolutionStatus.NONE) {
                return null;
            }
            pos = RtkCommon.ecef2pos(s.getPosition());
        }

        Location mLastLocation = new Location("");
        mLastLocation.setTime(s.getTime().getUtcTimeMillis());
        mLastLocation.setLatitude(Math.toDegrees(pos.getLat()));
        mLastLocation.setLongitude(Math.toDegrees(pos.getLon()));
        mLastLocation.setAltitude(pos.getHeight());

        //????????????
        LatLng sourceLatLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
        CoordinateConverter converter  = new CoordinateConverter(this.getActivity());
        // CoordType.GPS ?????????????????????
        converter.from(CoordinateConverter.CoordType.GPS);
        // sourceLatLng?????????????????? LatLng??????
        converter.coord(sourceLatLng);
        // ??????????????????
        LatLng desLatLng = converter.convert();
//        Log.e("amap",desLatLng.latitude + "," + desLatLng.longitude);


        return desLatLng;
    }

    Marker marker;
    MarkerOptions markerOption;
    //???????????????????????????
    private void setMarkerPos(Solution s) {

        LatLng desLatLng = SolutionToLocation(s);
        if(desLatLng == null) return;

        //????????????
        CameraPosition position = aMap.getCameraPosition();
        aMap.moveCamera(
                CameraUpdateFactory.newCameraPosition(new CameraPosition(
                        desLatLng, position.zoom, position.tilt, position.bearing)));
        //??????
        if (marker == null) {
            marker = aMap.addMarker(new MarkerOptions().position(desLatLng)
                    .icon(BitmapDescriptorFactory
                            .defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        }
        else {
            marker.setPosition(desLatLng);
        }
    }

}
