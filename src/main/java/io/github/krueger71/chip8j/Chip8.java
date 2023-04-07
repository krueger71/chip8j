package io.github.krueger71.chip8j;

import java.util.logging.Logger;

/**
 * Chip8-model that is run by calling the step()-function. Input is provided by setting the keyboard status true/false
 * whenever key is pressed/not pressed. Sound should play whenever the st-register is non-zero. A bitmap display should
 * be drawn/re-drawn with the contents of the display-buffer whenever display_update is true. When display has been drawn
 * the display update should be set to false.
 */
class Chip8 {
    /**
     * Memory size in bytes
     */
    public static final short MEMORY_SIZE = 4096;
    /**
     * Program start
     */
    public static final short PROGRAM_START = 0x200;
    /**
     * Number of general purpose registers
     */
    public static final byte NUMBER_OF_REGISTERS = 16;
    /**
     * Size of stack
     */
    public static final byte STACK_SIZE = 16;
    /**
     * Width of display in pixels
     */
    public static final byte DISPLAY_WIDTH = 64;
    /**
     * Height of display in pixels
     */
    public static final byte DISPLAY_HEIGHT = 32;
    /**
     * Size of fonts in bytes
     */
    public static final byte FONTS_SIZE = 16 * 5;
    /**
     * Default fonts
     */
    public static final byte[] FONTS = {
        (byte) 0xF0, (byte) 0x90, (byte) 0x90, (byte) 0x90, (byte) 0xF0, // 0
        (byte) 0x20, (byte) 0x60, (byte) 0x20, (byte) 0x20, (byte) 0x70, // 1
        (byte) 0xF0, (byte) 0x10, (byte) 0xF0, (byte) 0x80, (byte) 0xF0, // 2
        (byte) 0xF0, (byte) 0x10, (byte) 0xF0, (byte) 0x10, (byte) 0xF0, // 3
        (byte) 0x90, (byte) 0x90, (byte) 0xF0, (byte) 0x10, (byte) 0x10, // 4
        (byte) 0xF0, (byte) 0x80, (byte) 0xF0, (byte) 0x10, (byte) 0xF0, // 5
        (byte) 0xF0, (byte) 0x80, (byte) 0xF0, (byte) 0x90, (byte) 0xF0, // 6
        (byte) 0xF0, (byte) 0x10, (byte) 0x20, (byte) 0x40, (byte) 0x40, // 7
        (byte) 0xF0, (byte) 0x90, (byte) 0xF0, (byte) 0x90, (byte) 0xF0, // 8
        (byte) 0xF0, (byte) 0x90, (byte) 0xF0, (byte) 0x10, (byte) 0xF0, // 9
        (byte) 0xF0, (byte) 0x90, (byte) 0xF0, (byte) 0x90, (byte) 0x90, // A
        (byte) 0xE0, (byte) 0x90, (byte) 0xE0, (byte) 0x90, (byte) 0xE0, // B
        (byte) 0xF0, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0xF0, // C
        (byte) 0xE0, (byte) 0x90, (byte) 0x90, (byte) 0x90, (byte) 0xE0, // D
        (byte) 0xF0, (byte) 0x80, (byte) 0xF0, (byte) 0x80, (byte) 0xF0, // E
        (byte) 0xF0, (byte) 0x80, (byte) 0xF0, (byte) 0x80, (byte) 0x80, // F
    };
    /**
     * Size of the keyboard
     */
    public static final byte KEYBOARD_SIZE = 16;
    private static final Logger log = Logger.getLogger(Chip8.class.getName());
    /**
     * Display "buffer" output as 2-d array of bool
     */
    final boolean[][] display;
    /**
     * Keyboard input as array of bool
     */
    final boolean[] keyboard;
    /**
     * Options/quirks
     */
    final Quirks quirks;
    /**
     * RAM
     */
    private final byte[] memory;
    /**
     * General purpose registers
     */
    private final char[] registers;
    /**
     * Stack
     */
    private final char[] stack;
    /**
     * Stack pointer
     */
    private final byte sp;
    /**
     * Delay timer register
     */
    byte dt;
    /**
     * Sound timer register
     */
    byte st;
    /**
     * Display has been updated. Redraw the display on target and set to false
     */
    boolean display_update;
    /**
     * Index register
     */
    private char i;
    /**
     * Program counter
     */
    private char pc;

    Chip8(byte[] program, Quirks quirks) {
        memory = new byte[MEMORY_SIZE];

        // Load fonts from address 0x0000
        System.arraycopy(FONTS, 0, memory, 0, FONTS_SIZE);

        // Load program from PROGRAM_START
        System.arraycopy(program, 0, memory, 512, program.length);

        registers = new char[NUMBER_OF_REGISTERS];
        dt = 0;
        st = 0;
        i = 0;
        pc = PROGRAM_START;
        sp = 0;
        stack = new char[STACK_SIZE];
        display = new boolean[DISPLAY_HEIGHT][DISPLAY_WIDTH];
        display_update = false;
        keyboard = new boolean[KEYBOARD_SIZE];
        this.quirks = quirks;
    }

    /**
     * Fetch, decode and execute one instruction
     */
    void step() {
        var instr = fetch();
        execute(instr);
    }

    /**
     * Fetch an instruction and increment the program counter
     */
    char fetch() {
        char instr = (char) ((memory[pc] & 0xFF) << 8 | (memory[1 + pc] & 0xFF));
        pc += 2;

        return instr;
    }

    /**
     * Decode and execute instruction
     */
    void execute(char instr) {
        var i = (byte) ((instr & 0xF000) >> 12);
        var x = (byte) ((instr & 0x0F00) >> 8);
        var y = (byte) ((instr & 0x00F0) >> 4);
        var n = (byte) (instr & 0x000F);
        var nn = (byte) (instr & 0x00FF);
        var nnn = (char) (instr & 0x0FFF);

        //log.info(() -> "i=%x x=%x y=%x n=%x nn=%x nnn=%x".formatted(i, x, y, n, nn, (int) nnn));

        switch (i) {
            case 0x00 -> {
                switch (nnn) {
                    case 0x0E0 -> cls();
                    case 0x0EE -> ret();
                    default -> sys(nnn);
                }
            }
            case 0x1 -> jmp(nnn);
            case 0x2 -> call(nnn);
            case 0x3 -> skeb(x, nn);
            case 0x4 -> skneb(x, nn);
            case 0x5 -> ske(x, y);
            case 0x6 -> ldb(x, nn);
            case 0x7 -> addb(x, nn);
            case 0x8 -> {
                switch (n) {
                    case 0x0 -> ld(x, y);
                    case 0x1 -> or(x, y);
                    case 0x2 -> and(x, y);
                    case 0x3 -> xor(x, y);
                    case 0x4 -> add(x, y);
                    case 0x5 -> sub(x, y);
                    case 0x6 -> shr(x, y);
                    case 0x7 -> subr(x, y);
                    case 0xE -> shl(x, y);
                    default -> err(instr);
                }
            }
            case 0x9 -> skne(x, y);
            case 0xA -> ldi(nnn);
            case 0xB -> jmpz(nnn);
            case 0xC -> rnd(x, nn);
            case 0xD -> draw(x, y, n);
            case 0xE -> {
                switch (nn) {
                    case (byte) 0x9E -> skp(x);
                    case (byte) 0xA1 -> sknp(x);
                    default -> err(instr);
                }
            }
            case 0xF -> {
                switch (nn) {
                    case 0x07 -> ldft(x);
                    case 0x0a -> ldkp(x);
                    case 0x15 -> ldtt(x);
                    case 0x18 -> ldst(x);
                    case 0x1e -> addi(x);
                    case 0x29 -> font(x);
                    case 0x33 -> bcd(x);
                    case 0x55 -> sreg(x);
                    case 0x65 -> lreg(x);
                    default -> err(instr);
                }
            }
            default -> err(instr);
        }
    }

    private void lreg(byte x) {
        throw new UnsupportedOperationException();
    }

    private void sreg(byte x) {
        throw new UnsupportedOperationException();
    }

    private void bcd(byte x) {
        throw new UnsupportedOperationException();
    }

    private void font(byte x) {
        throw new UnsupportedOperationException();
    }

    private void addi(byte x) {
        throw new UnsupportedOperationException();
    }

    private void ldst(byte x) {
        throw new UnsupportedOperationException();
    }

    private void ldtt(byte x) {
        throw new UnsupportedOperationException();
    }

    private void ldkp(byte x) {
        throw new UnsupportedOperationException();
    }

    private void ldft(byte x) {
        throw new UnsupportedOperationException();
    }

    private void sknp(byte x) {
        throw new UnsupportedOperationException();
    }

    private void skp(byte x) {
        throw new UnsupportedOperationException();
    }

    /**
     * Dxyn - DRAW Vx, Vy, n. Draw sprite of height n from memory location I at location VX, VY using XOR and collision
     * status in VF (if any bit is flipped from 1 to 0).
     *
     * @param x x coord
     * @param y y coord
     * @param n height of sprite
     */
    private void draw(byte x, byte y, byte n) {
        var px = registers[x] % DISPLAY_WIDTH;
        var py = registers[y] % DISPLAY_HEIGHT;
        registers[0xF] = 0;

        for (var dy = 0; dy < n; dy++) {
            if (quirks.clipping && (py + dy) >= DISPLAY_HEIGHT) {
                break;
            }

            var b = memory[i + dy];

            for (var dx = 0; dx < 8; dx++) {
                if (quirks.clipping && (px + dx) >= DISPLAY_WIDTH) {
                    break;
                }

                var old = display[(py + dy) % DISPLAY_HEIGHT][(px + dx) % DISPLAY_WIDTH];
                var nyw = ((b >> (7 - dx)) & 1) == 1;

                if (nyw) {
                    if (old) {
                        nyw = false;
                        registers[0xF] = 1;
                    }

                    display[(py + dy) % DISPLAY_HEIGHT][(px + dx) % DISPLAY_WIDTH] = nyw;
                    display_update = true;
                }
            }
        }
    }

    private void rnd(byte x, byte nn) {
        throw new UnsupportedOperationException();
    }

    private void jmpz(char nnn) {
        throw new UnsupportedOperationException();
    }

    /**
     * Annn - LD I. Set index register to nnn.
     *
     * @param nnn address
     */
    private void ldi(char nnn) {
        i = nnn;
    }

    private void skne(byte x, byte y) {
        throw new UnsupportedOperationException();
    }

    private void err(int instr) {
        throw new UnsupportedOperationException(Integer.toUnsignedString(instr, 16));
    }

    private void shl(byte x, byte y) {
        throw new UnsupportedOperationException();
    }

    private void subr(byte x, byte y) {
        throw new UnsupportedOperationException();
    }

    private void shr(byte x, byte y) {
        throw new UnsupportedOperationException();
    }

    private void sub(byte x, byte y) {
        throw new UnsupportedOperationException();
    }

    private void add(byte x, byte y) {
        throw new UnsupportedOperationException();
    }

    private void xor(byte x, byte y) {
        throw new UnsupportedOperationException();
    }

    private void and(byte x, byte y) {
        throw new UnsupportedOperationException();
    }

    private void or(byte x, byte y) {
        throw new UnsupportedOperationException();
    }

    private void ld(byte x, byte y) {
        throw new UnsupportedOperationException();
    }

    /**
     * 7xkk - ADDB Vx, byte. Add byte to VX (without overflow status).
     *
     * @param x  register
     * @param nn byte
     */
    private void addb(byte x, byte nn) {
        registers[x] = (char) (registers[x] + nn);
    }

    /**
     * 6xkk - LDB Vx, byte. Load register VX with byte.
     *
     * @param x  byte
     * @param nn register
     */
    private void ldb(byte x, byte nn) {
        registers[x] = (char) nn;
    }

    private void ske(byte x, byte y) {
        throw new UnsupportedOperationException();
    }

    private void skneb(byte x, byte nn) {
        throw new UnsupportedOperationException();
    }

    private void skeb(byte x, byte nn) {
        throw new UnsupportedOperationException();
    }

    private void call(char nnn) {
        throw new UnsupportedOperationException();
    }

    /**
     * 1nnn - JMP addr. Jump to address.
     *
     * @param nnn address
     */
    private void jmp(char nnn) {
        pc = nnn;
    }

    private void sys(char nnn) {
        throw new UnsupportedOperationException();
    }

    private void ret() {
        throw new UnsupportedOperationException();
    }

    /**
     * 00E0 - CLS. Clear the screen.
     */
    private void cls() {
        for (var y = 0; y < DISPLAY_HEIGHT; y++)
            for (var x = 0; x < DISPLAY_WIDTH; x++)
                display[y][x] = false;
        display_update = true;
    }

    record Quirks(
        /** Quirk: AND, OR, XOR reset VF to zero*/
        boolean vf_reset,
        /** Quirk: Memory load/store registers operations increment I*/
        boolean memory,
        /** Quirk: Only one draw operation per frame*/
        boolean display_wait,
        /** Quirk: Drawing operations clip instead of wrap*/
        boolean clipping,
        /** Quirk: Shifting operations use only VX instead of VY*/
        boolean shifting,
        /** Quirk: Jump with offset operation BNNN will work as BXNN*/
        boolean jumping) {
    }
}
