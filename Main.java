import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

enum State {
    IF,
    ID,
    IS,
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
        int tagNum = 0;
        
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
                    Integer.parseInt(split[4]),
                    tagNum
                );
                tagNum++;
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
            
            // @FIX: This currently just pops from the first index, only done to keep track of how many instructions 
            //        are being passed and to break out of an infinite loop, this is not the correct implementation 
            //        so this needs to be fixed.

            fetch(instructions.get(0));
            instructions.remove(0);
            dispatch();
            issue();
            execute();
            writeback();
            fakeRetire();
        }
        while(advanceCycle(instructions));

        /** 
         * @TODO: Have a proper variable keep track of the number of cycles, and IPC 
         */

        System.out.println("Finshed Executing");
        System.out.println("number of instructions = "  + (tagNum));
        System.out.println("number of cycles       = Not Set");
        System.out.println("IPC                    = Not Set");
        
    }

    static List<Instruction> instructions = new ArrayList<>();

    /**
        This contains a list of instructions in either the IF or ID
        state. The dispatch_list models the Dispatch Queue. (By including both
        the IF and ID states, we don’t need to separately model the pipeline
        registers of the fetch and dispatch stages.)
    */
    static List<Instruction> dispatchList = new ArrayList<>();

    /**
        This contains a list of instructions in the IS state (waiting for
        operands or available issue bandwidth). The issue_list models the
        Scheduling Queue.
    */
    static List<Instruction> issueList = new ArrayList<>();

    /**
        This contains a list of instructions in the EX state (waiting
        for the execution latency of the operation). The execute_list models
        the FUs.
    */
    static List<Instruction> executeList = new ArrayList<>();

    /*
     *  This is the array that will simulate the 'register', its 127 in size since the
     *  current instruction set specifies instructions will only registers 0 - 127
     *  -1 is no reg, so do nothing with that
     * 
     *  set to null if unoccupied
     */
    static Instruction[] register = new Instruction[127];

    /**
     * @param List<Instruction> instructions
     * @return boolean - If the list is empty, return false and stop execution
     */
        
    private static boolean advanceCycle(List<Instruction> instructions) {
        // Advance simulator cycle?
        // int PC += 1?

        // Check if Instruction List is empty, return false
        return !instructions.isEmpty();

    }

    /**
     * IF Stage:
     * Should do the following:
     * 1. Push new instruction from instruction list to fake-ROB. Initialize the instruction's data structure
     *    including setting its state to IF.
     * 2. Add the instruction to the dispatch_list and reserve a dispatch queue entry 
     *    (increment count of the number of instruction in the dispatch queue)
     * 
     * @param Instruction instruction - an instruction to be configured, and added to dispatch_list
     * 
     */
    private static void fetch(Instruction instruction) {
        // Here, we are setting the instruction to IF, not sure what the initializing the data structure is (besides creating it?);
        instruction.setState(State.IF);

        // Push to dispatch_list, dispatchList.size() should give us the count of this arraylist.
        dispatchList.add(instruction);
        System.out.println("Successfully fetched and added to dispatch: " + instruction);
    }

    /**
     * ID Stage:
     * Should do the following:
     * From dispatch_list, construct a temp list of instructions in the ID state 
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
        for (Instruction i : dispatchList) {
            // Only add instructions with 'ID' tag to new list
            // @NOTE - I'm assuming the 1 cycle stall is it starts at IF, otherwise start at 'ID'
            if (i.state == State.ID) {
                // i.state = State.IS;
                // issueList.add(i); // Auto increments because arraylist
                // dispatchList.remove(i); // Same here, auto decrements because arraylist
                boolean isAssigned = false;
                System.out.println("Attempting to assign Instruction to REG[" + i.src1 +"] and REG[" + i.src2 + "]");

                // Assign instruction to a specific register, they need both registers, otherwise stall
                // @NOTE - I'm assuming that, dest is occupied later in writeback so we only carry
                //         if the source regsiters are currently being used.
                
                // Instruction doesn't need a register? Simply proceed
                if (i.src1 == -1 && i.src2 == -1) {
                    isAssigned = true;
                }
                // Instruction is using 1 register, indicated by -1
                else if (i.src2 == -1 && register[i.src1] != null) {
                    register[i.src1] = i;
                    isAssigned = true;
                }
                else if (i.src1 == -1 && register[i.src2] != null) {
                    register[i.src2] = i;
                    isAssigned = true;
                }
                // Instruction is using two registers
                else if (register[i.src1] != null && register[i.src2] != null) {
                    register[i.src1] = i;
                    register[i.src2] = i;
                    isAssigned = true;
                }

                // If an instruction has an avaliable register, then it can proceed to the issues, otherwise
                // stall until that register is avaliable???
                // @NOTE, i'm not sure if this is the correct implementation, subject to change.
                if (isAssigned) {
                    i.state = State.IS;
                    issueList.add(i); // Auto increments because arraylist
                    dispatchList.remove(i); // Same here, auto decrements because arraylist
                }
            }
            else {
                i.state = State.ID;
            }
        }
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
        System.out.println("Issue");

        // Uses iterator to prevent concurrent modification exception
        Iterator<Instruction> itr = issueList.iterator();
        while (itr.hasNext()) {
            Instruction i = itr.next();

            itr.remove();

            // Transition from the IS state to the EX state
            if (i.state == State.IS) {
                i.state = State.EX;
                System.out.println("  Instruction " + i.tag + " issued for execution.");
            }
                
              
            // Move instruction to execute list
            executeList.add(i);
        }
    }

    /**
     * EX Stage:
     * Should do the following:
     *  1. Remove instruction from execute_list
     *  2. Transition from EX state to WB state
     *  3. Update register file state and wake up dependendant instructions (set operand ready flags)
     */
    private static void execute() {
        System.out.println("Execute");
        Iterator<Instruction> itr = executeList.iterator();
        while (itr.hasNext()) {
            Instruction i = itr.next();
            
            // Increment timer to simulate execution latency
            if (i.exeTimer < i.cycles){
                i.exeTimer++; 
                System.out.println("  Instruction " + i.tag + " timer: " + i.exeTimer + "/" + i.cycles);
            }
               
            
            // When the timer finishes, move to WB state
            else if (i.state == State.EX) {
                i.state = State.WB;
                System.out.println("  Instruction " + i.tag + " done executing.");
            }
                
                
        }
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
    int pc, op, dest, src1, src2, tag;
    State state;

    int latency, cycles;
    int exeTimer = 0;

    Instruction(int pc, int op, int dest, int src1, int src2, int tag) {
        this.pc = pc;
        this.op = op;
        this.dest = dest;
        this.src1 = src1;
        this.src2 = src2;
        this.state = null;
        this.tag = tag;
        setLatency();
        this.cycles = 0;
    }

    public String toString() {
        return tag + " " + pc + " " + op + " " + dest + " " + src1 + " " + src2;
    }

    public void setState(State state) {
        this.state = state;
    }

    /**
     * Set how many cycles this instruction needs in order to finish processing based on
     * op code: 0 => 1 cycle, 1 => 2 cycles, 2 => 5 cycles.
     */
    private void setLatency() {
        switch (this.op) {
            case 0:
                this.latency = 1;
                break;
            case 1:
                this.latency = 2;
                break;
            case 2:
                this.latency = 5;
                break;
            default:
                break;
        }
    }
}