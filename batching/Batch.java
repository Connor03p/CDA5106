package batching;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Batch {

    public static void main(String args[]) {
        String filename = "./traces/val_trace_gcc.txt";

        List<Instruction> instructions = readTraceFile(filename, 10);
        System.out.println("Read " + instructions.size() + " instructions.");

        List<Dependency> dependencies = getDependencies(instructions);
        System.out.println("Found " + dependencies.size() + " dependencies:");
        System.out.println(dependencies);
    }

    public static List<Dependency> getDependencies(List<Instruction> instructions) {

        List<Dependency> dependencies = new ArrayList<>();
        
        for (int i = 0; i < instructions.size(); i++) {
            

        }

        return dependencies;
    }

    public static List<Instruction> readTraceFile(String filename, int maxLen) {
        List<Instruction> instructions = new ArrayList<>();
        File file = new File(filename);
        Scanner fileScanner;
        int tagNum = 0;
        
        try {
            fileScanner = new Scanner(file);

            while (fileScanner.hasNextLine() && tagNum < maxLen) {
                String line = fileScanner.nextLine();
                String[] split = line.split(" ");
                Instruction newInstruction = new Instruction(
                    Integer.parseInt(split[0], 16), // Converts hexadecimal to int
                    Integer.parseInt(split[1]), 
                    Integer.parseInt(split[2]), 
                    Integer.parseInt(split[3]), 
                    Integer.parseInt(split[4]),
                    tagNum
                );
                tagNum++;
                instructions.add(newInstruction);
            }

            fileScanner.close();
            return instructions;
        } catch (FileNotFoundException e) {
            System.out.println("Error reading file.");
            return null;
        }
    }
}

class Dependency {
    int tag1 = -1, tag2 = -1;
    Type type = null;
    
    public static enum Type {
        TRUE,
        ANTI,
        OUT;
    }
}

class Instruction implements Comparable<Instruction> {
    int pc, op, dest, src1, src2, tag;
    List<Dependency> dependencies = new ArrayList<>();

    Instruction(int pc, int op, int dest, int src1, int src2, int tag) {
        this.pc = pc;
        this.op = op;
        this.dest = dest;
        this.src1 = src1;
        this.src2 = src2;
        this.tag = tag;
    }

    @Override
    public String toString() {
        return tag + " "
            + "fu{" + op + "} "
            + "src{" + src1 + ", " + src2 + "} "
            + "dst{" + dest + "} ";
    }


    @Override // Sort by PC in ascending order
    public int compareTo(Instruction other) {
        return Integer.compare(this.tag, other.tag); 
    }
}