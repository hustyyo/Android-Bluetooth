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
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * This class does all the work for setting up and managing BluetoothMan
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothService {
    // Debugging
    private static final String TAG = "BluetoothService";

    // Unique UUID for this application
    private static final UUID MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    // Member fields
    private ConnectThread mConnectThread;
    private ReadingThread mReadingThread;
    private int mState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_CONNECTING = 1; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 2;  // now connected to a remote device

    private BluetoothDevice mDevice;

    public BluetoothService() {
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

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        mDevice = device;

        Log.d(TAG, "connecting to " + device.getName());

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mReadingThread != null) {
            mReadingThread.cancel();
            mReadingThread = null;
        }

        onConnecting();

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ReadingThread to begin managing a BluetoothMan connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mReadingThread != null) {
            mReadingThread.cancel();
            mReadingThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mReadingThread = new ReadingThread(socket);
        mReadingThread.start();

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, "stopped:"+mDevice.getName());

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mReadingThread != null) {
            mReadingThread.cancel();
            mReadingThread = null;
        }
        setState(STATE_NONE);
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        @TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
            } catch (Exception e) {
                Log.e(TAG, "create failed "+ mmDevice.getName());
                setState(STATE_NONE);
                onFail();
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
//            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (Exception e) {

                onFail();

                Log.e(TAG, "connect failed,"+ mmDevice.getName());

                try {
                    mmSocket.close();
                } catch (Exception e2) {
                }
                setState(STATE_NONE);
                return;
            }

            Log.d(TAG, "connect succeed," + mmDevice.getName());

            // Reset the ConnectThread because we're done
            synchronized (BluetoothService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (Exception e) {
            }
            setState(STATE_NONE);
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ReadingThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final BluetoothDevice mmDevice;
        BluetoothSocket mmSocket = null;

        public ReadingThread(BluetoothSocket socket) {
            Log.d(TAG, "create ReadingThread: ");
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            mmSocket = socket;
            mmDevice = mmSocket.getRemoteDevice();

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = mmSocket.getInputStream();
                tmpOut = mmSocket.getOutputStream();
            } catch (Exception e) {
                onFail();
                Log.e(TAG, "temp sockets not created,"+mmDevice.getName());
                setState(STATE_NONE);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, "BEGIN mReadingThread," + mmDevice.getName());
            byte[] buffer = new byte[1024];
            int bytes;


            BufferedInputStream bufferedInputStream = new BufferedInputStream(mmInStream);

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    bytes = bufferedInputStream.read(buffer);
                    Log.d(TAG, "reading succeed," + mmDevice.getName());
                    onReadResult(buffer, bytes);
                    cancel();
                    break;
                } catch (Exception e) {
                    onFail();
                    Log.e(TAG, "reading failed,"+mmDevice.getName(),e);
                    setState(STATE_NONE);
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
            } catch (Exception e) {
                Log.e(TAG, "Exception during write", e);
                setState(STATE_NONE);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
            setState(STATE_NONE);
        }
    }

    public void sendRequest(){
        Log.e(TAG, "request message " + mDevice.getName());
        if(getState()==STATE_CONNECTED){
            String s = "0";
            byte[] data = s.getBytes();
            if(data.length>0){
                mReadingThread.write(data);
            }
        }
    }

    void onConnecting(){
        String name = mDevice.getName();
        String id = mDevice.getAddress();
        BluetoothMan.notifyUI(BluetoothStatus.CONNECTING,id,name);
    }

    void onFail(){
        String name = mDevice.getName();
        String id = mDevice.getAddress();
        BluetoothMan.notifyUI(BluetoothStatus.FAILED,id,name);
    }

    void onReadResult(byte[] buffer, int len){
        String s = "";
        try {
            s = new String(buffer, 0, len);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(s.length()==0){
            Log.e(TAG,"Receive invalid message.");
            return;
        }

        String name = mDevice.getName();
        String id = mDevice.getAddress();
        BluetoothMan.notifyUI(BluetoothStatus.CONNECTED,id,name);
        BluetoothMan.notifyUI(BluetoothStatus.FINISH, s);
    }
}
