package io.github.krueger71.chip8j;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.logging.Logger;

/**
 * Runs a Chip8-instance with JDK built-in support for graphics (java.awt) and sound (javax.sound)
 */
class EmuJdk {
    public static final int INSTRUCTIONS_PER_FRAME = 20;
    public static final int SCALE = 10;
    public static final int FPS = 60;
    private static final Logger log = Logger.getLogger(EmuJdk.class.getName());
    private final Chip8 chip8;
    private final Sound sound;

    EmuJdk(Chip8 chip8) {
        this.chip8 = chip8;
        this.sound = new Sound();
    }

    /**
     * Map keycode to Chip8 keycode. Works best with QWERTY-layouts.
     *
     * @param keyCode key code on the outside
     * @return key code for Chip8
     */
    private static int keymap(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_1 -> 0x1;
            case KeyEvent.VK_2 -> 0x2;
            case KeyEvent.VK_3 -> 0x3;
            case KeyEvent.VK_4 -> 0xC;
            case KeyEvent.VK_Q -> 0x4;
            case KeyEvent.VK_W -> 0x5;
            case KeyEvent.VK_E -> 0x6;
            case KeyEvent.VK_R -> 0xD;
            case KeyEvent.VK_A -> 0x7;
            case KeyEvent.VK_S -> 0x8;
            case KeyEvent.VK_D -> 0x9;
            case KeyEvent.VK_F -> 0xE;
            case KeyEvent.VK_Z -> 0xA;
            case KeyEvent.VK_X -> 0x0;
            case KeyEvent.VK_C -> 0xB;
            case KeyEvent.VK_V -> 0xF;
            default -> -1;
        };
    }

    /**
     * Run initializes graphics and sound as well as starts a loop that runs the Chip8 instance. Exits on exception or
     * user quitting.
     */
    void run() {
        var frame = new JFrame("Chip8 Emulator");

        var component = new JComponent() {
            @Override
            public void paint(Graphics g) {
                var graphics = (Graphics2D) g;
                graphics.setColor(Color.BLACK);
                graphics.fillRect(0, 0, this.getWidth(), this.getHeight());
                graphics.setColor(Color.LIGHT_GRAY);
                graphics.scale(SCALE, SCALE);
                var display = chip8.getDisplay();

                for (var y = 0; y < Chip8.DISPLAY_HEIGHT; y++)
                    for (var x = 0; x < Chip8.DISPLAY_WIDTH; x++)
                        if (display[y][x]) {
                            graphics.fillRect(x, y, 1, 1);
                        }
            }
        };

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        component.setPreferredSize(new Dimension(Chip8.DISPLAY_WIDTH * SCALE, Chip8.DISPLAY_HEIGHT * SCALE));
        component.setDoubleBuffered(true);
        frame.add(component);
        frame.pack();
        frame.setVisible(true);

        frame.addKeyListener(new KeyAdapter() {
            private void setKey(KeyEvent e, boolean on) {
                var key = keymap(e.getKeyCode());
                if (key >= 0)
                    chip8.setKey(key, on);
            }

            @Override
            public void keyPressed(KeyEvent e) {
                setKey(e, true);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                setKey(e, false);
            }
        });

        final int FRAME_TIME = 1000_000_000 / FPS;

        //noinspection InfiniteLoopStatement
        while (true) {
            long startTime = System.nanoTime();

            // Step the Chip8 mul times
            for (var x = 0; x < INSTRUCTIONS_PER_FRAME; x++) {
                chip8.step();

                if (chip8.getQuirks().displayWait() && chip8.isDisplayUpdate()) {
                    break;
                }
            }

            // Decrement delay timer if non-zero
            if (chip8.getDt() > 0) {
                chip8.setDt(chip8.getDt() - 1);
            }

            // Decrement sound timer if non-zero and play sound
            if (chip8.getSt() > 0) {
                sound.play();
                chip8.setSt(chip8.getSt() - 1);
            } else {
                sound.stop();
            }

            // Draw display if Chip8 indicates display is updated
            if (chip8.isDisplayUpdate()) {
                component.repaint();
                chip8.setDisplayUpdate(false); // Chip8 will set this to true whenever something changes on screen
            }

            Toolkit.getDefaultToolkit().sync(); // Sync the display

            long endTime = System.nanoTime();
            long elapsedTime = endTime - startTime;
            if (elapsedTime < FRAME_TIME) {
                try {
                    //noinspection BusyWait
                    Thread.sleep((FRAME_TIME - elapsedTime) / 1000_000);
                } catch (InterruptedException e) {
                    log.warning(() -> "%s".formatted(e.getMessage()));
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    static class Sound {
        private final Clip clip;

        public Sound() {
            int sampleRate = 44100;
            AudioFormat format = new AudioFormat(sampleRate, 8, 1, true, true);
            DataLine.Info info = new DataLine.Info(Clip.class, format);
            try {
                clip = (Clip) AudioSystem.getLine(info);
            } catch (LineUnavailableException e) {
                throw new RuntimeException(e);
            }

            int durationMs = 8192; // duration of the sound in milliseconds
            int frequencyHz = 432; // frequency of the sound in Hertz
            int numSamples = durationMs * sampleRate / 1000;
            byte[] buffer = new byte[numSamples];

            for (int i = 0; i < numSamples; i++) {
                double angle = 2.0 * Math.PI * i * frequencyHz / sampleRate;
                buffer[i] = (byte) (Math.sin(angle) > 0 ? 31 : -32);
            }

            try {
                clip.open(format, buffer, 0, buffer.length);
            } catch (LineUnavailableException e) {
                throw new RuntimeException(e);
            }
        }

        void play() {
            if (!clip.isRunning()) {
                clip.setFramePosition(0);
                clip.setLoopPoints(0, -1);
                clip.loop(Clip.LOOP_CONTINUOUSLY);
                clip.start();
            }

        }

        void stop() {
            if (clip.isRunning()) {
                clip.stop();
            }
        }
    }
}
