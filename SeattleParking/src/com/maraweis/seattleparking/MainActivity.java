package com.maraweis.seattleparking;

import java.io.BufferedReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;
import android.widget.Toast;
 
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;

public class MainActivity extends FragmentActivity implements LocationListener, ActionBar.TabListener {
	
	GoogleMap googleMap;
	private static final String STATE_SELECTED_NAVIGATION_ITEM = "selected_navigation_item"; //to restore tab position state
	MarkerOptions markerOptions; //search
	LatLng latLng; //search
	SearchView searchView;
	private static boolean created = false;
	private static LatLngBounds bounds; //current bounds of the user's view
	private HashMap<Marker, MarkerOptions> searchMarkers = new HashMap<Marker, MarkerOptions>();
	private boolean parkingMode = true; //True means show "All". False means show "Free"
	private Marker currentMarker;
	public static final String SHARED_PREFERENCES_NAME = "PREFERENCE_FILE_KEY";
	private MarkerOptions savedLocation;
	private Marker savedLocationMarker;
	private boolean savedLocationIsSet = false;
	
	// DON'T FUCKING TOUCH THIS METHOD UNLESS YOU MEAN IT
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	
		setContentView(R.layout.activity_main);  
		
		//set up action bar
		final ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		// for each section, add a tab to the action bar
		//TODO make these be xml elements instead of creating them here
		actionBar.addTab(actionBar.newTab().setText(R.string.tab_all).setTabListener(this));
		actionBar.addTab(actionBar.newTab().setText(R.string.tab_free).setTabListener(this));
		
		
		//gets google play availability status
		int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getBaseContext());
		if(status!=ConnectionResult.SUCCESS) { // Google Play Services are not available
            int requestCode = 10;
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(status, this, requestCode);
            dialog.show();
            
        // and never EVER change this
        } else { // Google Play Services are available
            // Getting reference to the SupportMapFragment of activity_main.xml
            SupportMapFragment fm = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
            // Getting GoogleMap object from the fragment
            googleMap = fm.getMap();
            currentMarker = googleMap.addMarker(new MarkerOptions().position(new LatLng(0,0)).visible(false));

    		googleMap.setOnCameraChangeListener(new OnCameraChangeListener() {

    			@Override
    			public void onCameraChange(CameraPosition position) {
    				bounds = googleMap.getProjection().getVisibleRegion().latLngBounds;
    				try {
    					populateMap();
    				} catch (IOException e) {
    					// TODO Auto-generated catch block
    					e.printStackTrace();
    				} catch (JSONException e) {
    					// TODO Auto-generated catch block
    					e.printStackTrace();
    				}
    			}
    		});
            zoomToCurrentLocation();
            //googleMap.setOnCameraChangeListener(null);
            created = true; 
        }
		
		//Event listener for info window click. Pops up dialog box
		googleMap.setOnInfoWindowClickListener(new OnInfoWindowClickListener() {
	        @Override
	        public void onInfoWindowClick(Marker marker) {
	        	DialogFragment newFragment = new parkingMarkerDialogFragment();
				newFragment.show(getFragmentManager(), "parkingmarker");   
	        }
	    });
		
		//Event listener for marker clicks. Saves the current marker info
		googleMap.setOnMarkerClickListener(new OnMarkerClickListener() {
			@Override
			public boolean onMarkerClick(Marker marker) {
				findByLatLong(marker); //saves marker info of last clicked marker
				return false;
			}
			
		});

	}
	
	protected void zoomToCurrentLocation () {
		// Enabling MyLocation Layer of Google Map
        googleMap.setMyLocationEnabled(true);
        // Getting LocationManager object from System Service LOCATION_SERVICE
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        // Creating a criteria object to retrieve provider
        Criteria criteria = new Criteria();
        // Getting the name of the best provider
        String provider = locationManager.getBestProvider(criteria, true);
        // Getting Current Location
        Location location = locationManager.getLastKnownLocation(provider);
        if(location != null){
            onLocationChanged(location);
        }
        locationManager.requestLocationUpdates(provider, 20000, 0, this);
	}
	
	public void onPause (){
		super.onPause();
	}
	
	public void onRestart (){
		super.onRestart();
		if (savedLocationIsSet) {
			googleMap.moveCamera(CameraUpdateFactory.newLatLng(savedLocation.getPosition()));
			googleMap.addMarker(savedLocation);
		}
	}
	
	public void onResume (){
		super.onResume();
	}
	
	protected void onStop (Bundle savedInstanceState){
		super.onStop();
	}	
	
	public void saveToPreferences() {
		if (savedLocationIsSet) {
			//TODO delete old favorites marker
		}
		LatLng latLng = currentMarker.getPosition();
		double latitude = latLng.latitude;
		double longitude = latLng.longitude;
		Context context = getApplicationContext();
		AppPrefs appPrefs = new AppPrefs(context);
		appPrefs.setLatitude(latitude);
		appPrefs.setLongitude(longitude);
		savedLocation = new MarkerOptions();
		savedLocation.position(latLng).icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_saved));
		savedLocationMarker = googleMap.addMarker(savedLocation);	
		savedLocationIsSet = true;
	}
	
	// For tabs. Restore the previously serialized current tab position.
	public void onRestoreInstanceState(Bundle savedInstanceState) {   
        if (savedInstanceState.containsKey(STATE_SELECTED_NAVIGATION_ITEM)) {
        	getActionBar().setSelectedNavigationItem(savedInstanceState.getInt(STATE_SELECTED_NAVIGATION_ITEM));
        }
    }
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		// Serialize the current tab position.
		outState.putInt(STATE_SELECTED_NAVIGATION_ITEM, getActionBar()
				.getSelectedNavigationIndex());
	}
	

	// Gets current lat/long and sets view to location
	public void onLocationChanged(Location location) {
		Log.v("ME","locationchanged");
		if (!created) {
			TextView tvLocation = (TextView) findViewById(R.id.tv_location);
			// Getting latitude of the current location
	        double latitude = location.getLatitude();
	        // Getting longitude of the current location
	        double longitude = location.getLongitude();
	        // Creating a LatLng object for the current location
	        LatLng latLng = new LatLng(latitude, longitude);
	        // Showing the current location in Google Map
	        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
	        // Zoom in the Google Map
	        googleMap.animateCamera(CameraUpdateFactory.zoomTo(15));
	        // Setting latitude and longitude in the TextView tv_location
	        tvLocation.setText("Latitude:" +  latitude  + ", Longitude:"+ longitude );
		}
	}
	
	// Fetches all the parking signs in the map area and places a marker for each one.
	public void populateMap() throws IOException, JSONException{
		// Get map LatLng
		double left = bounds.southwest.longitude;
		double top = bounds.northeast.latitude;
		double right = bounds.northeast.longitude;
		double bottom = bounds.southwest.latitude;
		//Log.v("ME","bounds:" + left + ":" + top + ":" + right + ":" + bottom);
		// Format query to fetch all parking signs and filter out unwanted signs (no parking, paid parking, etc.)
		String url = "http://data.seattle.gov/resource/it8u-sznv.json?$where=latitude>"+bottom
			+"%20AND%20latitude<"+top+"%20AND%20longitude>"+left+"%20AND%20longitude<"+right
			+"%20AND%28category='P1H'%20OR%20category='PPP'%20OR%20category='PTIML'%20OR%20category='PS'%20OR%20category='PRZ'%20OR%20category='PPEAK'%29";
		//Log.v("ME",url);
		// Get data from URL query.
		new populateTask().execute(url);
	}

	@Override
	public void onProviderDisabled(String provider) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	// Inflate the menu; this adds items to the action bar if it is present.
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        searchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
        searchView.setOnQueryTextListener(new OnQueryTextListener() {

			public boolean onQueryTextChange(String newText) {
				// TODO Do updating search maybe?
				return false;
			}

			//Search button press
			public boolean onQueryTextSubmit(String query) {
				//TODO collapse the searchview
				searchView.clearFocus();
				new GeocoderTask().execute(query); //all the zooming in and stuff for search happens here
				return false;
			}
        });
        
        //sets on click handler to pan to favorited location
        MenuItem saved = (MenuItem) menu.findItem(R.id.menu_star);
        saved.setOnMenuItemClickListener(new OnMenuItemClickListener() {

			@Override
			public boolean onMenuItemClick(MenuItem menu) {
				//TODO pan to favorite location
				//Log.v("ME","click!");
				retrieveSavedPreferences();			 
				return false;
			}
        	
        });
        return true;
    }
	
	public void retrieveSavedPreferences() {
		Context context = getApplicationContext();
		AppPrefs appPrefs = new AppPrefs(context);
		double latitude = appPrefs.getLatitude();
		double longitude = appPrefs.getLongitude();
		/*SharedPreferences prefs = this.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		double latitude = Double.longBitsToDouble(prefs.getLong("latitude", 0));
		double longitude = Double.longBitsToDouble(prefs.getLong("longitude", 0));*/
		CameraUpdate center=
		        CameraUpdateFactory.newLatLng(new LatLng(latitude,longitude));
		googleMap.moveCamera(center);
	}
	//***Tabs Behavior. NO TOUCHY****//
	public static class TabListener<T extends Fragment> implements ActionBar.TabListener {
	    private Fragment mFragment;
	    private final Activity mActivity;
	    private final String mTag;
	    private final Class<T> mClass;

	    /** Constructor used each time a new tab is created.
	      * @param activity  The host Activity, used to instantiate the fragment
	      * @param tag  The identifier tag for the fragment
	      * @param clz  The fragment's Class, used to instantiate the fragment
	      */
	    public TabListener(Activity activity, String tag, Class<T> clz) {
	        mActivity = activity;
	        mTag = tag;
	        mClass = clz;
	    }

	    /* The following are each of the ActionBar.TabListener callbacks */
	    public void onTabSelected(Tab tab, FragmentTransaction ft) {
	        // Check if the fragment is already initialized
	        if (mFragment == null) {
	            // If not, instantiate and add it to the activity
	            mFragment = Fragment.instantiate(mActivity, mClass.getName());
	            ft.add(android.R.id.content, mFragment, mTag);
	        } else {
	            // If it exists, simply attach it in order to show it
	            ft.attach(mFragment);
	        }
	        
	    }

	    // Detach the fragment, because another one is being attached
	    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
	        if (mFragment != null) {
	            ft.detach(mFragment);
	        }
	    }

	    // User selected the already selected tab. Usually do nothing.
	    public void onTabReselected(Tab tab, FragmentTransaction ft) {
	    }
	    
	}
	
    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
	// When the given tab is selected, show the tab contents in the container
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        Fragment fragment = new DummySectionFragment();
        Bundle args = new Bundle();
        args.putInt(DummySectionFragment.ARG_SECTION_NUMBER, tab.getPosition() + 1);
        fragment.setArguments(args);
        
        //how to show the tab contents in a container somewhere
        //getSupportFragmentManager().beginTransaction()
           //    .replace(R.id.container, fragment)
          //     .commit();
        parkingMode = !parkingMode;
        Log.v("ME","tab has been selected...");
        //this is where you tell it to do clear and do a query again...
        if (created) {
	        try {
				populateMap();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }
	
	//Finds the currently selected marker and saves both a reference to the marker
	public void findByLatLong(Marker marker) {
		Log.v("ME","findByLatLong");
		LatLng latLng = marker.getPosition();
		double latitude = Math.round(latLng.latitude*10000);
		latitude = latitude/10000;
		double longitude = Math.round(latLng.longitude*10000);
		longitude = longitude/10000;
		currentMarker = marker;
	}
	
    
	//dummy fragment for tabs
	public static class DummySectionFragment extends Fragment {
	        public DummySectionFragment() {
	        }

	        public static final String ARG_SECTION_NUMBER = "section_number";

	        @Override
	        public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                Bundle savedInstanceState) {
	            TextView textView = new TextView(getActivity());
	            textView.setGravity(Gravity.CENTER);
	            Bundle args = getArguments();
	            textView.setText(Integer.toString(args.getInt(ARG_SECTION_NUMBER)));
	            return textView;
	        }
	}
	
	//TODO method not finished. Called when Time button is clicked. Should create new time dialog
	//public void showSavedParking() {
		//Log.v("ME", "Show parking spot");
		
	    //DialogFragment newFragment = new TimePickerFragment();
	    //newFragment.show(getSupportFragmentManager(), "timePicker");	    
	//}
		
	//**SEARCH STUFF**//
	// An AsyncTask class for accessing the GeoCoding Web Service. Used for search
	private class GeocoderTask extends AsyncTask<String, Void, List<Address>>{
		
		@Override
		protected List<Address> doInBackground(String... locationName) {
			String location = locationName[0];
			location += ",Seattle";
			// Creating an instance of Geocoder class
			Geocoder geocoder = new Geocoder(getBaseContext());
			List<Address> addresses = null;
	 
			try {
				// Getting a maximum of 3 Address that matches the input text
				addresses = geocoder.getFromLocationName(location, 3);
	            } catch (IOException e) {
	                e.printStackTrace();
	            }
	            return addresses;
	        }
	 
	        protected void onPostExecute(List<Address> addresses) {
	 
	            if(addresses==null || addresses.size()==0){
	                Toast.makeText(getBaseContext(), "No Location found", Toast.LENGTH_SHORT).show();
	            }
	 
	            //clear search markers only!         
				for (HashMap.Entry<Marker, MarkerOptions> entry : searchMarkers.entrySet()) {
				    Marker marker = entry.getKey();
				    marker.remove();
				    searchMarkers.remove(marker);				    
				}
	 
	            // Adding Markers on Google Map for each matching address
	            for(int i=0;i<addresses.size();i++){
	 
	                Address address = (Address) addresses.get(i);
	 
	                // Creating an instance of GeoPoint, to display in Google Map
	                latLng = new LatLng(address.getLatitude(), address.getLongitude());
	 
	                String addressText = String.format("%s, %s",
	                address.getMaxAddressLineIndex() > 0 ? address.getAddressLine(0) : "",
	                address.getCountryName());
	 
	                markerOptions = new MarkerOptions();
	                markerOptions.position(latLng);
	                markerOptions.title(addressText);
	 
	                Marker marker = googleMap.addMarker(markerOptions);
	                searchMarkers.put(marker, markerOptions);
	                
	                // Locate the first location
	                if(i==0)
	                    googleMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
	        }   
	   }
	        
	}
	
	private class populateTask extends AsyncTask<String, Void, JSONArray> {
		@Override
		protected JSONArray doInBackground(String... url) {
			
			InputStream source;
			try {
				source = new URL(url[0]).openStream();
				BufferedReader in = new BufferedReader(new InputStreamReader(source,Charset.forName("UTF-8")));
	            StringBuilder sb = new StringBuilder();
	            	            int cp;
	            while ((cp = in.read()) != -1) {
	              sb.append((char) cp);
	            }
	            //Log.v("ME","StringBuilder:" + sb.toString());
	    		JSONArray jsonArray = new JSONArray(sb.toString());
	    		source.close();
	    		return jsonArray;
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			} 
			return new JSONArray();
		}
		
		protected void onPostExecute(JSONArray jsonArray) {
			googleMap.clear();
			if (!searchMarkers.isEmpty()) {
				for (HashMap.Entry<Marker, MarkerOptions> entry : searchMarkers.entrySet()) {
					markerOptions = entry.getValue();
	            	googleMap.addMarker(markerOptions);
	            }
				//Log.v("ME","searchMarkers ain't empty");
			}
			
			if (savedLocationIsSet) {
				googleMap.addMarker(savedLocation);	
			}
			
			try {
				JSONObject jsonObject;
				//Log.v("ME","jsonString:" + jsonArray.length());
	    		LatLng location;
	            for (int i = 0; i < jsonArray.length(); i++) { 	
	            	jsonObject = jsonArray.getJSONObject(i);
	    	        location = new LatLng(jsonObject.getDouble("latitude"), jsonObject.getDouble("longitude"));
	    	       
	    	        markerOptions = new MarkerOptions();
	    	        if (jsonObject.getString("category").equals("PPP")) {
		    	        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.bluemarker));
		    	    } else {
	    	        	markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.greenmarker));
	    	        }
	    	        markerOptions.position(location);
	    	        String blip = jsonObject.getString("customtext");
	    	        markerOptions.snippet(blip);
	    	        if (blip.length() > 20) {
	    	        	blip = jsonObject.getString("customtext").substring(0, 20);
	    	        	blip += "...";
	    	        }
	    	        markerOptions.title(blip);
	    	        //add marker to map
	    	        if (jsonObject.getString("category").equals("PPP") && parkingMode) {//don't add the marker if equals PPP and parkingMode is false
	    	        	//do nothing
	    	        } else {
	    	        	Marker marker = googleMap.addMarker(markerOptions);
	    	        	if (currentMarker.getPosition().equals(marker.getPosition())) {
		    				marker.showInfoWindow();
		    			}
	    	        }
	    	        
	            }
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}
	// Time picker stuff.
	/*public static class TimePickerFragment extends DialogFragment implements TimePickerDialog.OnTimeSetListener {

		@Override
		public void onTimeSet(TimePicker arg0, int arg1, int arg2) {
			// TODO Auto-generated method stub
			
		}*/
	
  
		/*@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
		// Use the current time as the default values for the picker
		final Calendar c = Calendar.getInstance();
		int hour = c.get(Calendar.HOUR_OF_DAY);
		int minute = c.get(Calendar.MINUTE);
		
		// Create a new instance of TimePickerDialog and return it
		return new TimePickerDialog(getActivity(), this, hour, minute,
				DateFormat.is24HourFormat(getActivity()));
		}
		
		public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
		// Do something with the time chosen by the user
		}
	}*/
	
	public class parkingMarkerDialogFragment extends DialogFragment {
		public Dialog onCreateDialog(Bundle savedInstanceState) {
	        // Use the Builder class for convenient dialog construction
	        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
	        builder.setMessage(currentMarker.getSnippet().toString())
	               .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
	                   public void onClick(DialogInterface dialog, int id) {
	                       // Save the spot!
	                	   saveToPreferences();
	                   }
	               })
	               .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
	                   public void onClick(DialogInterface dialog, int id) {
	                       // User cancelled the dialog, do nothing
	                   }
	               });
	        // Create the AlertDialog object and return it
	        return builder.create();
	    }
	}
	
	/*private class findOneTask extends AsyncTask<String, Void, JSONArray> {
		@Override
		protected JSONArray doInBackground(String... url) {
			
			InputStream source;
			try {
				source = new URL(url[0]).openStream();
				BufferedReader in = new BufferedReader(new InputStreamReader(source,Charset.forName("UTF-8")));
	            StringBuilder sb = new StringBuilder();
	            	            int cp;
	            while ((cp = in.read()) != -1) {
	              sb.append((char) cp);
	            }
	            //Log.v("ME","StringBuilder:" + sb.toString());
	    		JSONArray jsonArray = new JSONArray(sb.toString());
	    		source.close();
	    		return jsonArray;
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			} 
			return new JSONArray();
		}
		
		protected void onPostExecute(JSONArray jsonArray) {	
			Log.v("ME","onPostExecute");
			try {
				JSONObject jsonObject;
				//Log.v("ME","jsonString:" + jsonArray.length());
	    		LatLng location;
	            for (int i = 0; i < jsonArray.length(); i++) { 	
	        
	            	jsonObject = jsonArray.getJSONObject(i);
	            	Log.v("ME",jsonObject.toString());
	    	        location = new LatLng(jsonObject.getDouble("latitude"), jsonObject.getDouble("longitude"));
	    	       
	    	        markerOptions = new MarkerOptions();
	    	        if (jsonObject.getString("category").equals("PPP")) {
		    	        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.bluemarker));
		    	    } else {
	    	        	markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.greenmarker));
	    	        }
	    	        markerOptions.position(location);
	    	        markerOptions.title(jsonObject.getString("customtext"));
	    	        currentMarkerOptions = markerOptions;
	    	        Log.v("ME", jsonObject.toString());
	            }
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}*/
	
	public class AppPrefs {
		private static final String SHARED_PREFERENCES_NAME = "PREFERENCE_FILE_KEY";
		private SharedPreferences appSharedPrefs;
		private SharedPreferences.Editor prefsEditor;
		private String latitude = "latitude";
		private String longitude = "longitude";
		 
		public AppPrefs(Context context){
			this.appSharedPrefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Activity.MODE_PRIVATE);
			this.prefsEditor = appSharedPrefs.edit();
		}
		public double getLatitude() {
			return Double.longBitsToDouble(appSharedPrefs.getLong(latitude, 0));
		}
		 
		public void setLatitude(double lat) {
			prefsEditor.putLong("latitude", Double.doubleToLongBits(lat)).commit();
		}
		public double getLongitude() {
			return Double.longBitsToDouble(appSharedPrefs.getLong(longitude, 0));
		}
		 
		public void setLongitude(double longe) {
			prefsEditor.putLong("longitude", Double.doubleToLongBits(longe)).commit();
		}
	}
}