/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.collectiveintelligence.pandora.BaseFunction;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.util.Log;

import com.collectiveintelligence.pandora.WebService.WebServiceLogin;

import java.io.OutputStream;
import java.util.UUID;

/**
 * This class does all the work for setting up and managing BluetoothMan
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothServiceListen {
    // Debugging
    private static final String TAG = "BluetoothServiceListen";

    // Name for the SDP record when creating server socket
    private static final String NAME_INSECURE = "PandoraBluetooth";

    // Unique UUID for this application
    private static final UUID MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private AcceptThread mInsecureAcceptThread;
    private int mState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_READING = 2;

    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     */
    public BluetoothServiceListen() {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
    }

    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }

    public synchronized void start() {
        Log.d(TAG, "start listening");

        setState(STATE_LISTEN);

        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread(false);
            mInsecureAcceptThread.start();
        }
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }

//        for(Map.Entry<String,ReadingThread> entry: mapReadingThread.entrySet()){
//            ReadingThread thread = entry.getValue();
//            thread.cancel();
//        }
//        mapReadingThread.clear();

        setState(STATE_NONE);
    }

    static public boolean isInsecureBluetoothSupport(){
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD_MR1){
            return false;
        }else{
            BluetoothServerSocket tmp = null;
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                tmp = adapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, MY_UUID_INSECURE);
            } catch (Exception e) {
                return false;
            }
            if(tmp != null){
                return true;
            }
            return false;
        }
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private BluetoothServerSocket mmServerSocket;
        private boolean isCancel = false;

        @TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;

            // Create a new listening server socket
            try {
                tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, MY_UUID_INSECURE);
            } catch (Exception e) {
                Log.e(TAG,  "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        @TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
        public void run() {
            Log.d(TAG, "BEGIN mAcceptThread" + this);
            setName("AcceptThread");

            BluetoothSocket socket = null;

            // Listen to the server socket if we're not connected
            while (true){
                if(isCancel){
                    break;
                }

                if(mmServerSocket != null) {
                    try {
                        socket = mmServerSocket.accept();
                    } catch (Exception e) {
                        Log.e(TAG,  "accept() failed ");
                        mmServerSocket = null;
                        continue;
                    }
                }else{
                    try {
                        mmServerSocket = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, MY_UUID_INSECURE);
                    }catch (Exception e1){
                        mmServerSocket = null;
                        if(isCancel){
                            break;
                        }
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e2) {
                        }
                    }
                    continue;
                }

                if (socket != null) {
                    connected(socket,socket.getRemoteDevice());
                }
            }
        }

        public void cancel() {
            Log.d(TAG, "cancel ");
            isCancel = true;
        }
    }

//    Map<String,ReadingThread> mapReadingThread = new HashMap<>();


//    /**
//     * This thread runs during a connection with a remote device.
//     * It handles all incoming and outgoing transmissions.
//     */
//    private class ReadingThread extends Thread {
//        private final BluetoothSocket mmSocket;
//        private final InputStream mmInStream;
//        private final BluetoothDevice mmDevice;
//        int state;
//
//        public ReadingThread(BluetoothSocket socket) {
//            mmSocket = socket;
//            mmDevice = mmSocket.getRemoteDevice();
//
//            Log.d(TAG, "create ReadingThread:" + mmDevice.getName());
//
//            InputStream tmpIn = null;
//
//            // Get the BluetoothSocket input and output streams
//            try {
//                tmpIn = socket.getInputStream();
//            } catch (Exception e) {
//                Log.e(TAG, "temp sockets not created:" + mmDevice.getName());
//                state = STATE_NONE;
//            }
//
//            mmInStream = tmpIn;
//        }
//
//        public void run() {
//            Log.i(TAG, "BEGIN mReadingThread,"+mmDevice.getName());
//            byte[] buffer = new byte[1024];
//
//            // Keep listening to the InputStream while connected
//            while (true) {
//                try {
//                    // Read from the InputStream
//                    state = STATE_READING;
//                    mmInStream.read(buffer);
//                    onAccept(mmSocket);
//                } catch (Exception e) {
//                    Log.e(TAG, "disconnected,"+mmDevice.getName());
//                    state = STATE_NONE;
//                    break;
//                }
//            }
//        }
//
//        public void cancel() {
//            Log.e(TAG, "cancel reading,"+mmDevice.getName());
//            try {
//                mmSocket.close();
//            } catch (Exception e) {
//            }
//            state = STATE_NONE;
//        }
//    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "connected " + device.getName());

        onAccept(socket);

//        String s = device.getAddress();
//        ReadingThread readingThread = mapReadingThread.get(s);
//        if(readingThread!=null && (readingThread.state==STATE_READING)){
//            return;
//        }
//        if(readingThread!=null){
//            mapReadingThread.remove(s);
//            readingThread.cancel();
//        }
//        readingThread = new ReadingThread(socket);
//        mapReadingThread.put(s,readingThread);
//        readingThread.start();
    }

    void onAccept(BluetoothSocket socket){
        String s= BluetoothMan.makeBluetoothMessage(WebServiceLogin.getUserName(), WebServiceLogin.getNameOfUser());
        byte[] data = s.getBytes();
        if(data.length>0){
            try {
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(data);
            }catch (Exception e){

            }
        }
    }
}
