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
        // Build dependency graph using only RAW dependencies
    
        int n = instructions.size();
        int[] inDegree = new int[n]; // Number of dependencies for each instruction 
        List<List<Integer>> adjList = new ArrayList<>(); // Adjacency list for dependent instructions
    
        // Creating adjacency list
        for (int i = 0; i < n; i++) {
            adjList.add(new ArrayList<>());
        }
    
        // Fill in-degree array and adjacency list based on TRUE dependencies
        for (Instruction inst : instructions) {
            for (Dependency d : inst.dependencies) {
                if (d.type == Dependency.Type.TRUE) {
                    inDegree[d.tag2]++; // Instruction d.tag2 depends on d.tag1
                    adjList.get(d.tag1).add(d.tag2); // Add an edge from d.tag1 to d.tag2
                }
            }
        }
    
        // Step 2: Schedule instructions in batches using a topological sort-like approach
    
        List<List<Instruction>> batches = new ArrayList<>(); // Each batch = instructions issued in one cycle
        List<Instruction> ready = new ArrayList<>(); // Instructions ready to issue (in-degree == 0)
    
        // Find instructions with no dependencies
        for (Instruction inst : instructions) {
            if (inDegree[inst.tag] == 0) {
                ready.add(inst);
            }
        }
    
        // Continue until all instructions are scheduled
        while (!ready.isEmpty()) {
            List<Instruction> batch = new ArrayList<>();      // Instructions issued in the current cycle
            List<Instruction> nextReady = new ArrayList<>();  // Instructions that become ready after this batch
    
            // Issue up to 'width' instructions this cycle
            for (int i = 0; i < Math.min(width, ready.size()); i++) {
                Instruction inst = ready.get(i);
                batch.add(inst);
    
                // Reduce the in-degree of dependent instructions that follow
                for (int neighbor : adjList.get(inst.tag)) {
                    inDegree[neighbor]--;
                    if (inDegree[neighbor] == 0) {
                        // If all dependencies are resolved, mark ready for the next cycle
                        nextReady.add(instructions.get(neighbor));
                    }
                }
            }
    
            // Remove issued instructions from the ready list
            ready.removeAll(batch);
    
            // Add newly ready instructions to be considered in the next cycle
            ready.addAll(nextReady);
    
            // Store the current batch
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