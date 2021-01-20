# SeizureDetection

This is the official repository of Seizure's Detection Android APK. This application predicts a pacient's risk of having a seizure.

To run this application, a H10 Polar sensor from firmware version 3.0.35 onwards is required.

Heart rate as beats per minute. RR Interval in ms and 1/1024 format.
Electrocardiography (ECG) data in µV. Default epoch for timestamp is 1.1.2000

## Usage

1. Login Activity: to create a new user, click on the "register" button
2. Register Activity: fill in your email, height (cm), weight (kg) and choose a password (more than 6 characters)
3. Register Activity: click "Join"
4. Login Activity: click "Login"
5. Main Activity: make sure you are wearing the H10 Polar sensor and that the firmware version is at least 3.0.35
6. MainActivity: choose the name of the device. It is written on the top of the sensor.
7. MainActivity: choose the compression rate between 2, 4 or 6 (recommended).
8. Main Activity: click the play button to start recording
9. LiveActivity: wait until it connects to the sensor and starts recording. This should take about 3s. If it does not connect, wet the sensor and try again.
10. Live Activity: when you are satisfied with the amount of data recorded (at least 3s but we recommend 9s), press the "Stop recording" button
11. ResultActivity: the application will compute the probability of having a seizure
12. ResultActivity: in case the probability is more than zero and the application is run on a smartphone, it will dial the number to call an ambulance

Note: profile info can be changed clicking on the button at the top right of the MainActivity.

## Support

y.martin@epfl.ch, ana.morenomartinez@epfl.ch, maria.medinahernandez@epfl.ch

## Authors

This application was developped by Ana Moreno Martínez, Yves Martin and María José Medina. It is based on the work of Ahmed Kooli for his semester project "ECG Annotator System for Evaluation of Cardiovascular Anomalies".