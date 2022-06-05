package com.pusketiri.simpleiot;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.regions.Regions;

import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.helper.DateAsXAxisLabelFormatter;
import com.jjoe64.graphview.helper.StaticLabelsFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.pusketiri.simpleiot.databinding.FragmentMainBinding;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MainFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MainFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private String[] topics = {
            "temperature/reading",
            "moisture/reading",
            "cooler/switch",
            "cooler/reading"
    };

    // Customer specific IoT endpoint
    // AWS Iot CLI describe-endpoint call returns: XXXXXXXXXX.iot.<region>.amazonaws.com,
    private static final String CUSTOMER_SPECIFIC_ENDPOINT = "a20giyqb0hvu11-ats.iot.eu-central-1.amazonaws.com";

    private static final String LOG_TAG = "Ivan";

    // Cognito pool ID. For this app, pool needs to be unauthenticated pool with
    // AWS IoT permissions.
    private static final String COGNITO_POOL_ID = "eu-central-1:613530a9-0cbf-4ffe-946a-f8942192f157";

    // Region of AWS IoT
    private static final Regions MY_REGION = Regions.EU_CENTRAL_1;

    private FragmentMainBinding binding;

    AWSIotMqttManager mqttManager;
    String clientId;

    CognitoCachingCredentialsProvider credentialsProvider;

    LineGraphSeries<DataPoint> temp_series, moisture_series;
    private Thread temp_thread;
    private boolean plot_temp = true;

    public MainFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment main.
     */
    // TODO: Rename and change types and number of parameters
    public static MainFragment newInstance(String param1, String param2) {
        MainFragment fragment = new MainFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        //requireActivity().getSupportActionBar().hide();

        // MQTT client IDs are required to be unique per AWS IoT account.
        // This UUID is "practically unique" but does not _guarantee_
        // uniqueness.
        clientId = UUID.randomUUID().toString();
        //tvClientId.setText(clientId);

        // Initialize the AWS Cognito credentials provider
        credentialsProvider = new CognitoCachingCredentialsProvider(
                requireActivity(), // context
                COGNITO_POOL_ID, // Identity Pool ID
                MY_REGION // Region
        );

        // MQTT Client
        mqttManager = new AWSIotMqttManager(clientId, CUSTOMER_SPECIFIC_ENDPOINT);

        // Create connection
        try {
            mqttManager.connect(credentialsProvider, new AWSIotMqttClientStatusCallback() {
                @Override
                public void onStatusChanged(final AWSIotMqttClientStatus status,
                                            final Throwable throwable) {
                    Log.d(LOG_TAG, "Status = " + String.valueOf(status));

                    requireActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (status == AWSIotMqttClientStatus.Connecting) {
                                binding.mqttStatusMessage.setText("Connecting...");

                            } else if (status == AWSIotMqttClientStatus.Connected) {
                                binding.mqttStatusMessage.setText("Connected");

                            } else if (status == AWSIotMqttClientStatus.Reconnecting) {
                                if (throwable != null) {
                                    Log.e(LOG_TAG, "Connection error.", throwable);
                                }
                                binding.mqttStatusMessage.setText("Reconnecting");
                            } else if (status == AWSIotMqttClientStatus.ConnectionLost) {
                                if (throwable != null) {
                                    Log.e(LOG_TAG, "Connection error.", throwable);
                                    throwable.printStackTrace();
                                }
                                binding.mqttStatusMessage.setText("Disconnected");
                            } else {
                                binding.mqttStatusMessage.setText("Disconnected");

                            }
                        }
                    });
                }
            });
        } catch (final Exception e) {
            Log.e(LOG_TAG, "Connection error.", e);
            binding.mqttStatusMessage.setText("Error! " + e.getMessage());
        }

        temp_series = new LineGraphSeries<DataPoint>();
        moisture_series = new LineGraphSeries<DataPoint>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentMainBinding.inflate(inflater, container, false);
        binding.getRoot().getRootView().setBackgroundColor(Color.WHITE);
        //binding.getRoot().getRootView().

        /*binding.tempGraph.getViewport().setScalable(true); // enables horizontal zooming and scrolling
        binding.tempGraph.getViewport().setScalableY(true); // enables vertical zooming and scrolling
        binding.tempGraph.getViewport().setYAxisBoundsManual(true); // Prevents auto-rescaling the Y-axis
        binding.tempGraph.getViewport().setXAxisBoundsManual(true); // Prevents auto-rescaling the X-axis
        binding.tempGraph.setTitleTextSize(96);*/
        /*binding.tempGraph.setTitle("Temperature measurements");
        binding.tempGraph.setTitleColor(Color.BLACK);
        binding.tempGraph.setTitleTextSize(64);
        binding.tempGraph.getGridLabelRenderer().setHumanRounding(true);
        //binding.tempGraph.getGridLabelRenderer().setVerticalAxisTitleTextSize(64);

        binding.tempGraph.getGridLabelRenderer().setVerticalAxisTitle("T[°C]");
        binding.tempGraph.getGridLabelRenderer().setVerticalLabelsVisible(true);
        binding.tempGraph.getGridLabelRenderer().setVerticalAxisTitleColor(Color.BLACK);

        binding.tempGraph.getGridLabelRenderer().setGridColor(Color.BLACK);
        //binding.tempGraph.getGridLabelRenderer().setHorizontalAxisTitleTextSize(64);

        binding.tempGraph.getGridLabelRenderer().setHorizontalAxisTitle("Time [s]");
        binding.tempGraph.getGridLabelRenderer().setHorizontalLabelsVisible(true);
        binding.tempGraph.getGridLabelRenderer().setHorizontalAxisTitleColor(Color.BLACK);

        binding.moistureGraph.setTitle("Moisture measurements");
        binding.moistureGraph.setTitleColor(Color.BLACK);
        binding.moistureGraph.setTitleTextSize(64);
        binding.moistureGraph.getGridLabelRenderer().setHumanRounding(true);
        //binding.tempGraph.getGridLabelRenderer().setVerticalAxisTitleTextSize(64);

        binding.moistureGraph.getGridLabelRenderer().setVerticalAxisTitle("Moisture");
        binding.moistureGraph.getGridLabelRenderer().setVerticalLabelsVisible(true);
        binding.moistureGraph.getGridLabelRenderer().setVerticalAxisTitleColor(Color.BLACK);

        binding.moistureGraph.getGridLabelRenderer().setGridColor(Color.BLACK);
        //binding.tempGraph.getGridLabelRenderer().setHorizontalAxisTitleTextSize(64);

        binding.moistureGraph.getGridLabelRenderer().setHorizontalAxisTitle("Time [s]");
        binding.moistureGraph.getGridLabelRenderer().setHorizontalLabelsVisible(true);
        binding.moistureGraph.getGridLabelRenderer().setHorizontalAxisTitleColor(Color.BLACK);

        //binding.tempGraph.addSeries(tmp);
        //binding.moistureGraph.addSeries(tmp);
*/
        return binding.getRoot();
        //return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mqttManager.disconnect();
        binding = null;
    }

    AWSIotMqttNewMessageCallback new_message_callback = new AWSIotMqttNewMessageCallback() {
        @Override
        public void onMessageArrived(final String topic, final byte[] data) {
            requireActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String message = new String(data, "UTF-8");
                        if(topic.equals(topics[0])) {
                            JSONObject obj = new JSONObject(message);
                            int value = Integer.parseInt(obj.getString("reading"));
                            Random rand = new Random();
                            //value = rand.nextInt(30) % value;
                            //int epoch = Integer.parseInt(obj.getString("timestamp"));
                            //@SuppressLint("SimpleDateFormat")
                            //String date = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new java.util.Date(epoch*1000));

                            Date currentTime = Calendar.getInstance().getTime();

                            binding.tempGraph.setTitle("Temperature measurements");
                            binding.tempGraph.setTitleColor(Color.BLACK);
                            binding.tempGraph.setTitleTextSize(64);
                            binding.tempGraph.getGridLabelRenderer().setHumanRounding(true);
                            //binding.tempGraph.getGridLabelRenderer().setVerticalAxisTitleTextSize(64);

                            binding.tempGraph.getGridLabelRenderer().setVerticalAxisTitle("T[°C]");
                            binding.tempGraph.getGridLabelRenderer().setVerticalLabelsVisible(true);
                            binding.tempGraph.getGridLabelRenderer().setVerticalAxisTitleColor(Color.BLACK);

                            binding.tempGraph.getGridLabelRenderer().setGridColor(Color.BLACK);
                            binding.tempGraph.getGridLabelRenderer().setVerticalLabelsColor(Color.BLACK);
                            //binding.tempGraph.getGridLabelRenderer().setHorizontalAxisTitleTextSize(64);

                            /*binding.tempGraph.getGridLabelRenderer().setHorizontalAxisTitle("Time [s]");
                            binding.tempGraph.getGridLabelRenderer().setHorizontalLabelsVisible(true);
                            binding.tempGraph.getGridLabelRenderer().setHorizontalAxisTitleColor(Color.BLACK);
                            binding.tempGraph.getGridLabelRenderer().setHorizontalLabelsColor(Color.BLACK);

*/
                            temp_series.appendData(new DataPoint(currentTime, value), true, 500);
                            temp_series.setColor(Color.BLUE);

                            //temp_thread.
                            binding.tempGraph.addSeries(temp_series);

                            binding.tempGraph.getViewport().setMinY(10);
                            binding.tempGraph.getViewport().setMaxY(40);

                            binding.tempGraph.getViewport().setYAxisBoundsManual(true);
                            //binding.tempGraph.getViewport().setXAxisBoundsManual(true);

                            Log.d(LOG_TAG, "run: series " + value);


                        }
                        else if(topic.equals(topics[3])) {

                            JSONObject obj = new JSONObject(message);
                            String value = obj.getString("reading");
                            value = value.replace("\"", "");
                            binding.coolerStatusMessage.setText(value);

                        } else if(topic.equals(topics[1])) {
                            JSONObject obj = new JSONObject(message);
                            int value = Integer.parseInt(obj.getString("reading"));
                            Random rand = new Random();
                            //value = rand.nextInt(30) % value;
                            //int epoch = Integer.parseInt(obj.getString("timestamp"));
                            //@SuppressLint("SimpleDateFormat")
                            //String date = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new java.util.Date(epoch*1000));

                            Date currentTime = Calendar.getInstance().getTime();

                            binding.moistureGraph.setTitle("Moisture measurements");
                            binding.moistureGraph.setTitleColor(Color.BLACK);
                            binding.moistureGraph.setTitleTextSize(64);
                            binding.moistureGraph.getGridLabelRenderer().setHumanRounding(true);

                            binding.moistureGraph.getGridLabelRenderer().setVerticalAxisTitle("Moisture");
                            binding.moistureGraph.getGridLabelRenderer().setVerticalLabelsVisible(true);
                            binding.moistureGraph.getGridLabelRenderer().setVerticalAxisTitleColor(Color.BLACK);
                            binding.moistureGraph.getGridLabelRenderer().setVerticalLabelsColor(Color.BLACK);

                            binding.moistureGraph.getGridLabelRenderer().setGridColor(Color.BLACK);

                            moisture_series.appendData(new DataPoint(currentTime, value), true, 500);
                            moisture_series.setColor(Color.BLUE);

                            //temp_thread.
                            binding.moistureGraph.addSeries(moisture_series);

                            binding.moistureGraph.getViewport().setMinY(20);
                            binding.moistureGraph.getViewport().setMaxY(70);

                            binding.moistureGraph.getViewport().setYAxisBoundsManual(true);

                            Log.d(LOG_TAG, "run: series " + value);
                        }
                       /* Log.d(LOG_TAG, "Message arrived:");
                        Log.d(LOG_TAG, "   Topic: " + topic);
                        Log.d(LOG_TAG, " Message: " + message);*/

                        //binding.receivedMessages.setText(message);

                    } catch (UnsupportedEncodingException | JSONException e) {
                        Log.e(LOG_TAG, "Message encoding error.", e);
                    }
                }
            });
        }
    };

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonPublish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    mqttManager.publishString("HELLLLLLOOOOOO", topics[2], AWSIotMqttQos.QOS0);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Publish error.", e);
                }
            }
        });

        binding.buttonSubscribe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Log.d(LOG_TAG, "onClick: tu smo");
                    try {
                        mqttManager.subscribeToTopic(topics[0], AWSIotMqttQos.QOS0, new_message_callback);
                        mqttManager.subscribeToTopic(topics[1], AWSIotMqttQos.QOS0, new_message_callback);
                        mqttManager.subscribeToTopic(topics[3], AWSIotMqttQos.QOS0, new_message_callback);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Subscription error.", e);
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Subscribe error.", e);
                }
            }
        });
    }
}