import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

enum State {
    IF,
    ID,
    EX,
    MEM,
    WB;
}

public class Main {
    public static void main(String args[]) {
        /*
            Scanner inputScanner = new Scanner(System.in);
            System.out.println("Enter trace file:");
            String filename = inputScanner.nextLine();
            inputScanner.close();
        */
        String filename = "./traces/val_trace_gcc.txt";

        File file = new File(filename);
        Scanner fileScanner;
        try {
            fileScanner = new Scanner(file);

            List<Instruction> instructions = new ArrayList<>();
            while (fileScanner.hasNextLine()) {
                String line = fileScanner.nextLine();
                String[] split = line.split(" ");
                Instruction newInstruction = new Instruction(
                    Integer.parseInt(split[0], 16), // Converts hexadecimal to int
                    Integer.parseInt(split[1]), 
                    Integer.parseInt(split[2]), 
                    Integer.parseInt(split[3]), 
                    Integer.parseInt(split[4])
                );
                System.out.println(newInstruction.toString());
                instructions.add(newInstruction);
            }

            fileScanner.close();
        } catch (FileNotFoundException e) {
            System.out.println("Error reading file.");
            return;
        }        
    }
}

class Instruction {
    int pc, op, dest, src1, src2;
    State state;

    Instruction(int pc, int op, int dest, int src1, int src2) {
        this.pc = pc;
        this.op = op;
        this.dest = dest;
        this.src1 = src1;
        this.src2 = src2;
        this.state = null;
    }

    public String toString() {
        return pc + " " + op + " " + dest + " " + src1 + " " + src2;
    }
}