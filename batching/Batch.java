package batching;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class Batch {

    public static void main(String args[]) {
        String filename = "./traces/val_trace_gcc.txt";

        List<Instruction> instructions = readTraceFile(filename, 10000);
        System.out.println("\nRead " + instructions.size() + " instructions from '" + filename + "'");

        List<Dependency> dependencies = getDependencies(instructions);
        System.out.println("\nFound " + dependencies.size() + " dependencies:");
        //System.out.println(dependencies);

        Collections.sort(instructions);

        int i = 0;
        while (instructions.get(i).dependencies.size() == 0) {
            System.out.println(instructions.get(i));
            i++;
        }
        
    }

    public static List<Dependency> getDependencies(List<Instruction> instructions) {

        List<Dependency> dependencies = new ArrayList<>();
        
        for (int i = 0; i < instructions.size(); i++) {
            Instruction i1 = instructions.get(i);

            // For all instructions after i
            for (int j = i + 1; j < instructions.size(); j++) {
                Instruction i2 = instructions.get(j);
                
                // Check for true (read after write) and output (write after write) dependencies
                boolean hasTrue = (i1.dest != -1) && (i1.dest == i2.src1 || i1.dest == i2.src2);
                boolean hasOut = (i1.dest != -1) && (i1.dest == i2.dest);
                boolean hasAnti = (i1.src1 != -1 && i1.src1 == i2.dest) || (i1.src2 != -1 && i1.src2 == i2.dest);

                if (hasTrue) {
                    Dependency d = new Dependency(i1.tag, i2.tag, Dependency.Type.TRUE);
                    i1.dependencies.add(d);
                    dependencies.add(d);
                }

                if (hasOut) {
                    Dependency d = new Dependency(i1.tag, i2.tag, Dependency.Type.OUT);
                    i1.dependencies.add(d);
                    dependencies.add(d);
                }

                if (hasAnti) {
                    Dependency d = new Dependency(i1.tag, i2.tag, Dependency.Type.ANTI);
                    i1.dependencies.add(d);
                    dependencies.add(d);
                }
                
            }
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

    Dependency(int t1, int t2, Type type) {
        this.type = type;
        this.tag1 = t1;
        this.tag2 = t2;
    }
    
    public static enum Type {
        TRUE, ANTI, OUT;
    }

    @Override
    public String toString() {
        return "(" + tag1 + ", " + tag2 + ": " + type + ")";
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
        return Integer.compare(this.dependencies.size(), other.dependencies.size()); 
    }
}