package io.github.krueger71.chip8j;

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
    public static final int MEMORY_SIZE = 4096;
    /**
     * Program start
     */
    public static final int PROGRAM_START = 0x200;
    /**
     * Number of general purpose registers
     */
    public static final int NUMBER_OF_REGISTERS = 16;
    /**
     * Size of stack
     */
    public static final int STACK_SIZE = 16;
    /**
     * Width of display in pixels
     */
    public static final int DISPLAY_WIDTH = 64;
    /**
     * Height of display in pixels
     */
    public static final int DISPLAY_HEIGHT = 32;
    /**
     * Size of fonts in bytes
     */
    public static final int FONTS_SIZE = 16 * 5;
    /**
     * Default fonts
     */
    public static final char[] FONTS = {0xF0, 0x90, 0x90, 0x90, 0xF0, // 0
        0x20, 0x60, 0x20, 0x20, 0x70, // 1
        0xF0, 0x10, 0xF0, 0x80, 0xF0, // 2
        0xF0, 0x10, 0xF0, 0x10, 0xF0, // 3
        0x90, 0x90, 0xF0, 0x10, 0x10, // 4
        0xF0, 0x80, 0xF0, 0x10, 0xF0, // 5
        0xF0, 0x80, 0xF0, 0x90, 0xF0, // 6
        0xF0, 0x10, 0x20, 0x40, 0x40, // 7
        0xF0, 0x90, 0xF0, 0x90, 0xF0, // 8
        0xF0, 0x90, 0xF0, 0x10, 0xF0, // 9
        0xF0, 0x90, 0xF0, 0x90, 0x90, // A
        0xE0, 0x90, 0xE0, 0x90, 0xE0, // B
        0xF0, 0x80, 0x80, 0x80, 0xF0, // C
        0xE0, 0x90, 0x90, 0x90, 0xE0, // D
        0xF0, 0x80, 0xF0, 0x80, 0xF0, // E
        0xF0, 0x80, 0xF0, 0x80, 0x80, // F
    };
    /**
     * Size of the keyboard
     */
    public static final int KEYBOARD_SIZE = 16;
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
    private final char[] memory;
    /**
     * General purpose registers
     */
    private final char[] registers;
    /**
     * Stack
     */
    private final int[] stack;
    /**
     * Delay timer register
     */
    char dt;
    /**
     * Sound timer register
     */
    char st;
    /**
     * Display has been updated. Redraw the display on target and set to false
     */
    boolean display_update;
    /**
     * Index register
     */
    private int i;
    /**
     * Program counter
     */
    private int pc;
    /**
     * Stack pointer
     */
    private final int sp;

    Chip8(char[] program, Quirks quirks) {
        memory = new char[MEMORY_SIZE];

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
        stack = new int[STACK_SIZE];
        display = new boolean[DISPLAY_HEIGHT][DISPLAY_WIDTH];
        display_update = false;
        keyboard = new boolean[KEYBOARD_SIZE];
        this.quirks = quirks;
    }

    /**
     * Fetch, decode and execute one instruction
     */
    void step() {
        int instr = fetch();
        execute(instr);
    }

    /**
     * Fetch an instruction and increment the program counter
     */
    int fetch() {
        int instr = (((int) memory[pc]) << 8) | ((int) memory[1 + pc]);
        pc += 2;

        return instr;
    }

    /**
     * Decode and execute instruction
     */
    void execute(int instr) {
        var i = ((instr & 0xF000) >> 12);
        var x = ((instr & 0x0F00) >> 8);
        var y = ((instr & 0x00F0) >> 4);
        var n = (instr & 0x000F);
        var nn = (instr & 0x00FF);
        var nnn = (instr & 0x0FFF);

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
                    case 0x9E -> skp(x);
                    case 0xA1 -> sknp(x);
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

    private void lreg(int x) {
        throw new UnsupportedOperationException();
    }

    private void sreg(int x) {
        throw new UnsupportedOperationException();
    }

    private void bcd(int x) {
        throw new UnsupportedOperationException();
    }

    private void font(int x) {
        throw new UnsupportedOperationException();
    }

    private void addi(int x) {
        throw new UnsupportedOperationException();
    }

    private void ldst(int x) {
        throw new UnsupportedOperationException();
    }

    private void ldtt(int x) {
        throw new UnsupportedOperationException();
    }

    private void ldkp(int x) {
        throw new UnsupportedOperationException();
    }

    private void ldft(int x) {
        throw new UnsupportedOperationException();
    }

    private void sknp(int x) {
        throw new UnsupportedOperationException();
    }

    private void skp(int x) {
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
    private void draw(int x, int y, int n) {
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

    private void rnd(int x, int nn) {
        throw new UnsupportedOperationException();
    }

    private void jmpz(int nnn) {
        throw new UnsupportedOperationException();
    }

    /**
     * Annn - LD I. Set index register to nnn.
     *
     * @param nnn address
     */
    private void ldi(int nnn) {
        i = nnn;
    }

    private void skne(int x, int y) {
        throw new UnsupportedOperationException();
    }

    private void err(int instr) {
        throw new UnsupportedOperationException(Integer.toUnsignedString(instr, 16));
    }

    private void shl(int x, int y) {
        throw new UnsupportedOperationException();
    }

    private void subr(int x, int y) {
        throw new UnsupportedOperationException();
    }

    private void shr(int x, int y) {
        throw new UnsupportedOperationException();
    }

    private void sub(int x, int y) {
        throw new UnsupportedOperationException();
    }

    private void add(int x, int y) {
        throw new UnsupportedOperationException();
    }

    private void xor(int x, int y) {
        throw new UnsupportedOperationException();
    }

    private void and(int x, int y) {
        throw new UnsupportedOperationException();
    }

    private void or(int x, int y) {
        throw new UnsupportedOperationException();
    }

    private void ld(int x, int y) {
        throw new UnsupportedOperationException();
    }

    /**
     * 7xkk - ADDB Vx, byte. Add byte to VX (without overflow status).
     *
     * @param x  register
     * @param nn byte
     */
    private void addb(int x, int nn) {
        registers[x] = (char) (registers[x] + nn);
    }

    /**
     * 6xkk - LDB Vx, byte. Load register VX with byte.
     *
     * @param x  byte
     * @param nn register
     */
    private void ldb(int x, int nn) {
        registers[x] = (char) nn;
    }

    private void ske(int x, int y) {
        throw new UnsupportedOperationException();
    }

    private void skneb(int x, int nn) {
        throw new UnsupportedOperationException();
    }

    private void skeb(int x, int nn) {
        throw new UnsupportedOperationException();
    }

    private void call(int nnn) {
        throw new UnsupportedOperationException();
    }

    /**
     * 1nnn - JMP addr. Jump to address.
     *
     * @param nnn address
     */
    private void jmp(int nnn) {
        pc = nnn;
    }

    private void sys(int nnn) {
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
