package batching;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class Batch {

    public static void main(String args[]) {
        String filename = "./traces/val_trace_gcc.txt";
        String outputFile = "./traces/val_trace_gcc_reordered.txt";
        int fetchRate = 8;
        int queueSize = 8;

        List<Instruction> instructions = readTraceFile(filename, 10000);
        System.out.println("\nRead " + instructions.size() + " instructions from '" + filename + "'");

        List<Dependency> dependencies = getDependencies(instructions);
        System.out.println("\nFound " + dependencies.size() + " dependencies:");
        //System.out.println(dependencies);

        Collections.sort(instructions);

        List<List<Instruction>> batches = scheduleBatches(instructions, 4);
        System.out.println("\nScheduled in " + batches.size() + " clock cycles");

        //Lists Dependencies
        /*int i = 0;
        while (instructions.get(i).dependencies.size() == 0) {
            System.out.println(instructions.get(i));
            i++;
        }
        */

        int cycle = 0;
        for(List<Instruction> batch : batches){
            System.out.println("Cyles " + cycle + ": " + batch);
            cycle++;
        }
        
        // Flatten the batches into a single list (reordered trace)
        List<Instruction> reordered = flattenBatches(batches);

        // Write reordered instructions to a new trace file
        writeTraceFile(reordered, outputFile);

        Main.main(fetchRate, queueSize, reordered);
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
                    i2.dependencies.add(d);
                    dependencies.add(d);
                }

                if (hasOut) {
                    Dependency d = new Dependency(i1.tag, i2.tag, Dependency.Type.OUT);
                    i2.dependencies.add(d);
                    dependencies.add(d);
                }

                if (hasAnti) {
                    Dependency d = new Dependency(i1.tag, i2.tag, Dependency.Type.ANTI);
                    i2.dependencies.add(d);
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

    public static List<List<Instruction>> scheduleBatches(List<Instruction> instructions, int width) {
    
        int n = instructions.size();
        int[] inDegree = new int[n]; // Number of dependencies for each instruction 
        List<List<Integer>> adjList = new ArrayList<>(); // Adjacency list for dependent instructions
    
        // Creating adjacency list
        for (int i = 0; i < n; i++) {
            adjList.add(new ArrayList<>());
        }
    
        // All dependency types
        for (Instruction inst : instructions) {
            for (Dependency d : inst.dependencies) {
                if (d.type == Dependency.Type.TRUE || d.type == Dependency.Type.OUT || d.type == Dependency.Type.ANTI) {
                    inDegree[d.tag2]++; // Instruction d.tag2 depends on d.tag1
                    adjList.get(d.tag1).add(d.tag2); // Add an edge from d.tag1 to d.tag2
                }
            }
        }
    
        List<List<Instruction>> batches = new ArrayList<>(); // Each batch = instructions issued in one cycle
        boolean[] scheduled = new boolean[n];
        int scheduledCount = 0;

        while(scheduledCount < n) {
            List<Instruction> batch = new ArrayList<>();

            for (int i = 0; i < n && batch.size() < width; i++) {
                if (!scheduled[i] && inDegree[i] == 0) {
                    Instruction inst = instructions.get(i);
                    batch.add(inst);
                    scheduled[i] = true;
                    scheduledCount++;
    
                    // Decrement in-degrees of children
                    for (int neighbor : adjList.get(inst.tag)) {
                        inDegree[neighbor]--;
                    }
                }
            }
             //If nothing was scheduled this cycle, force instruction. (Prevents Deadlocks)
             if (batch.size() == 0) {
                for(int i = 0; i < n; i++){
                    if (!scheduled[i]) {
                        Instruction inst = instructions.get(i);
                        batch.add(inst);
                        scheduled[i] = true;
                        scheduledCount++;

                        for(int neighbor : adjList.get(inst.tag)){
                            inDegree[neighbor]--;
                        }
                        break; //only issue one to break deadlock
                    }
                }
            }
            batches.add(batch);   
        }
        return batches;        
    }
    
    public static List<Instruction> flattenBatches(List<List<Instruction>> batches){
        List<Instruction> reordered = new ArrayList<>();
        for (List<Instruction> batch : batches){
            reordered.addAll(batch);
        }
        return reordered;
    }

    public static void writeTraceFile(List<Instruction> instructions, String outputFilename) {
    try {
        StringBuilder sb = new StringBuilder();
        for (Instruction inst : instructions) {
            sb.append(String.format("%08x %d %d %d %d\n", 
                inst.pc, inst.op, inst.dest, inst.src1, inst.src2));
        }
        Files.write(Path.of(outputFilename), sb.toString().getBytes());
        System.out.println("Reordered trace written to: " + outputFilename);
    } catch (IOException e) {
        System.err.println("Failed to write reordered trace: " + e.getMessage());
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