package com.example.classicbtdemo;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;

public class BluetoothRxJavaUtils {

    private static OutputStream outputStream;
    private static BluetoothSocket currentSocket;

    @SuppressLint("MissingPermission")
    public static Observable<BluetoothSocket> startServer(BluetoothAdapter adapter, Handler handler) {
        return Observable.create(emitter -> {
            BluetoothServerSocket serverSocket = null;
            try {
                Log.d("123", "Attempting to start server...");
                serverSocket = adapter.listenUsingRfcommWithServiceRecord("BluetoothApp", UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66"));
                Log.d("123", "Server started, waiting for connections...");
                BluetoothSocket socket = serverSocket.accept();
                if (socket != null) {
                    Log.d("BluetoothServer", "Connection accepted from: " + socket.getRemoteDevice().getName() + socket.getRemoteDevice().getAddress());
                    emitter.onNext(socket);
                    emitter.onComplete();
                    serverSocket.close();
                }
            } catch (IOException e) {
                Log.e("BluetoothServer", "Error while starting server or accepting connection", e);
                emitter.onError(e);
            } finally {
                if (serverSocket != null) {
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        Log.e("BluetoothServer", "Error while closing server socket", e);
                    }
                }
            }
        });
    }

    @SuppressLint("MissingPermission")
    public static Observable<BluetoothSocket> connectToDevice(BluetoothAdapter adapter, BluetoothDevice device, String uuid) {
        Log.d("BluetoothClient", device.getName()+":"+ device.getAddress() +"11111111111111");
        Log.d("BluetoothClient", uuid+"11111111111111222222222222222");
        return Observable.create(emitter -> {
            Log.d("BluetoothClient", "1122222222222222222222222222222222");
            BluetoothSocket socket = null;
            try {
                Log.d("BluetoothClient", "Attempting to create RFCOMM socket...");
                socket = device.createRfcommSocketToServiceRecord(UUID.fromString(uuid));
                Log.d("BluetoothClient", "RFCOMM socket created, canceling discovery...");
                adapter.cancelDiscovery();
                Log.d("BluetoothClient", "Connecting to device...");
                socket.connect();
                Log.d("BluetoothClient", "Connected to device");
                emitter.onNext(socket);
                emitter.onComplete();
            } catch (IOException e) {
                Log.e("BluetoothClient", "Error while connecting to device", e);
                emitter.onError(e);
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ex) {
                        Log.e("BluetoothClient", "Error while closing socket", ex);
                    }
                }
            }
        });
    }

    public static Observable<byte[]> manageConnectedSocket(BluetoothSocket socket, Handler handler, TextView tvReceived) {
        return Observable.create(emitter -> {
            InputStream inputStream = null;
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                currentSocket = socket;

                byte[] buffer = new byte[1024];
                int bytes;
                while ((bytes = inputStream.read(buffer)) != -1) {
                    emitter.onNext(Arrays.copyOf(buffer, bytes));
                }
            } catch (IOException e) {
                emitter.onError(e);
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (outputStream != null) {
                        outputStream.close();
                    }
                    socket.close();
                    currentSocket = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                emitter.onComplete();
            }
        });
    }

    public static Completable sendMessage(byte[] message) {
        return Completable.create(emitter -> {
            if (outputStream != null) {
                try {
                    outputStream.write(message);
                    outputStream.flush();
                    emitter.onComplete();
                } catch (IOException e) {
                    emitter.onError(e);
                }
            } else {
                emitter.onError(new IllegalStateException("OutputStream is null"));
            }
        });
    }
}

