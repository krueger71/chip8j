package io.github.krueger71.chip8j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Chip8Test {

    private Chip8 chip8;

    @BeforeEach
    void setup() {
        chip8 = new Chip8(new byte[]{}, new Chip8.Quirks(false, false, false, false, false, false));
    }

    @Test
    void testGetSetRegister() {
        chip8.setRegister(0, 0xFE);
        assertEquals(0xFE, chip8.getRegister(0));
        chip8.setRegister(0, 0xFF);
        assertEquals(0xFF, chip8.getRegister(0));
        chip8.setRegister(0, 0x1AB);
        assertEquals(0xAB, chip8.getRegister(0));
    }

    @Test
    void testSreg() {
        chip8.setRegister(0, 0xAB);
        chip8.setRegister(1, 0xCD);
        chip8.setRegister(2, 0xEF);
        chip8.execute((char) 0xF255);
        var mem = chip8.getMemory();
        assertEquals(Byte.toUnsignedInt(mem[0]), 0xAB);
        assertEquals(Byte.toUnsignedInt(mem[1]), 0xCD);
        assertEquals(Byte.toUnsignedInt(mem[2]), 0xEF);
    }
}
