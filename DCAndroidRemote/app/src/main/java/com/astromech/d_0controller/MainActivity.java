package com.astromech.d_0controller;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;
import android.view.View;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.Timer;
import java.util.TimerTask;

import io.github.controlwear.virtual.joystick.android.JoystickView;

public class MainActivity extends AppCompatActivity {
    BluetoothAdapter bt_adapter;
    ArrayAdapter<String> bt_array_adapter;

    BluetoothDevice bt_device;
    BluetoothSocket bt_socket = null;
    OutputStream bt_output_stream;
    InputStream bt_input_stream;
    UUID serial_port_service_uuid;

    TextView mStatusView;

    Handler mHandler;
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status
    private final static int TIMER_ELAPSED = 4;

    Timer mTimer;
    int mTimerNum;
    private final static int mTimerDelay = 25; // in ms

    int mWheelAngle, mWheelStrength;
    int mHeadAngle, mHeadStrength;

    D0ProtocolHandler bt_handler_thread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler(){
            public void handleMessage(android.os.Message msg){
                switch(msg.what) {
                    case PacketSerialHandler.MSG_PACKETSERIAL_MESSAGE_RECEIVED:
                        /*
                        byte[] buffer = (byte[])(msg.obj);

                        String text = new String();
                        for(int i=0; i<msg.arg1; i++) {
                            text = text + String.format("%02x ", buffer[i]);
                        }
                        mStatusView.setText(Integer.toString(msg.arg1) + " bytes: " + text);
                         */
                        break;

                    case PacketSerialHandler.MSG_PACKETSERIAL_DEBUG:
                        //mStatusView.setText(msg.obj.toString());
                        break;

                    case PacketSerialHandler.MSG_PACKETSERIAL_PROTOCOL_ERROR:
                        mStatusView.setText(msg.obj.toString());
                        break;

                    case PacketSerialHandler.MSG_PACKETSERIAL_CHECKSUM_FAILED:
                        mStatusView.setText(msg.obj.toString());
                        break;

                    case D0ProtocolHandler.MSG_D0_MESSAGE_RECEIVED:
                        mStatusView.setText(msg.obj.toString());
                        break;

                    case D0ProtocolHandler.MSG_D0_PROTOCOL_ERROR:
                        mStatusView.setText(msg.obj.toString());
                        break;

                    case CONNECTING_STATUS:
                        if(msg.arg1 == 1)
                            mStatusView.setText("Connected to Device: " + (String)(msg.obj));
                        else
                            mStatusView.setText("Connection Failed");
                        break;

                    case TIMER_ELAPSED:
                        D0ProtocolHandler.Controller2Robot c2r = new D0ProtocolHandler.Controller2Robot();
                        c2r.mainBar = (short)mHeadAngle; c2r.nodBar = (short)mHeadStrength;
                        c2r.leftWheel = (short)mWheelAngle; c2r.rightWheel = (short)mWheelStrength;
                        c2r.joyButtonState = 130;
                        bt_handler_thread.write(c2r);
                        break;

                    default:
                        mStatusView.setText(String.format("Unknown message code 0x%02x: ", msg.what) + msg.obj.toString());
                        break;
                }
            }
        };

        mStatusView = (TextView)findViewById(R.id.bt_status);

        serial_port_service_uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        bt_handler_thread = null;

        // check if we are enabled
        bt_adapter = BluetoothAdapter.getDefaultAdapter();
        if (bt_adapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        if (!bt_adapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth disabled, please enable in System Settings", Toast.LENGTH_SHORT).show();
            finish();
        }

        JoystickView wheelstick = (JoystickView) findViewById(R.id.wheel_stick);
        wheelstick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                mWheelAngle = angle;
                mWheelStrength = strength;
            }
        });
        JoystickView headstick = (JoystickView) findViewById(R.id.head_stick);
        headstick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                mHeadAngle = angle;
                mHeadStrength = strength;
            }
        });

        populateDevicesSpinner();

        mTimer = null;
    }

    protected void listBondedDevices() {
        bt_array_adapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1);
        Set<BluetoothDevice> paired_bt_devices = bt_adapter.getBondedDevices();
        for(BluetoothDevice device: paired_bt_devices) {
            bt_array_adapter.add(device.getName() + "\n" + device.getAddress());
        }
    }

    protected void populateDevicesSpinner() {
        listBondedDevices();
        Spinner devices_spinner = (Spinner) findViewById(R.id.devices_spinner);
        bt_array_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        devices_spinner.setAdapter(bt_array_adapter);

        devices_spinner.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selectedItemView, int position, long id) {
                useDevice(parent.getItemAtPosition(position).toString());
            }

            public void onNothingSelected(AdapterView<?> parent) {
                Toast.makeText(parent.getContext(), "No Device Selected", Toast.LENGTH_LONG).show();
            }
        });
    }


    public void useDevice(String device_name) {
        stopTimer();
        final String address = device_name.substring(device_name.length() - 17);
        final String name = device_name.substring(0, device_name.length() - 17);

        BluetoothDevice device = bt_adapter.getRemoteDevice(address);
        if(device == null) {
            Toast.makeText(this,  "Could not open " + name + "(" + address + ")", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Using " + name + "(" + address + ")", Toast.LENGTH_LONG).show();
        }
        openDevice(bt_adapter.getRemoteDevice(address));
    }

    void openDevice(BluetoothDevice device) {
        if(bt_handler_thread != null) {
            bt_handler_thread.cancel();
        }

        try {
            bt_socket = device.createRfcommSocketToServiceRecord(serial_port_service_uuid);
        } catch(IOException e) {
            mStatusView.setText("Couldn't create socket");
        }

        try {
            bt_socket.connect();
        } catch(IOException e) {
            try {
                bt_socket.close();
            } catch(IOException e2) {
                mStatusView.setText("Couldn't connect and couldn't even close?!");
                return;
            }
            mStatusView.setText("Couldn 't connect to device");
            return;
        }

        mStatusView.setText("Successfully connected");
        startTimer();

        InputStream istream;
        OutputStream ostream;

        try {
            istream = bt_socket.getInputStream();
            ostream = bt_socket.getOutputStream();
        } catch(IOException e) {
            mStatusView.setText("Couldn't get input/output streams");
            return;
        }

        bt_handler_thread = new D0ProtocolHandler(istream, ostream, mHandler);
        bt_handler_thread.start();
    }

    public void startTimer() {
        mTimer = new Timer();
        mTimerNum = 0;
        TimerTask task = new TimerTask() {
            public void run() {
                mHandler.obtainMessage(TIMER_ELAPSED, mTimerNum, -1).sendToTarget();
                mTimerNum = mTimerNum + 1;
            }
        };
        mTimer.schedule(task, 100, 100);
    }

    public void stopTimer() {
        //stop the timer, if it's not already null
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
            mTimerNum = 0;
        }
    }
}
