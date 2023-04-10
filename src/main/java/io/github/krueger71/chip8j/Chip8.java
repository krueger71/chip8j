package io.github.krueger71.chip8j;

import java.util.Random;
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
    public static final int KEYBOARD_SIZE = 16;
    private static final Logger log = Logger.getLogger(Chip8.class.getName());
    /**
     * Options/quirks
     */
    private final Quirks quirks;
    /**
     * Display "buffer" output as 2-d array of bool
     */
    private final boolean[][] display;
    /**
     * Keyboard input as array of bool
     */
    private final boolean[] keyboard;
    /**
     * RAM
     */
    private final byte[] memory;
    /**
     * General purpose registers
     */
    private final byte[] registers;
    /**
     * Stack
     */
    private final char[] stack;
    private final Random rand;
    /**
     * Delay timer register
     */
    private byte dt;
    /**
     * Sound timer register
     */
    private byte st;
    /**
     * Stack pointer
     */
    private int sp;
    /**
     * Display has been updated. Redraw the display on target and set to false
     */
    private boolean displayUpdate;
    /**
     * Index register
     */
    private char i;
    /**
     * Program counter
     */
    private char pc;
    Chip8(byte[] program, Quirks quirks) {
        rand = new Random();
        memory = new byte[MEMORY_SIZE];

        // Load fonts from address 0x0000
        System.arraycopy(FONTS, 0, memory, 0, FONTS_SIZE);

        // Load program from PROGRAM_START
        System.arraycopy(program, 0, memory, 512, program.length);

        registers = new byte[NUMBER_OF_REGISTERS];
        dt = 0;
        st = 0;
        i = 0;
        pc = PROGRAM_START;
        sp = 0;
        stack = new char[STACK_SIZE];
        display = new boolean[DISPLAY_HEIGHT][DISPLAY_WIDTH];
        displayUpdate = false;
        keyboard = new boolean[KEYBOARD_SIZE];
        this.quirks = quirks;
    }

    public byte[] getMemory() {
        return memory;
    }

    public int getDt() {
        return Byte.toUnsignedInt(dt);
    }

    public void setDt(int dt) {
        this.dt = (byte) dt;
    }

    public int getSt() {
        return Byte.toUnsignedInt(st);
    }

    public void setSt(int st) {
        this.st = (byte) st;
    }

    public Quirks getQuirks() {
        return quirks;
    }

    public boolean[] getKeyboard() {
        return keyboard;
    }

    public boolean[][] getDisplay() {
        return display;
    }

    boolean isDisplayUpdate() {
        return displayUpdate;
    }

    void setDisplayUpdate(boolean displayUpdate) {
        this.displayUpdate = displayUpdate;
    }

    /**
     * Set Chip8 key to status.
     *
     * @param keyCode 0x0 - 0xF
     * @param b       status true/pressed, false/not pressed
     */
    void setKey(int keyCode, boolean b) {
        keyboard[keyCode] = b;
    }

    /**
     * Get register as unsigned int.
     *
     * @param register register
     * @return register byte as unsigned int
     */
    int getRegister(int register) {
        return Byte.toUnsignedInt(registers[register]);
    }

    /**
     * Set register as integer. Will be cast to byte.
     *
     * @param register register
     * @param data     integer will be cast to byte
     */
    void setRegister(int register, int data) {
        registers[register] = (byte) data;
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
        var i = ((instr & 0xF000) >> 12);
        var x = ((instr & 0x0F00) >> 8);
        var y = ((instr & 0x00F0) >> 4);
        var n = (instr & 0x000F);
        var nn = (instr & 0x00FF);
        var nnn = (instr & 0x0FFF);

        //log.info(() -> "instr=%X i=%X x=%X y=%X n=%X nn=%X nnn=%X".formatted((int) instr, i, x, y, n, nn, (int) nnn));

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

    /**
     * Fx65 - LREG Vx. Load register V0 to VX from memory starting at I.
     *
     * @param x register
     */
    private void lreg(int x) {
        for (int r = 0; r <= x; r++) {
            setRegister(r, memory[i + r]);
        }

        if (quirks.memory())
            i += x + 1;
    }

    /**
     * Fx55 - SREG Vx. Store registers V0 to VX in memory starting at I.
     *
     * @param x register
     */
    private void sreg(int x) {
        for (int r = 0; r <= x; r++) {
            memory[i + r] = (byte) getRegister(r);
        }

        if (quirks.memory())
            i += x + 1;
    }

    /**
     * Fx33 - BCD Vx. Store BCD value of VX in I, I+1 and I+2.
     *
     * @param x register
     */
    private void bcd(int x) {
        var val = getRegister(x);
        memory[i] = (byte) (val % 1000 / 100);
        memory[i + 1] = (byte) (val % 100 / 10);
        memory[i + 2] = (byte) (val % 10);
    }

    /**
     * Fx29 - FONT Vx. Load I with font for key num in VX.
     *
     * @param x register
     */
    private void font(int x) {
        //self.i = (self.registers[x] * 5) as usize;
        i = (char) (5 * getRegister(x));
    }

    /**
     * Fx1E - ADDI VX. Set I = I + VX
     *
     * @param x register
     */
    private void addi(int x) {
        i += getRegister(x);
    }

    /**
     * Fx18 - LDST Vx. Set sound timer to value from VX.
     *
     * @param x register
     */
    private void ldst(int x) {
        st = (byte) getRegister(x);
    }

    /**
     * Fx15 - LDTT Vx. Set delay timer with value from VX.
     *
     * @param x register
     */
    private void ldtt(int x) {
        setDt(getRegister(x));
    }

    /**
     * Fx0A - LDKP Vx. Wait for a keypress and load the key num into VX.
     *
     * @param x register
     */
    private void ldkp(int x) {
        var wait = true;
        for (int key = 0; key < keyboard.length; key++) {
            if (keyboard[key]) {
                setRegister(x, key);
                wait = false;
                keyboard[key] = false;
                break;
            }
        }

        if (wait) {
            pc -= 2;
        }
    }

    /**
     * Fx07 - LDFT Vx. Load VX with delay timer value.
     *
     * @param x register
     */
    private void ldft(int x) {
        setRegister(x, getDt());
    }

    /**
     * ExA1 - SKNP Vx. Skip next instruction if key number in VX is not pressed.
     *
     * @param x register
     */
    private void sknp(int x) {
        if (!keyboard[getRegister(x)])
            pc += 2;
    }

    /**
     * Ex9E - SKP Vx. Skip next instruction if key number in VX is pressed.
     *
     * @param x register
     */
    private void skp(int x) {
        if (keyboard[getRegister(x)])
            pc += 2;
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
        var px = getRegister(x) % DISPLAY_WIDTH;
        var py = getRegister(y) % DISPLAY_HEIGHT;
        setRegister(0xF, 0);

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
                    displayUpdate = true;
                }
            }
        }
    }

    /**
     * Cxkk - RND Vx, byte. Set VX to (random number AND byte).
     *
     * @param x  register
     * @param nn data
     */
    private void rnd(int x, int nn) {
        setRegister(x, rand.nextInt(0, 256) & nn);
    }

    /**
     * Bnnn - JMPZ addr. Jump to nnn + V0.
     *
     * @param nnn address
     */
    private void jmpz(int nnn) {
        if (quirks.jumping()) {
            var x = nnn >> 8;
            pc = (char) (nnn + getRegister(x));
        } else {
            pc = (char) (nnn + getRegister(0));
        }
    }

    /**
     * Annn - LD I. Set index register to nnn.
     *
     * @param nnn address
     */
    private void ldi(int nnn) {
        i = (char) nnn;
    }

    /**
     * 9xy0 - SKNE Vx, Vy. Skip next instruction if VX != VY.
     *
     * @param x register
     * @param y register
     */
    private void skne(int x, int y) {
        if (getRegister(x) != getRegister(y))
            pc += 2;
    }

    /**
     * Unsupported instruction
     *
     * @param instr
     * @throws UnsupportedOperationException
     */
    private void err(int instr) {
        throw new UnsupportedOperationException(Integer.toUnsignedString(instr, 16));
    }

    /**
     * 8xyE - SHL Vx. Shift VX left with bit 7 before shift in VF. Remember that VX can be the same as VF. Instruction with quirks.
     *
     * @param x register
     * @param y register
     */
    private void shl(int x, int y) {
        var val = quirks.shifting() ? getRegister(x) : getRegister(y);
        setRegister(x, val << 1);
        setRegister(0xF, 1 & (val >> 7));
    }

    /**
     * 8xy7 - SUBR Vx, Vy. Set VX = VY - VX with borrow status in VF (not borrow means set). Remember that VX can be the same as VF.
     *
     * @param x
     * @param y
     */
    private void subr(int x, int y) {
        var result = getRegister(y) - getRegister(x);
        setRegister(x, result);
        setRegister(0xF, result < 0 ? 0 : 1);

    }

    /**
     * 8xy6 - SHR Vx. Shift VX right with bit 0 before shift in VF. Remember that VX can be the same as VF. Instruction with quirks.
     *
     * @param x
     * @param y
     */
    private void shr(int x, int y) {
        var val = quirks.shifting() ? getRegister(x) : getRegister(y);
        setRegister(x, val >> 1);
        setRegister(0xF, val & 1);
    }

    /**
     * 8xy5 - SUB Vx, Vy. Set VX = VX - VY with borrow status in VF (not borrow means set). Remember that VX can be the same as VF.
     *
     * @param x register
     * @param y register
     */
    private void sub(int x, int y) {
        var result = getRegister(x) - getRegister(y);
        setRegister(x, result);
        setRegister(0xF, result < 0 ? 0 : 1);
    }

    /**
     * 8xy4 - ADD Vx, Vy. Set VX = VX + VY with carry status in VF. Remember that VX can be the same as VF.
     *
     * @param x register
     * @param y register
     */
    private void add(int x, int y) {
        var result = getRegister(x) + getRegister(y);
        setRegister(x, result);
        setRegister(0xF, result > 0xFF ? 1 : 0);
    }

    /**
     * 8xy3 - XOR Vx, Vy. Set VX = VX XOR VY.
     *
     * @param x register
     * @param y register
     */
    private void xor(int x, int y) {
        if (quirks.vfReset())
            setRegister(0xF, 0);

        setRegister(x, getRegister(x) ^ getRegister(y));
    }

    /**
     * 8xy2 - AND Vx, Vy. Set VX = VX AND VY.
     *
     * @param x register
     * @param y register
     */
    private void and(int x, int y) {
        if (quirks.vfReset())
            setRegister(0xF, 0);

        setRegister(x, getRegister(x) & getRegister(y));
    }

    /**
     * 8xy1 - OR Vx, Vy. Set VX = VX OR VY.
     *
     * @param x register
     * @param y register
     */
    private void or(int x, int y) {
        if (quirks.vfReset())
            setRegister(0xF, 0);

        setRegister(x, getRegister(x) | getRegister(y));
    }

    /**
     * 8xy0 - LD Vx, Vy. Load register VX with register VY.
     *
     * @param x register
     * @param y register
     */
    private void ld(int x, int y) {
        setRegister(x, getRegister(y));
    }

    /**
     * 7xkk - ADDB Vx, byte. Add byte to VX (without overflow status).
     *
     * @param x  register
     * @param nn byte
     */
    private void addb(int x, int nn) {
        setRegister(x, getRegister(x) + nn);
    }

    /**
     * 6xkk - LDB Vx, byte. Load register VX with byte.
     *
     * @param x  byte
     * @param nn register
     */
    private void ldb(int x, int nn) {
        setRegister(x, nn);
    }

    /**
     * 5xy0 - SKE Vx, Vy. Skip next instruction if VX == VY.
     *
     * @param x register
     * @param y register
     */
    private void ske(int x, int y) {
        if (getRegister(x) == getRegister(y))
            pc += 2;
    }

    /**
     * 4xkk - SKNEB Vx, byte. Skip next instruction if VX != byte.
     *
     * @param x  register
     * @param nn data
     */
    private void skneb(int x, int nn) {
        if (getRegister(x) != nn)
            this.pc += 2;
    }

    /**
     * 3xkk - SKEB Vx, byte. Skip next instruction if VX == byte.
     *
     * @param x  register
     * @param nn data
     */
    private void skeb(int x, int nn) {
        if (getRegister(x) == nn)
            this.pc += 2;
    }

    /**
     * 2nnn - CALL addr. Call subroutine at address.
     *
     * @param nnn address
     */
    private void call(int nnn) {
        stack[sp] = pc;
        sp += 1;
        pc = (char) nnn;
    }

    /**
     * 1nnn - JMP addr. Jump to address.
     *
     * @param nnn address
     */
    private void jmp(int nnn) {
        pc = (char) nnn;
    }

    /**
     * 0nnn - SYS addr. Jump to machine code at address (unused in practice).
     *
     * @param nnn address
     */
    private void sys(int nnn) {
    }

    /**
     * 00EE - RET. Return from subroutine.
     */
    private void ret() {
        sp -= 1;
        pc = stack[sp];
    }

    /**
     * 00E0 - CLS. Clear the screen.
     */
    private void cls() {
        for (var y = 0; y < DISPLAY_HEIGHT; y++)
            for (var x = 0; x < DISPLAY_WIDTH; x++)
                display[y][x] = false;
        setDisplayUpdate(true);
    }

    /**
     * Quirks to get Chip8 to work with different roms
     *
     * @param vfReset     AND, OR, XOR reset VF to zero
     * @param memory      Memory load/store registers operations increment I
     * @param displayWait Only one draw operation per frame
     * @param clipping    Drawing operations clip instead of wrap
     * @param shifting    Shifting operations use only VX instead of VY
     * @param jumping     Jump with offset operation BNNN will work as BXNN
     */
    record Quirks(
        boolean vfReset,
        boolean memory,
        boolean displayWait,
        boolean clipping,
        boolean shifting,
        boolean jumping) {
    }
}
