# RGB LED Controller via HC-06

This is an Android app built with **Jetpack Compose** and **Kotlin DSL** that allows users to control an RGB LED using an **HC-06 Bluetooth module**. The app connects to the Arduino-based Bluetooth device and sends RGB color values to change the LED's color in real time.

## Features

- Connects to HC-06 Bluetooth module
- User interface with three SeekBars to adjust **Red**, **Green**, and **Blue** values
- Send button to transmit the RGB value to the connected module
- Real-time visual feedback of selected color

## Technologies Used

- **Kotlin**
- **Jetpack Compose** for UI
- **Kotlin DSL** for Gradle configuration
- **BluetoothAdapter** & **BluetoothSocket** for Bluetooth communication

## Requirements

- Android device with Bluetooth support (API 21+)
- HC-06 Bluetooth module
- Arduino or compatible board connected to an RGB LED and HC-06

## Arduino Example Code

```cpp
#include <SoftwareSerial.h>
SoftwareSerial BT(11, 10); // RX, TX
int redPin = 3;
int greenPin = 5;
int bluePin = 6;

void setup()
{
    BT.begin(9600);
    Serial.begin(9600);
    pinMode(redPin, OUTPUT);
    pinMode(greenPin, OUTPUT);
    pinMode(bluePin, OUTPUT);
}

void loop()
{
    if (BT.available())
    {
        String input = BT.readStringUntil('\n');
        Serial.println(input);
        int r = input.substring(input.indexOf('R') + 1, input.indexOf('G')).toInt();
        int g = input.substring(input.indexOf('G') + 1, input.indexOf('B')).toInt();
        int b = input.substring(input.indexOf('B') + 1).toInt();

        analogWrite(redPin, 255 - r);
        analogWrite(greenPin, 255 - g);
        analogWrite(bluePin, 255 - b);
    }
}
