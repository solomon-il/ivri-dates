package com.shlominet.ivridate;

//Jewish calendar calculation in Java//
//http://www.david-greve.de/luach-code/jewish-java.html//

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import com.google.api.services.calendar.model.Events;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

    GoogleAccountCredential mCredential;
    private TextView mOutputText;
    private Button mCallApiButton;
    ProgressDialog mProgress;
    private Spinner daySpinner, monthSpinner;
    private Spinner yearsSpinner;
    private EditText eventDetailsET;
    private String selectedDay, selectedMonth;
    private String selectedYears;
    private int intSelectedDay, intSelectedMonth;
    private int intSelectedYears;
    private int flagMonthSelected = 0;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;


    private static final String[] SCOPES = { CalendarScopes.CALENDAR};
    private static final String PREF_ACCOUNT_NAME = "accountName";

    private CalendarImpl calendarImpl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        calendarImpl = new CalendarImpl();

        daySpinner = findViewById(R.id.day_spinner);
        monthSpinner = findViewById(R.id.month_spinner);

        daySpinner.setOnItemSelectedListener(new ItemSelectedListener());
        monthSpinner.setOnItemSelectedListener(new ItemSelectedListener());

        eventDetailsET = findViewById(R.id.event_details_et);

        yearsSpinner = findViewById(R.id.years_spinner);
        yearsSpinner.setOnItemSelectedListener(new ItemSelectedListener());
        intSelectedYears = 100;

        mCallApiButton = findViewById(R.id.call_api_btn);
        mCallApiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(selectedDay.equals("") || selectedMonth.equals("")) {
                    Toast.makeText(MainActivity.this, "לא נבחר תאריך", Toast.LENGTH_SHORT).show();
                    return;
                }
                if(eventDetailsET.getText().toString().trim().length() == 0) {
                    Toast.makeText(MainActivity.this, "לא מולאו פרטי האירוע", Toast.LENGTH_SHORT).show();
                    return;
                }
                mCallApiButton.setEnabled(false);
                mOutputText.setText("");

                getResultsFromApi();
                mCallApiButton.setEnabled(true);
            }
        });

        mOutputText = findViewById(R.id.output_text);
        mOutputText.setVerticalScrollBarEnabled(true);
        mOutputText.setMovementMethod(new ScrollingMovementMethod());

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("מתקשר עם יומן גוגל... ");

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());

    }

    private class ItemSelectedListener implements android.widget.AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            switch (adapterView.getId()) {
                case R.id.month_spinner:

                    String[] monthsItems = getResources().getStringArray(R.array.month_array);
                    selectedMonth = monthsItems[i];
                    intSelectedMonth = (i+5)%13+1;
                    if(i == 0) {
                        selectedMonth = "";
                        intSelectedMonth = 0;
                    }
                    Toast.makeText(MainActivity.this, "month: " + intSelectedMonth, Toast.LENGTH_SHORT).show();
                    //init days spinner
                    initDay(intSelectedMonth);
                    break;


                case R.id.day_spinner:
                    String[] daysItems = getResources().getStringArray(R.array.day_array30);
                    selectedDay = daysItems[i];
                    intSelectedDay = i;
                    Toast.makeText(MainActivity.this, "day: " + intSelectedDay, Toast.LENGTH_SHORT).show();
                    if(i == 0)
                        selectedDay = "";
                    break;

                case R.id.years_spinner:
                    String[] yearsItems = getResources().getStringArray(R.array.years);
                    selectedYears = yearsItems[i];
                    intSelectedYears = Integer.parseInt(selectedYears);
                    Toast.makeText(MainActivity.this, "day: " + intSelectedDay, Toast.LENGTH_SHORT).show();
                    break;

                default:
                    break;
            }
        }

        private void initDay(int month) {
            if(month == 0) {
                ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(MainActivity.this, R.array.day_array0, android.R.layout.simple_spinner_item);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                daySpinner.setAdapter(adapter);
            }
            else if ((month == 2) || (month == 4) || (month == 6) || (month == 10) || (month == 13)) {
                ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(MainActivity.this, R.array.day_array29, android.R.layout.simple_spinner_item);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                daySpinner.setAdapter(adapter);
            }
            else {
                ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(MainActivity.this, R.array.day_array30, android.R.layout.simple_spinner_item);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                daySpinner.setAdapter(adapter);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }
    }

    private class MakeRequestTask extends AsyncTask<Void, Void, ArrayList<Event>> {
        private com.google.api.services.calendar.Calendar mService = null;

        private Exception mLastError = null;

        MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.calendar.Calendar.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Google Calendar API Android Quickstart")
                    .build();
        }

        @Override
        protected void onPreExecute() {
            mOutputText.setText("");
            mProgress.show();
        }

        /**
         * Background task to call Google Calendar API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected ArrayList<Event> doInBackground(Void... params) {
            try {
                return setData();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        private ArrayList<Event> setData() throws IOException {


            int curGreYear = Calendar.getInstance().get(Calendar.YEAR);
            int curGreMonth = Calendar.getInstance().get(Calendar.MONTH)+1;
            int curGreDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);

            int curJewYear;

            //holds the cur date
            CalendarDate todayGreCD  = new CalendarDate(curGreDay, curGreMonth, curGreYear);

            //convert to jew date then extract the year
            int absolute = calendarImpl.absoluteFromGregorianDate(todayGreCD);
            CalendarDate dateJewish = calendarImpl.jewishDateFromAbsolute(absolute);
            curJewYear = dateJewish.getYear();

            //check if the first date to add is in the future
            CalendarDate dateJewCD  = new CalendarDate(intSelectedDay, intSelectedMonth, curJewYear);
            Log.d("aaa", "jew date: " + dateJewCD.toString());
            Log.d("aaa", "jew date: " + selectedDay + "' " + selectedMonth);
            int absolute2 = calendarImpl.absoluteFromJewishDate(dateJewCD);
            CalendarDate dateGregorian = calendarImpl.gregorianDateFromAbsolute(absolute2);

            if(todayGreCD.isLaterDate(dateGregorian))
                curJewYear++;

            ArrayList<Event> events = new ArrayList<>(100);

            //convert the selected jew date to greg then add to calendar

            for(int year = curJewYear; year < (curJewYear+intSelectedYears); year++) {
                dateJewCD  = new CalendarDate(intSelectedDay, intSelectedMonth, year);
                Log.d("aaa", "jew date: " + dateJewCD.toString());
                Log.d("aaa", "jew date: " + selectedDay + "' " + selectedMonth);
                absolute2 = calendarImpl.absoluteFromJewishDate(dateJewCD);
                dateGregorian = calendarImpl.gregorianDateFromAbsolute(absolute2);

                if(todayGreCD.isLaterDate(dateGregorian))
                    dateGregorian.setYear(dateGregorian.getYear()+1);


                Log.d("aaa", "gre date: " + dateGregorian.toString());

                //format: "2018-05-20" or "2018-05-28T09:00:00-07:00"
                String startDateTimeStr = ""+ dateGregorian.getYear()+ "-"+
                        String.format("%02d", dateGregorian.getMonth())+ "-"+
                        String.format("%02d", dateGregorian.getDay());


                Event event = new Event()
                        .setSummary(eventDetailsET.getText().toString())
//                        .setLocation("Test setLocation()")
                        .setDescription("" + selectedDay + "' " + selectedMonth);

                DateTime startDateTime = new DateTime(startDateTimeStr);
                EventDateTime start = new EventDateTime().setDate(startDateTime);
                event.setStart(start);

                DateTime endDateTime = new DateTime(startDateTimeStr);
                EventDateTime end = new EventDateTime().setDate(endDateTime);
                event.setEnd(end);


                EventReminder[] reminderOverrides = new EventReminder[] {
                        new EventReminder().setMethod("email").setMinutes(24 * 60),
                        new EventReminder().setMethod("popup").setMinutes(10),
                };
                Event.Reminders reminders = new Event.Reminders()
                        .setUseDefault(false)
                        .setOverrides(Arrays.asList(reminderOverrides));
                event.setReminders(reminders);

                event.setTransparency("transparent");


                String calendarId = "primary";
                event = mService.events().insert(calendarId, event).execute();

                events.add(event);

                Log.d("aaa", "Event created: \n");
                Log.d("aaa", "Event getHtmlLink:"+ event.getHtmlLink());
                Log.d("aaa", "Event getSummary:"+ event.getSummary());
                Log.d("aaa", "Event getStart:"+ event.getStart());
                Log.d("aaa", "Event getLocation:"+ event.getLocation());
            }
            return events;
        }


        @Override
        protected void onPostExecute(ArrayList<Event> events) {
            mProgress.hide();
            if (events == null || events.size() == 0) {
                mOutputText.setText("No results returned.");
            } else {
                String eventsStr = "";
                for (Event event : events) {
                    eventsStr += event.getStart().getDate().toStringRfc3339() + "\n";
                }
                mOutputText.setText(eventsStr);
            }
        }
        @Override
        protected void onCancelled() {
            mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    mOutputText.setText("The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                mOutputText.setText("Request cancelled.");
            }
        }

//        /**
//         * Fetch a list of the next 10 events from the primary calendar.
//         * @return List of Strings describing returned events.
//         * @throws IOException
//         */
//
//        private List<String> getDataFromApi() throws IOException {
//                // List the next 10 events from the primary calendar.
//                DateTime now = new DateTime(System.currentTimeMillis());
//                List<String> eventStrings = new ArrayList<String>();
//                Events events = mService.events().list("primary")
//                        .setMaxResults(10)
//                        .setTimeMin(now)
//                        .setOrderBy("startTime")
//                        .setSingleEvents(true)
//                        .execute();
//                List<Event> items = events.getItems();
//
//                for (Event event : items) {
//                    DateTime start = event.getStart().getDateTime();
//                    if (start == null) {
//                        // All-day events don't have start times, so just use
//                        // the start date.
//                        start = event.getStart().getDate();
//                    }
//                    eventStrings.add(
//                            String.format("%s (%s)", event.getSummary(), start));
//                }
//                return eventStrings;
//            }


//        @Override
//        protected void onPostExecute(List<String> output) {
//            mProgress.hide();
//            if (output == null || output.size() == 0) {
//                mOutputText.setText("No results returned.");
//            } else {
//                output.add(0, "Data retrieved using the Google Calendar API:");
//                mOutputText.setText(TextUtils.join("\n", output));
//            }
//        }

    }

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (! isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (! isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
        } else {
            new MakeRequestTask(mCredential).execute();
        }
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    mOutputText.setText(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
    */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }

    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }
}
