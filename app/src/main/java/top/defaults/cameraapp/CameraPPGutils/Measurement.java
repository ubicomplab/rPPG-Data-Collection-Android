package top.defaults.cameraapp.CameraPPGutils;

import java.util.Date;

class Measurement<T> {
    final Date timestamp;
    final T measurement;

    // Structure of measurement values with timestamps
    Measurement(Date timestamp, T measurement) {
        this.timestamp = timestamp;
        this.measurement = measurement;
    }
}
