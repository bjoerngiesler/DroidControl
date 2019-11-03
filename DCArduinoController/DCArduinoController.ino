#include <SoftwareSerial.h>
#include <Servo.h>
#include <Wire.h>
#include <ServoEaser.h>
#include <digitalWriteFast.h>
#include <LightweightServo.h>
#include <EasyTransfer.h>
#include <Adafruit_PWMServoDriver.h>

// Pins
const int mainBarPin = 6;
const int nodBarPin = 5;
const int serTXPin = 3;
const int serRXPin = 2;

// Servo setup
const int servoFrameMillis = 20;
uint8_t servonum = 0;

Servo mainBarServo, nodBarServo; 
ServoEaser mainBarEaser, nodBarEaser;
Adafruit_PWMServoDriver pwm = Adafruit_PWMServoDriver();

// Comm setup
SoftwareSerial btSerial(serRXPin, serTXPin);
const int btSerialSpeed = 9600; // By default, HC-05 uses 9600bps. If you have changed that, change this :-)
EasyTransfer ETin, ETout; 

// Internal state
int leftWheel, rightWheel, mainBar, nodBar;
unsigned long lastMillis;

// Communication structs to talk to the app
struct Controller2Robot {
  uint8_t sequenceNum;
  int16_t mainBar, nodBar; 
  int16_t leftWheel, rightWheel;
  uint8_t joyButtonState;
};

struct Robot2Controller {
  uint8_t sequenceNum;
  int16_t mainBar, nodBar;
  int16_t leftWheel, rightWheel;
  uint8_t robotState;
};

Controller2Robot controller2RobotMsg;
Robot2Controller robot2ControllerMsg;
bool receiveOK = false;

void setup() {
  Serial.begin(38400);
  Serial.println("D-0 Control Software - Receiver");
  Serial.println("2019 by Michael Baddeley, Bjoern Giesler");

  // setup EasyTransfer (packet serial) over HC-05. 
  btSerial.begin(btSerialSpeed);
  ETin.begin(details(controller2RobotMsg), &btSerial);
  ETout.begin(details(robot2ControllerMsg), &btSerial);

  // setup PWM for 
  pwm.begin();
  pwm.setPWMFreq(60);  // Analog servos run at ~60 Hz updates 
 
  nodBarServo.attach(nodBarPin);

  mainBarEaser.begin(mainBarServo, servoFrameMillis);

  mainBarEaser.easeTo(90, 2000);
  mainBarServo.attach(mainBarPin);
  //nodBarEaser.begin(nodBarServo, servoFrameMillis);
  //nodBarEaser.easeTo(90, 2000);  
}


void outputStateToSerial() {
  static char outputString[255];
  snprintf(outputString, 255, "#%03d l%03d r%03d m%03d n%03d s0x%02x", 
    controller2RobotMsg.sequenceNum,
    controller2RobotMsg.leftWheel, controller2RobotMsg.rightWheel, 
    controller2RobotMsg.mainBar, controller2RobotMsg.nodBar, 
    controller2RobotMsg.joyButtonState);
  Serial.println(outputString);
}

void loop() {
  static int sequenceNum = 0;
  
 #if 0
  nodBarEaser.update();
  if (nodBarEaser.hasArrived() ) {
    lastMillis = millis();
    int angle    = random(30,150);
    int duration = random(1000,1500); 
    nodBarEaser.easeTo( angle, duration );
  }
  
  mainBarEaser.update();
  if (mainBarEaser.hasArrived() ) {
    lastMillis = millis();
    int angle    = random(70,110);
    int duration = random(1000,1500); 
    mainBarEaser.easeTo( angle, duration );
  }
#endif

  // take up control input
  if(ETin.receiveData()) {
    leftWheel = controller2RobotMsg.leftWheel;
    rightWheel = controller2RobotMsg.rightWheel;
    mainBar = controller2RobotMsg.mainBar;
    nodBar = controller2RobotMsg.nodBar;
  }

  #if 0
  // Send out status info
  robot2ControllerMsg.sequenceNum = sequenceNum++;
  robot2ControllerMsg.leftWheel = leftWheel;
  robot2ControllerMsg.rightWheel = rightWheel;
  robot2ControllerMsg.mainBar = mainBar;
  robot2ControllerMsg.nodBar = nodBar;
  robot2ControllerMsg.robotState = 0;
  ETout.sendData();

  pwm.setPWM(0, 0, leftWheel);
  pwm.setPWM(1, 0, rightWheel);
  pwm.setPWM(2, 0, mainBar);
  pwm.setPWM(3, 0, nodBar);
  #endif
  nodBarServo.write(nodBar);

  outputStateToSerial();

  delay(25);
}
