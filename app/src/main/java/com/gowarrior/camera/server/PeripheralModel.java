package com.gowarrior.camera.server;


import android.gowarrior.GPIO;
import android.gowarrior.PWM;
import android.util.Log;

public class PeripheralModel {
    static final String LOG_TAG = "PeripheralModel";
    private GPIO gpio = null;
    private PWM pwm = null;
    private float dutyCycle = 10.0f; //10.0f;
    private float freq = 50.0f; //60.0f; // 55.0f; //50.0f; //Hz;
    private int ledPin ;
    private int pwdPin ;

    public PeripheralModel() {
        init();
    }

    private void init(){
        // Init for GPIO usage
        gpio = new GPIO();

        ledStart();
    }

    public void directStart( ){
        // The Basic initialization for PWM devices
        // GPIO[11] for PWM output
        pwdPin = 10; //18;//0, fail

        // Config the GPIO for PWM output, and Start PWM
        gpio.setmode(GPIO.BCM);
        gpio.setup(pwdPin, GPIO.OUTPUT);
        pwm = gpio.PWM( pwdPin,freq );
        pwm.start(dutyCycle);
    }

    public void directStop(){
        // Stop the PWM devices
        if( pwm != null){
            pwm.stop();
            pwm = null;
        }
    }


    /*
    * Function: Control the direction.;
    * Parameter: degree: -90, the left direction;
    *                     0, the middle direction;
    *                     90, the right direction;
    */
    public void directControl( int degree ){
        // For MG90S; if the pulse period is about 1ms, direct to the left all the way.
        // if the pulse period is about 1.5ms, direct to the middle all the way.
        // if the pulse period is about 2ms, direct to the middle all the way.

        if( null == pwm ){
            Log.d( LOG_TAG, " invalid PWM instance");
            directStart();
        }

        /*
        if( -90 == degree )
            dutyCycle = 0.1f; // theoretical  value is 5%, adjust to generate the requested signal;
        else if( 0 == degree )
            dutyCycle = 2.5f; // theoretical  value is 7.5%, adjust to generate the requested signal;
        else if( 90 == degree )
            dutyCycle = 5.0f; // theoretical  value is 10%, adjust to generate the requested signal;
        */

        if( -90 == degree )
            dutyCycle = 0.1f; //1.0f; // theoretical  value is 5%, adjust to generate the requested signal;
        else if( 0 == degree )
            dutyCycle = 7.5f; // theoretical  value is 7.5%, adjust to generate the requested signal;
        else if( 90 == degree )
            dutyCycle = 20.0f; // theoretical  value is 10%, adjust to generate the requested signal;

        /*
        dutyCycle = 2.0f;
        freq = 50.0f;
        pwm.ChangeFrequency( freq );
        */

        float period = 1000.0f/freq; //ms
        Log.d(LOG_TAG, " dutyCycle = " + dutyCycle
                + ", freq = " + (1000.0f / period)
                + ", period = " + period + "ms");

        pwm.ChangeDutyCycle(dutyCycle);
        //pwm.ChangeFrequency( freq );
    }

    public void ledStart(){
        // The Basic initialization for Led control
        // GPIO[84] for LED
        ledPin = 84;

        // Config the GPIO for Led and PWM output
        gpio.setmode(GPIO.BCM);
        gpio.setup(ledPin, GPIO.OUTPUT);
        //gpio.output(ledPin, 1);
    }

    public void ledOn(){
        gpio.output(ledPin, 1);
    }

    public void ledOff(){
        gpio.output( ledPin, 0);
    }

    /*
    * Function:
     */
    /*
    * Function: Get the status of Usr_Key1 and Usr_Key2.;
    * Parameter: index: 1, Usr_Key1; 2, Usr_Key2
    * Return: 0, The pressed status;
    *         1, The unpressed status
    */
    public int getKeyStatus( int idx ){
        int gpioNum = 0;
        int ret;

        if( 1 == idx ) {
            // User Key 1
            gpioNum = 15;
        }else if( 2 == idx ){
            // User Key 2
            gpioNum = 28;
        }

        // Config the GPIO for Led and PWM output
        gpio.setmode( GPIO.BCM);
        gpio.setup( gpioNum, GPIO.INPUT);
        ret = gpio.input( gpioNum );

        return ret;
    }
}
