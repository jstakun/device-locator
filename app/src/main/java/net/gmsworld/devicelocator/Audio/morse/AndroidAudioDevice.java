package net.gmsworld.devicelocator.Audio.morse;

/**
 * Created by jstakun on 8/12/17.
 * from https://github.com/uschwar/morsecode/tree/master/MorseService/src/at/schwar/android/morse/service
 */

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class AndroidAudioDevice {

    private static final String LOG_ID = AndroidAudioDevice.class.getCanonicalName();
    private AudioTrack track;
    short[] buffer = new short[1024];
    private int samplesWritten;
    private Boolean active = false;

    public AndroidAudioDevice(int samplingrate) {
        int minSize = AudioTrack.getMinBufferSize(samplingrate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        track = new AudioTrack(AudioManager.STREAM_RING, samplingrate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, minSize,
                AudioTrack.MODE_STREAM);
        track.play();
        this.active = true;
    }

    public Boolean writeSamples(float[] samples) {
        try {
            if (active) {
                fillBuffer(samples);
                track.write(buffer, 0, samples.length);
            }
        } catch (IllegalStateException e) {
            //Sometimes Android fails with this..
        }
        return active;
    }

    public Boolean writeSamples(short[] samples) {
        try {
            if (active) {
                samplesWritten = 0;
                while (samplesWritten < samples.length) {
                    samplesWritten += track.write(samples, samplesWritten,
                            Math.min(buffer.length, samples.length - samplesWritten));
                }
            }
        } catch (IllegalStateException e) {
            //Sometimes Android fails with this..
        }
        return active;
    }

    private void fillBuffer(float[] samples) {
        if (buffer.length < samples.length)
            buffer = new short[samples.length];

        for (int i = 0; i < samples.length; i++)
            buffer[i] = (short) (samples[i] * Short.MAX_VALUE);
    }

    public void stop() {
        tryToStopTrack(track);
        active = false;
    }

    private void tryToStopTrack(AudioTrack track2) {
        if (null != track && track.getState() != AudioTrack.STATE_UNINITIALIZED) {
            track.stop();
        }

    }

    public void release() {
        tryToRelease(track);
        active = false;
    }

    private void tryToRelease(AudioTrack track2) {
        if (null != track && track.getState() != AudioTrack.STATE_UNINITIALIZED) {
            track.release();
        }
    }

    public Boolean isActive() {
        return active;
    }
}

