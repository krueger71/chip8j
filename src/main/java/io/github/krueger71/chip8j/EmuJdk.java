package io.github.krueger71.chip8j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Runs a Chip8-instance with JDK built-in support for graphics (java.awt) and sound (javax.sound)
 */
class EmuJdk {
    public static final int INSTRUCTIONS_PER_FRAME = 20;
    private final Chip8 chip8;

    EmuJdk(Chip8 chip8) {
        this.chip8 = chip8;
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
                graphics.scale(10, 10);

                for (var y = 0; y < Chip8.DISPLAY_HEIGHT; y++) {
                    for (var x = 0; x < Chip8.DISPLAY_WIDTH; x++) {
                        if (chip8.display[y][x]) {
                            graphics.drawLine(x, y, x, y);  // There is no drawPoint-primitive
                        }
                    }
                }
            }
        };

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(640, 320);
        frame.add(component);
        component.setDoubleBuffered(true);
        frame.setVisible(true);

        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                System.err.println(e.getKeyChar() + " pressed");
            }

            @Override
            public void keyReleased(KeyEvent e) {
                System.err.println(e.getKeyChar() + " released");
            }
        });

        final int FPS = 60;
        final int FRAME_TIME = 1000_000_000 / FPS;

        while (true) {
            long startTime = System.nanoTime();

            // Step the Chip8 mul times
            for (var x = 0; x < INSTRUCTIONS_PER_FRAME; x++) {
                chip8.step();

                if (chip8.quirks.display_wait() && chip8.display_update) {
                    break;
                }
            }

            // Decrement delay timer if non-zero
            if (chip8.dt > 0) {
                chip8.dt -= 1;
            }

            // Decrement sound timer if non-zero and play sound
            if (chip8.st > 0) {
                // Play sound
                chip8.st -= 1;
            }

            // Draw display if Chip8 indicates display is updated
            if (chip8.display_update) {
                component.repaint();
                chip8.display_update = false; // Chip8 will set this to true whenever something changes on screen
            }

            long endTime = System.nanoTime();
            long elapsedTime = endTime - startTime;
            if (elapsedTime < FRAME_TIME) {
                try {
                    Thread.sleep((FRAME_TIME - elapsedTime) / 1000_000);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        }
    }
}
