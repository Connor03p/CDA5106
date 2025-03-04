import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

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
        
        List<Instruction> instructions = new ArrayList<>();

        try {
            fileScanner = new Scanner(file);

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

        // Main Simulator Loop
        do  {
            // Stuff goes here?
            instructions.remove(0);
        }
        while(advanceCycle(instructions));
        System.out.println("Finshed Executing");
        
    }
        
    private static boolean advanceCycle(List<Instruction> instructions) {
        // Advance simulator cycle?
        // int PC += 1?

        // Check if Instruction List is empty, return false
        return instructions.isEmpty();

    }

    /**
     * IF Stage:
     * Should do the following:
     * 1. Push new instruction from instruction list to fake-ROB. Initialize the instruction's data structure
     *    including setting its state to IF.
     * 2. Add the instruction to the dispatch_list and reserve a dispatch queue entry 
     *    (increment count of the number of instruction in the dispatch queue)
     */
    private static void fetch() {
        
    }

    /**
     * ID Stage:
     * Should do the following:
     * From dispatch_list, construct a temp listt of instructions in the ID state 
     * 
     * Scan the temp list in ascending order of tags and if the scheduling queue is not full, then:
     * 1. Remove the instruction from the dispatch_list and add it to the issue_list. Reserve a schedule
     *    queue entry (e.g. increment a count of the number of instructions in the scheduling queue) and
     *    free a dispatch queue entry (e.g. decrement a count of the number of instructions in the dispatch queue)
     * 2. Transition from the ID state to the IS state
     * 3. Rename source operands by lookingg up state in the register file:
     * 
     */
    private static void dispatch() {

    }

    /**
     * IS Stage:
     *  From the issue_list, construct a temp list of instructions whose operands are ready – these are the READY instructions.
     * Scan the READY instructions in ascending order of tags and issue up to N+1 of them. To issue an instruction:
     *  1) Remove the instruction from the issue_list and add it to the execute_list.
     *  2) Transition from the IS state to the EX state.
     *  3) Free up the scheduling queue entry (e.g., decrement a count of the number of instructions in the scheduling queue)
     *  4) Set a timer in the instruction’s data structure that will allow you to model the execution latency 
     */
    private static void issue() {

    }

    /**
     * EX Stage:
     * Should do the following:
     *  1. Remove instruction from execute_list
     *  2. Transition from EX state to WB state
     *  3. Update register file state and wake up dependendant instructions (set operand ready flags)
     */
    private static void execute() {

    }

    /**
     * Writeback Stage:
     * 
     */
    private static void writeback() {

    }

    /**
     * Removes instructions from the head of the fake-ROB
     * until an instruction is reached that is not in the WB state
     */
    private static void fakeRetire() {

    }
}

class Instruction {
    int pc, op, dest, src1, src2;
    
    Instruction(int pc, int op, int dest, int src1, int src2) {
        this.pc = pc;
        this.op = op;
        this.dest = dest;
        this.src1 = src1;
        this.src2 = src2;
    }

    public String toString() {
        return pc + " " + op + " " + dest + " " + src1 + " " + src2;
    }
}

