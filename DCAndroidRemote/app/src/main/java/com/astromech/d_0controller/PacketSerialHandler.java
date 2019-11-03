package com.astromech.d_0controller;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class PacketSerialHandler extends Thread {
    public final static int MSG_PACKETSERIAL_PREFIX = 0x50;
    public final static int MSG_PACKETSERIAL_MESSAGE_RECEIVED = MSG_PACKETSERIAL_PREFIX + 1;
    public final static int MSG_PACKETSERIAL_CHECKSUM_FAILED = MSG_PACKETSERIAL_PREFIX + 2;
    public final static int MSG_PACKETSERIAL_PROTOCOL_ERROR = MSG_PACKETSERIAL_PREFIX + 3;
    public final static int MSG_PACKETSERIAL_DEBUG = MSG_PACKETSERIAL_PREFIX + 4;

    private final InputStream mInStream;
    private final OutputStream mOutStream;
    protected final Handler mHandler;
    private boolean mShouldStop;

    public PacketSerialHandler(InputStream istream, OutputStream ostream, Handler handler) {
        mInStream = istream;
        mOutStream = ostream;
        mHandler = handler;
        mShouldStop = true;
    }

    public void run() {
        mShouldStop = false;

        byte[] buffer = new byte[1024];  // buffer store for the stream
        byte[] recv = new byte[1];
        int numbytes_expected, numbytes_received;

        while(mShouldStop == false) {
            try {
                if(mInStream.available() == 0) {
                    SystemClock.sleep(10);
                } else {
                    // protocol is: 0x06 0x85 <numbytes> <byte_0> <byte_1> ... <byte_n> <checksum>

                    // initial lock; wait for initial sync byte
                    if(mInStream.read(recv, 0, 1) != 1) continue;
                    if(recv[0] != (byte)0x06) continue;

                    // look for 2nd sync byte
                    if(mInStream.read(recv, 0, 1) != 1) {
                        mHandler.obtainMessage(MSG_PACKETSERIAL_PROTOCOL_ERROR, "No byte after 0x06").sendToTarget();
                        continue;
                    }
                    if(recv[0] != (byte)0x85) {
                        mHandler.obtainMessage(MSG_PACKETSERIAL_PROTOCOL_ERROR, String.format("Byte after 0x06 is 0x%02x and not 0x85", recv[0])).sendToTarget();
                        continue;
                    }

                    // OK, full sync. Next is number of bytes in packet
                    if(mInStream.read(recv, 0, 1) != 1) {
                        mHandler.obtainMessage(MSG_PACKETSERIAL_PROTOCOL_ERROR, "No size byte after 0x06 0x85").sendToTarget();
                        continue;
                    }

                    numbytes_expected = recv[0]+1; // add one for checksum byte
                    numbytes_received = 0;

                    // Read <numbytes+1> bytes, which includes checksum
                    // A packet of 256 bytes at 9600bps takes 27ms, so allow for 30ms max of waiting time
                    int maxsleep = 30;
                    while(numbytes_received < numbytes_expected) {
                        while(mInStream.read(buffer, numbytes_received, 1) != 1) {
                            SystemClock.sleep(1);
                            maxsleep = maxsleep - 1;
                            if(maxsleep == 0) break;
                        }
                        if(maxsleep == 0) break;
                        numbytes_received = numbytes_received + 1;
                    }

                    // timeout reached
                    if(maxsleep == 0) {
                        mHandler.obtainMessage(MSG_PACKETSERIAL_PROTOCOL_ERROR, "Timeout after sync").sendToTarget();
                        continue;
                    }

                    // compute checksum
                    byte cs = (byte)(numbytes_received-1);
                    for(int i=0; i<numbytes_received-1; i++) {
                        cs ^= buffer[i];
                    }
                    if(cs == buffer[numbytes_received-1]) {
                        sendHandlerMessageReceived(buffer, numbytes_received-1);
                    } else {
                        mHandler.obtainMessage(MSG_PACKETSERIAL_PROTOCOL_ERROR,
                                String.format("Checksum should be 0x%02x but is 0x%02x", cs, buffer[numbytes_received-1])).sendToTarget();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    public Message createHandlerMessageReceived(byte[] buffer, int bufferlen) {
        return mHandler.obtainMessage(MSG_PACKETSERIAL_MESSAGE_RECEIVED, bufferlen, -1, buffer);
    }

    public void sendHandlerMessageReceived(byte[] buffer, int bufferlen) {
        createHandlerMessageReceived(buffer, bufferlen).sendToTarget();
    }

    public void write(byte[] buffer, int bufferlen) {
        byte[] intbuffer = new byte[bufferlen+4];
        intbuffer[0] = 0x06;
        intbuffer[1] = (byte)(0x85-256);
        intbuffer[2] = (byte)bufferlen;
        byte cs = (byte)bufferlen;
        for(int i=0; i<bufferlen; i++) {
            intbuffer[3+i] = buffer[i];
            cs ^= buffer[i];
        }
        intbuffer[bufferlen+3] = cs;

        try {
            mOutStream.write(intbuffer);
        } catch (IOException e) { }
    }

    /* Call this from the main activity to shutdown the connection */
    public void cancel() {
        mShouldStop = true;
    }
}