package com.ytmusic;

import org.concentus.OpusDecoder;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.*;

public final class AudioPlayer {
    public static volatile Track current;
    public static volatile String status = "";
    public static volatile boolean paused;
    private static volatile double seconds;
    private static volatile int gen;

    public static double seconds() { return seconds; }

    public static void play(Track t) {
        int my = ++gen;
        current = t;
        paused = false;
        seconds = 0;
        status = "Yukleniyor...";
        Thread th = new Thread(() -> run(t, my), "ytmusic-audio");
        th.setDaemon(true);
        th.start();
    }

    public static void stop() {
        gen++;
        current = null;
        status = "";
    }

    public static void togglePause() { paused = !paused; }

    private static void run(Track t, int my) {
        int src = 0;
        final int N = 6;
        int[] bufs = new int[N];
        try {
            String url = YouTubeService.audioUrl(t.url());
            if (my != gen) return;
            OpusDecoder dec = new OpusDecoder(48000, 2);
            DataInputStream in = new DataInputStream(new BufferedInputStream(new RangeStream(url), 1 << 16));

            src = AL10.alGenSources();
            AL10.alSourcei(src, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            Deque<Integer> free = new ArrayDeque<>();
            Map<Integer, Integer> sizes = new HashMap<>();
            for (int i = 0; i < N; i++) { bufs[i] = AL10.alGenBuffers(); free.add(bufs[i]); }

            short[] pcm = new short[5760 * 2];
            short[] acc = new short[48000 * 2];
            int accLen = 0;
            long processed = 0;
            boolean eof = false, first = true;

            while (my == gen) {
                int done = AL10.alGetSourcei(src, AL10.AL_BUFFERS_PROCESSED);
                while (done-- > 0) {
                    int b = AL10.alSourceUnqueueBuffers(src);
                    free.add(b);
                    processed += sizes.getOrDefault(b, 0);
                }
                AL10.alSourcef(src, AL10.AL_GAIN, Config.volume);

                int st = AL10.alGetSourcei(src, AL10.AL_SOURCE_STATE);
                int queued = AL10.alGetSourcei(src, AL10.AL_BUFFERS_QUEUED);
                if (paused && st == AL10.AL_PLAYING) AL10.alSourcePause(src);
                else if (!paused && queued > 0 && st != AL10.AL_PLAYING) AL10.alSourcePlay(src);

                seconds = (processed + AL10.alGetSourcei(src, AL11.AL_SAMPLE_OFFSET)) / 48000.0;

                if (!eof && !free.isEmpty()) {
                    while (accLen < 9600 * 2) { // ~200 ms
                        byte[] f = WebmOpus.next(in);
                        if (f == null) { eof = true; break; }
                        try {
                            int n = dec.decode(f, 0, f.length, pcm, 0, 5760, false);
                            System.arraycopy(pcm, 0, acc, accLen, n * 2);
                            accLen += n * 2;
                        } catch (Exception ignored) {}
                    }
                    if (accLen > 0) {
                        int b = free.poll();
                        ShortBuffer sb = ByteBuffer.allocateDirect(accLen * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
                        sb.put(acc, 0, accLen).flip();
                        AL10.alBufferData(b, AL10.AL_FORMAT_STEREO16, sb, 48000);
                        AL10.alSourceQueueBuffers(src, b);
                        sizes.put(b, accLen / 2);
                        accLen = 0;
                        if (first) { status = ""; first = false; }
                    }
                } else {
                    Thread.sleep(30);
                }
                if (eof && free.size() == N) break;
            }
        } catch (Throwable e) {
            if (my == gen) status = "Hata: " + e.getClass().getSimpleName() + " " + e.getMessage();
        } finally {
            try {
                if (src != 0) {
                    AL10.alSourceStop(src);
                    AL10.alSourcei(src, AL10.AL_BUFFER, 0);
                    AL10.alDeleteSources(src);
                    for (int b : bufs) if (b != 0) AL10.alDeleteBuffers(b);
                }
            } catch (Throwable ignored) {}
            if (my == gen && status.isEmpty()) current = null;
        }
    }
}
