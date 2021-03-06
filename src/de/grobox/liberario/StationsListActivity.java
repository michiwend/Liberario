/*    Liberario
 *    Copyright (C) 2013 Torsten Grote
 *
 *    This program is Free Software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as
 *    published by the Free Software Foundation, either version 3 of the
 *    License, or (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.grobox.liberario;

import java.util.ArrayList;
import java.util.List;

import de.grobox.liberario.R;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.LineDestination;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.NearbyStationsResult;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.dto.StationDepartures;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public class StationsListActivity extends Activity {
	private LinearLayout main;
	private LocationManager locationManager;
	private Menu mMenu;
	private List<Location> mStations;
	boolean gps;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_stations_list);

		getActionBar().setDisplayHomeAsUpEnabled(true);

		main = (LinearLayout) findViewById(R.id.activity_stations_list);
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

		Intent intent = getIntent();
		if(intent.getAction() != null && intent.getAction().equals("de.grobox.liberario.LIST_NEARBY_STATIONS")) {
			NearbyStationsResult stations = (NearbyStationsResult) intent.getSerializableExtra("de.schildbach.pte.dto.NearbyStationsResult");
			gps = true;

			mStations = stations.stations;
		}
		else {
			Location station = (Location) intent.getSerializableExtra("de.schildbach.pte.dto.Location");
			gps = false;

			mStations = new ArrayList<Location>();
			mStations.add(station);
		}
		addStations();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.stations_activity_actions, menu);
		mMenu = menu;

		if(Preferences.getShowPlatforms(this)) {
			mMenu.findItem(R.id.action_platforms).setIcon(R.drawable.ic_menu_hide_platforms);
		} else {
			mMenu.findItem(R.id.action_platforms).setIcon(R.drawable.ic_menu_show_platforms);
		}

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();

				return true;
			case R.id.action_refresh:
				int i = 0;
				for(final Location station : mStations) {
					LinearLayout stationView = (LinearLayout) main.getChildAt(i++);
					LinearLayout depList = (LinearLayout) main.getChildAt(i++);
					// one more increment for horizontal divider
					i++;

					// show progress bar
					depList.getChildAt(0).setVisibility(View.VISIBLE);

					// remove old departures
					depList.removeViews(1, depList.getChildCount() - 1);

					// get new departures
					AsyncQueryDeparturesTask query_stations = new AsyncQueryDeparturesTask(this, stationView, station.id);
					query_stations.execute();
				}

				return true;
			case R.id.action_platforms:
				boolean show = !Preferences.getShowPlatforms(this);

				// change action icon
				if(show) {
					mMenu.findItem(R.id.action_platforms).setIcon(R.drawable.ic_menu_hide_platforms);
				} else {
					mMenu.findItem(R.id.action_platforms).setIcon(R.drawable.ic_menu_show_platforms);
				}
				showPlatforms(show);
				Preferences.setShowPlatforms(this, show);

				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void addStations() {
		android.location.Location cur_loc = new android.location.Location("");
		android.location.Location sta_loc = new android.location.Location("");

		if(gps) {
			// get last known position
			for(String provider : locationManager.getProviders(true)) {
				// Register the listener with the Location Manager to receive location updates
				android.location.Location tmp_loc = locationManager.getLastKnownLocation(provider);
				if(tmp_loc != null && tmp_loc.getTime() > cur_loc.getTime()) {
					cur_loc = tmp_loc;
				}
				Log.d(getClass().getSimpleName(), "Received last known location: " + cur_loc.toString());
			}
		}

		for(final Location station : mStations) {
			LinearLayout stationView = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.station, null);

			TextView stationNameView = (TextView) stationView.findViewById(R.id.stationNameView);
			stationNameView.setText(station.uniqueShortName());

			if(gps) {
				// transform station location into android format
				sta_loc.setLatitude(station.lat / 1E6);
				sta_loc.setLongitude(station.lon / 1E6);

				TextView distanceView = (TextView) stationView.findViewById(R.id.distanceView);
				distanceView.setText(String.valueOf(Math.round(cur_loc.distanceTo(sta_loc))) + " m");

				stationView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						ViewGroup parent = ((ViewGroup) view.getParent());
						View v = parent.getChildAt(parent.indexOfChild(view) + 1);
						if(v != null) {
							if(v.getVisibility() == View.GONE) {
								v.setVisibility(View.VISIBLE);
							}
							else if(v.getVisibility() == View.VISIBLE) {
								v.setVisibility(View.GONE);
							}
						}
					}
				});
			}
			else {
				stationView.setClickable(false);
			}

			main.addView(stationView);

			AsyncQueryDeparturesTask query_stations = new AsyncQueryDeparturesTask(this, stationView, station.id);
			query_stations.execute();

			LinearLayout depList = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.departure_list, null);
			if(!gps) {
				// show departures right away if we only have one station
				depList.setVisibility(View.VISIBLE);
			}
			main.addView(depList);

			main.addView(TripsActivity.getDivider(this));
		}
	}

	public void addDepartures(View v, QueryDeparturesResult result) {
		LinearLayout layout = (LinearLayout) main.getChildAt(main.indexOfChild(v) + 1);

		boolean have_lines = false;

		for(final StationDepartures stat_dep : result.stationDepartures) {
			if(stat_dep.departures.size() == 0) {
				// hide progress bar
				layout.getChildAt(0).setVisibility(View.GONE);

				// there's no departures here, so exit
				return;
			}

			LinearLayout stationView = (LinearLayout) v;
			ViewGroup stationLineLayout = (ViewGroup) stationView.findViewById(R.id.lineLayout);

			// add line boxes if available
			if(stat_dep.lines != null) {
				for(LineDestination line : stat_dep.lines) {
					TripsActivity.addLineBox(this, stationLineLayout, line.line);
				}
				stationLineLayout.setVisibility(View.VISIBLE);
				have_lines = true;
			}

			// get maximum number of departures
			int max = AsyncQueryDeparturesTask.max_departures;
			if(stat_dep.departures.size() <= max) {
				max = stat_dep.departures.size() - 1;
			}

			for(final Departure dep : stat_dep.departures.subList(0, max)) {
				LinearLayout view = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.departure, null);

				TextView timeView = (TextView) view.findViewById(R.id.timeView);
				timeView.setText(DateUtils.getTime(dep.plannedTime));

				if(dep.predictedTime != null) {
					long delay = dep.predictedTime.getTime() - dep.plannedTime.getTime();

					if(delay > 0) {
						TextView delayView = (TextView) view.findViewById(R.id.delayView);
						delayView.setText("+" + Long.toString(delay / 1000 / 60));
					}
				}

				if(dep.line != null) {
					LinearLayout lineLayout = (LinearLayout) view.findViewById(R.id.lineLayout);
					TripsActivity.addLineBox(this, lineLayout, dep.line, 0);

					// add lines also to departing station row
					if(!have_lines) {
						TripsActivity.addLineBox(this, stationLineLayout, dep.line, true);
						stationLineLayout.setVisibility(View.VISIBLE);
					}
				}

				TextView destinationView = (TextView) view.findViewById(R.id.destinationView);
				destinationView.setText(dep.destination.uniqueShortName());

				// show platform/position according to user preference and availability
				if(dep.position != null) {
					TextView positionView = (TextView) view.findViewById(R.id.positionView);
					positionView.setText(dep.position);
					positionView.setVisibility(Preferences.getShowPlatforms(this) ? View.VISIBLE : View.GONE);
				}

				// hide progress bar
				layout.getChildAt(0).setVisibility(View.GONE);

				layout.addView(view);
			}
		}

	}

	private void showPlatforms(boolean show) {
		// loop over each station
		for(int i = 0; i < main.getChildCount(); ++i) {
			View v = (View) main.getChildAt(i);
			if(v instanceof LinearLayout && v.getId() == R.id.departureListLayout) {
				LinearLayout dep = (LinearLayout) v;
				// loop over each departure
				for(int j = 0; j < dep.getChildCount(); ++j) {
					TextView positionView = (TextView) dep.getChildAt(j).findViewById(R.id.positionView);
					if(positionView != null) {
						// collapse/expand platforms on departures
						positionView.setVisibility(show ? View.VISIBLE : View.GONE);
					}
				}
			}
		}
	}

}
