package io.github.krueger71.chip8j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Main {
    public static void main(String[] args) throws IOException {
        var program = Files.readAllBytes(new File(args[0]).toPath());
        var chip8 = new Chip8(program, new Chip8.Quirks(false, false, false, false, false, false));
        var emujdk = new EmuJdk(chip8);

        emujdk.run();
    }
}
