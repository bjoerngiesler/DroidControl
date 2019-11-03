package com.astromech.d_0controller;

import android.os.Message;
import android.os.Handler;
import android.renderscript.ScriptGroup;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ResourceBundle;

public class D0ProtocolHandler extends PacketSerialHandler {

    /*
        on Arduino:
        struct Controller2Robot {
            uint8_t sequenceNum;
            int16_t mainBar, nodBar;
            int16_t leftWheel, rightWheel;
            uint8_t joyButtonState;
        };
     */
    public static class Controller2Robot {
        public int sequenceNum;
        public short mainBar, nodBar;
        public short leftWheel, rightWheel;
        public int joyButtonState;
        public String toString() {
            return String.format("#%03d l%03d r%03d m%03d n%03d jx%02x",
                    sequenceNum, leftWheel, rightWheel, mainBar, nodBar, joyButtonState);
        }
    }
    public final static int controller2RobotSize = 10;

    /*
        on Arduino:
        struct Robot2Controller {
            uint8_t sequenceNum;
            int16_t mainBar, nodBar;
            int16_t leftWheel, rightWheel;
            uint8_t robotState;
        };
     */
    public static class Robot2Controller {
        public int sequenceNum;
        public short mainBar, nodBar;
        public short leftWheel, rightWheel;
        public int robotState;
        public String toString() {
            return String.format("#%03d l%03d r%03d m%03d n%03d rx%02x",
                    sequenceNum, leftWheel, rightWheel, mainBar, nodBar, robotState);
        }
    }
    public final static int robot2ControllerSize = 10;

    public final static int MSG_D0_PREFIX = 0x60;
    public final static int MSG_D0_MESSAGE_RECEIVED = MSG_D0_PREFIX + 1;
    public final static int MSG_D0_PROTOCOL_ERROR = MSG_D0_PREFIX + 2;

    private int sendSeqNum;

    public D0ProtocolHandler(InputStream istream, OutputStream ostream, Handler handler) {
        super(istream, ostream, handler);
        sendSeqNum = 0;
    }

    @Override
    public Message createHandlerMessageReceived(byte[] buffer, int bufferlen) {
        if(bufferlen != robot2ControllerSize) {
            return mHandler.obtainMessage(MSG_D0_PROTOCOL_ERROR,
                    String.format("Wrong message size (expected %d, got %d)", robot2ControllerSize, bufferlen));
        }

        Robot2Controller msg = new Robot2Controller();
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        if(buffer[0]<0) msg.sequenceNum = 256+buffer[0];
        else msg.sequenceNum = buffer[0];
        bb.clear(); bb.put(buffer[1]); bb.put(buffer[2]); msg.mainBar = bb.getShort(0);
        bb.clear(); bb.put(buffer[3]); bb.put(buffer[4]); msg.nodBar = bb.getShort(0);
        bb.clear(); bb.put(buffer[5]); bb.put(buffer[6]); msg.leftWheel = bb.getShort(0);
        bb.clear(); bb.put(buffer[7]); bb.put(buffer[8]); msg.rightWheel = bb.getShort(0);
        if(buffer[9]<0) msg.robotState = 256+buffer[9];
        else msg.robotState = buffer[9];

        return mHandler.obtainMessage(MSG_D0_MESSAGE_RECEIVED, msg);
    }

    public void sendHandlerMessageReceived(byte[] buffer, int bufferlen) {
        super.createHandlerMessageReceived(buffer, bufferlen).sendToTarget();
        createHandlerMessageReceived(buffer, bufferlen).sendToTarget();
    }

    public void write(Controller2Robot msg) {
        byte[] buffer = new byte[controller2RobotSize];

        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        if(sendSeqNum > 256) sendSeqNum = 0;
        if(sendSeqNum > 127) buffer[0] = (byte)(sendSeqNum - 256);
        else buffer[0] = (byte)sendSeqNum;
        sendSeqNum = sendSeqNum + 1;
        bb.clear(); bb.putShort(msg.mainBar); buffer[1] = bb.get(0); buffer[2] = bb.get(1);
        bb.clear(); bb.putShort(msg.nodBar); buffer[3] = bb.get(0); buffer[4] = bb.get(1);
        bb.clear(); bb.putShort(msg.leftWheel); buffer[5] = bb.get(0); buffer[6] = bb.get(1);
        bb.clear(); bb.putShort(msg.rightWheel); buffer[7] = bb.get(0); buffer[8] = bb.get(1);
        if(msg.joyButtonState > 127) buffer[9] = (byte)(msg.joyButtonState - 256);
        else buffer[9] = (byte)msg.joyButtonState;

        super.write(buffer, controller2RobotSize);
    }
}
