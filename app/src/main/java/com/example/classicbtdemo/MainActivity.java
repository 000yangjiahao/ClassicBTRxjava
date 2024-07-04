package com.example.classicbtdemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.classicbtdemo.ThreadManage.ConnectThread;
import com.example.classicbtdemo.ThreadManage.ConnectedThread;
import com.example.classicbtdemo.ThreadManage.ServerThread;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Logger;

import androidx.activity.result.ActivityResultLauncher;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    private static final UUID MY_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> deviceArrayAdapter;
    private ArrayList<BluetoothDevice> bluetoothDevices;

    private EditText etMessage;
    private TextView tvReceived;
    private Button btnSend;
    private Button btnDisconnect;
    private Button btnSearch;
    private Handler handler;

    private static final String DISCONNECT_MESSAGE = "__DISCONNECT__";

    private Disposable serverDisposable;
    private Disposable connectDisposable;
    private Disposable connectedDisposable;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkPermissions();
        }
        initView();
        initListener();
        initAdapter();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

        handler = new Handler(msg -> {
            if (msg.what == 0) {
                String receivedMessage = (String) msg.obj;
                tvReceived.append("Received: " + receivedMessage + "\n");
            } else if (msg.what == 1) {
                Toast.makeText(MainActivity.this, "Disconnected by remote device", Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        startServerThread();

    }

    private void initView() {
        btnSearch = findViewById(R.id.btn_search);
        etMessage = findViewById(R.id.et_message);
        tvReceived = findViewById(R.id.tv_received);
        btnSend = findViewById(R.id.btn_send);
        btnDisconnect = findViewById(R.id.btn_disconnected);
    }

    private void initListener() {
        btnSearch.setOnClickListener(v -> searchBluetoothDevices());
        btnSend.setOnClickListener(v -> sendMessage());
        btnDisconnect.setOnClickListener(v -> disconnect());
    }

    @SuppressLint("MissingPermission")
    private void initAdapter() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothDevices = new ArrayList<>();
        deviceArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Device doesn't support Bluetooth", Toast.LENGTH_SHORT).show();
            finish();
        } else if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void checkPermissions() {
        ArrayList<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    private void startServerThread() {
        serverDisposable = BluetoothRxJavaUtils
                .startServer(bluetoothAdapter, handler)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        socket -> {
                            Toast.makeText(MainActivity.this, "Connected as server", Toast.LENGTH_SHORT).show();
                            manageConnectedSocket(socket);
                        },
                        throwable -> runOnUiThread(() -> Toast.makeText(MainActivity.this, "Server error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show())
                );
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        Log.d("BluetoothClient", "Attempting to connect to device: " + device.getName());
        disconnect();

        connectDisposable = BluetoothRxJavaUtils.connectToDevice(bluetoothAdapter, device, MY_UUID.toString())
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        socket -> {
                            Log.d("BluetoothClient", "connectToDevice: "+ device.getName());
                            Toast.makeText(MainActivity.this, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show();
                            manageConnectedSocket(socket);
                        },
                        throwable ->{
                            Log.e("BluetoothClient", "Connection failed", throwable);
                            Toast.makeText(MainActivity.this, "Connection failed: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                );
    }

    private void manageConnectedSocket(BluetoothSocket socket) {
        connectedDisposable = BluetoothRxJavaUtils.manageConnectedSocket(socket, handler, tvReceived)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        buffer -> {
                            String message = new String(buffer);
                            if (message.equals(DISCONNECT_MESSAGE)) {
                                disconnect();
                                Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            handler.obtainMessage(0, message).sendToTarget();
                        },
                        throwable -> Toast.makeText(MainActivity.this, "Receive failed: " + throwable.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    @SuppressLint("CheckResult")
    private void sendMessage() {
        String message = etMessage.getText().toString();
        if (!message.isEmpty() && connectedDisposable != null) {
            BluetoothRxJavaUtils.sendMessage(message.getBytes())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            () -> tvReceived.append("Sent: " + message + "\n"),
                            throwable ->  Toast.makeText(this, "Send failed: " + throwable.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        } else {
            Toast.makeText(this, "Message is empty or not connected", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("CheckResult")
    private void disconnect() {
        if (connectedDisposable != null && !connectedDisposable.isDisposed()) {
            BluetoothRxJavaUtils.sendMessage(DISCONNECT_MESSAGE.getBytes())
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            () -> {
                                Toast.makeText(this, "disconnected！", Toast.LENGTH_SHORT).show();
                                Log.e("BluetoothClient", "disconnected");
                            },
                            throwable -> Log.e("BluetoothClient", "Failed to send disconnect message", throwable)
                    );
            connectedDisposable.dispose();
        }
        if (serverDisposable != null && !serverDisposable.isDisposed()) {
            serverDisposable.dispose();
        }
        if (connectDisposable != null && !connectDisposable.isDisposed()) {
            connectDisposable.dispose();
        }

        startServerThread();
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        unregisterReceiver(receiver);
        disconnect();
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                @SuppressLint("MissingPermission") String deviceName = device.getName();
                if (deviceName != null) {
                    String deviceAddress = device.getAddress();
                    deviceArrayAdapter.add(deviceName + "\n" + deviceAddress);
                    bluetoothDevices.add(device);
                }
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                searchBluetoothDevices();
            } else {
                Toast.makeText(this, "Permissions required for Bluetooth scanning", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void searchBluetoothDevices() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS);
        } else {
            deviceArrayAdapter.clear();
            bluetoothDevices.clear();
            bluetoothAdapter.startDiscovery();

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select Bluetooth Device");
            ListView deviceListView = new ListView(this);
            deviceListView.setAdapter(deviceArrayAdapter);
            builder.setView(deviceListView);
            AlertDialog dialog = builder.create();
            dialog.show();

            deviceListView.setOnItemClickListener((parent, view, position, id) -> {
                BluetoothDevice device = bluetoothDevices.get(position);
                connectToDevice(device);
                dialog.dismiss();
            });
        }
    }
}
