import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;

enum State {
    IF,
    ID,
    IS,
    EX,
    WB;
}

enum Reg {
    free,
    write,
}


public class Main {

    // Variables chosen by user
    static int schedulingQueueSize = 8;
    static int fetchRate = 8;
    static String filename = "./traces/val_trace_gcc_reordered.txt";

    public static void main(String args[]) {
        
        try {
            if (args.length != 0) {
                schedulingQueueSize = Integer.parseInt(args[0]);
                fetchRate = Integer.parseInt(args[1]);
                filename = args[2];
            }
            
        } catch (NumberFormatException e) {
            System.out.println("An error has occured, arguments potentially incorrect. Did you format it as: 'java Main <scheduleQueueSize> <fetchRate> <tracefile>'?");
            return;
        }


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
                //System.out.println(newInstruction.toString());
                instructions.add(newInstruction);
            }

            fileScanner.close();
        } catch (FileNotFoundException e) {
            System.out.println("Error reading file.");
            return;
        }        

        // Main Simulator Loop
        Iterator<Instruction> iterator = instructions.iterator();
        int instructionsProcessed = 0;
        Arrays.fill(register, -1);
        do  {
            fakeRetire();
            execute();
            issue();
            dispatch();
            
            System.out.println("Fetch");
            for (int i = 0; i < fetchRate; i++) {
                if (iterator.hasNext()) {
                    if (dispatchList.size() < fetchRate * 2){
                        fetch(iterator.next());
                        instructionsProcessed++;
                    }
                }
            }
        }
        while(advanceCycle(instructionsProcessed));

        System.out.println("Writing output to file...");
        writeOutput(tagNum);
        System.out.println("Done!");
    }

    private static void writeOutput(int tagNum) {
        // Data to be written in file
        String text = "";

        // Defining the file name of the file
        String name = filename.substring(filename.lastIndexOf('_') + 1);
        name = name.substring(0, name.lastIndexOf('.'));

        Path fileName = Path.of("./pipe_" + schedulingQueueSize + "_" + fetchRate + "_" + name + ".txt");

        for (Instruction i : instructions) {
            text += i + "\n";
        }

        text += "number of instructions = " + (tagNum) + "\n";
        text += "number of cycles       = " + (PC) + "\n";
        text += "IPC                    = " + String.format("%.5f", ((float)(tagNum) / (float)(PC))) + "\n";

        try {
            Files.writeString(fileName, text);
        } 
          catch (IOException e) {
            System.err.println("An error occurred: " + e.getMessage());
        }
    }

    private static int PC = 0;

    /**
     * This is a data structure that represents the trace file, holds all incoming instructions 
     */
    private static List<Instruction> instructions = new ArrayList<>();

    /**
     * 
     */
    private static Queue<Instruction> fakeROB = new LinkedList<>();

    /**
        This contains a list of instructions in either the IF or ID
        state. The dispatch_list models the Dispatch Queue. (By including both
        the IF and ID states, we don’t need to separately model the pipeline
        registers of the fetch and dispatch stages.)
    */
    private static List<Instruction> dispatchList = new ArrayList<>();

    /**
        This contains a list of instructions in the IS state (waiting for
        operands or available issue bandwidth). The issue_list models the
        Scheduling Queue.
    */
    private static List<Instruction> issueList = new ArrayList<>();

    /**
        This contains a list of instructions in the EX state (waiting
        for the execution latency of the operation). The execute_list models
        the FUs.
    */
    private static List<Instruction> executeList = new ArrayList<>();

    /*
     *  This is the array that will simulate the 'register', its 127 in size since the
     *  current instruction set specifies instructions will only registers 0 - 127
     *  -1 is no reg, so do nothing with that
     * 
     *  a register is 'renamed' via tagging that register with an instruction's tag. 
     *  A register is not named if -1
     */
    private static int[] register = new int[127];

    /**
     * @param List<Instruction> instructions
     * @return boolean - If the list is empty, return false and stop execution
     */
        
    private static boolean advanceCycle(int instructionsProcessed) {
        // Advance simulator cycle?
        boolean output = !(instructionsProcessed >= instructions.size() && fakeROB.size() <= 0);

        if (output)
            PC += 1;

        //if (PC > 10) return false;

        // Check if Instruction List is empty, return false
        return output;

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
        instruction.c_IF = PC;
        
        // Push instruction to fake ROB
        fakeROB.add(instruction);

        // Push to dispatch_list, dispatchList.size() should give us the count of this arraylist.
        dispatchList.add(instruction);

        System.out.println("  Fetched instruction: " + instruction);
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
        System.out.println("Dispatch");
        System.out.println("  " + dispatchList.size() + " instructions in list");

        List<Instruction> id_list = new ArrayList<>();

        // From the dispatch_list, construct a temp list of instructions in the ID
        for (int i = 0; i < dispatchList.size(); i++) {
            if (dispatchList.get(i).state == State.ID)
                id_list.add(dispatchList.get(i));
        }

        Collections.sort(id_list);
        Iterator<Instruction> iterator = id_list.iterator();

        while (iterator.hasNext()) {
            Instruction i = iterator.next();

            //if the scheduling queue is not full
            if (issueList.size() >= schedulingQueueSize)
                continue;

            // Remove from dispatch_list
            dispatchList.remove(i);
            issueList.add(i);

            // Transition from ID to IS state
            i.setState(State.IS);
            i.c_IS = PC;
            i.d_ID = PC - i.c_ID;

            // Rename source operands by looking up state in the register file
            // For each register that isnt -1, add to the renamed list
            if (i.src1 != -1 && register[i.src1] != -1) {
                i.renamedRegisters.add(register[i.src1]);
                i.isReady = false;
            }

            if (i.src2 != -1 && register[i.src2] != -1) {
                i.renamedRegisters.add(register[i.src2]);
                i.isReady = false;
            }

            // Rename destination by updating state in the register file
            if (i.dest != -1)
                register[i.dest] = i.tag;
        }

        // If still in dispatch, change from IF to ID state
        for (int j = 0; j < dispatchList.size(); j++) {
            Instruction i = dispatchList.get(j);

            if (i.state != State.ID) {
                i.setState(State.ID);
                i.d_IF = PC - i.c_IF;
                i.c_ID = PC;
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
        System.out.println("  " + issueList.size() + " instructions in list");

        // Get a list of all ready instructions
        List<Instruction> readyList = new ArrayList<>();
        Iterator<Instruction> iterator = issueList.iterator();

        while (iterator.hasNext()) {
            Instruction i = iterator.next();

            if (i.isReady) {
                readyList.add(i);
            }
            else {
                System.out.println(" Instruction " + i.tag + " is not ready, waiting on: " + i.renamedRegisters);
            }
        }


        // Issue instructions first based on their tag
        Collections.sort(readyList);

        // Uses iterator to prevent concurrent modification exception
        iterator = readyList.iterator();
        int limit = fetchRate + 1;
        int counter = 0;
        while (iterator.hasNext() && counter < limit) {
            Instruction i = iterator.next();
            
            i.setState(State.EX);
            i.d_IS = PC - i.c_IS;
            i.c_EX = PC;

            executeList.add(i);
            iterator.remove(); // removes item from scheduling queue, decrementing value 
            issueList.remove(i);
            counter++;
        }
    }

    /**
     * EX Stage:
     * Should do the following:
     *  1. Remove instruction from execute_list
     *  2. Transition from EX state to WB state
     *  3. Update register file state and wake up dependant instructions (set operand ready flags)
     */
    private static void execute() {
        System.out.println("Execute");
        System.out.println("  " + executeList.size() + " instructions in list");
        Iterator<Instruction> iterator = executeList.iterator();
        while (iterator.hasNext()) {
            Instruction i = iterator.next();
            
            // Increment timer to simulate execution latency
            if (i.exeTimer < i.latency) {
                i.exeTimer++; 
                System.out.println("  Instruction " + i.tag + " timer: " + i.exeTimer + "/" + i.latency);
            }
               
            // When the timer finishes, move to WB state
            else {
                i.setState(State.WB);

                i.d_EX = PC - i.c_EX;
                i.c_WB = PC;

                System.out.println("  Instruction " + i + " done executing. Broadcasting to reservation tables");
                
                // Broadcast to all instructions in the scheduling queue that this instruction is finished
                if (i.dest != -1) {
                    for (Instruction b_i : issueList) {
                        b_i.attemptWakeUp(i.tag);
                    }

                    if (register[i.dest] == i.tag)
                        register[i.dest] = -1;
                }
                    
                iterator.remove();
            }       
        }
    }
    /**
     * Removes instructions from the head of the fake-ROB
     * until an instruction is reached that is not in the WB state
     */
    private static void fakeRetire() {
        System.out.println("FakeRetire");

        while (true) { 
            if (!fakeROB.isEmpty() && fakeROB.peek().state == State.WB) {
                Instruction i = fakeROB.remove();
                i.d_WB = 1;
            }
            else {
                break;
            }
        }
    }
}

class Instruction implements Comparable<Instruction> {
    int pc, op, dest, src1, src2, tag;

    // Store cycle and duration instruction was in each state for final output
    int c_IF = -1, d_IF = -1;
    int c_ID = -1, d_ID = -1;
    int c_IS = -1, d_IS = -1;
    int c_EX = -1, d_EX = -1;
    int c_WB = -1, d_WB = -1;

    State state;
    boolean isReady = true;
    boolean isRenamed = false;
    List<Integer> renamedRegisters = new ArrayList<>();

    int latency;
    int exeTimer = 1;

    Instruction(int pc, int op, int dest, int src1, int src2, int tag) {
        this.pc = pc;
        this.op = op;
        this.dest = dest;
        this.src1 = src1;
        this.src2 = src2;
        this.state = null;
        this.tag = tag;
        setLatency();
    }

    // <tag> fu{<operation type>} src{<src1 reg#>,<src2 reg#>} dst{<dest reg#>} IF{<cycle>,<duration>} ID{<cycle>,<duration>} IS{<cycle>,<duration>} EX{<cycle>,<duration>} WB{<cycle>,<duration>} 
    @Override
    public String toString() {
        return tag + " "
            + "fu{" + op + "} "
            + "src{" + src1 + "," + src2 + "} "
            + "dst{" + dest + "} "
            + "IF{" + c_IF + "," + d_IF + "} "
            + "ID{" + c_ID + "," + d_ID + "} "
            + "IS{" + c_IS + "," + d_IS + "} "
            + "EX{" + c_EX + "," + d_EX + "} "
            + "WB{" + c_WB + "," + d_WB + "}";
    }

    public void setState(State state) {
        this.state = state;
    }

    // Implement the compareTo method for sorting
    @Override
    public int compareTo(Instruction other) {
        return Integer.compare(this.tag, other.tag); // Sort by pc, ascending order

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
    /**
     * If the instruction recieves a tag in their renamedRegister, it will remove it
     * If it no longer waiting for a result (renameRegister is empty), then set this instruction to ready
     * 
     * @param tag - The tag being broadcast to the instruction
     * 
     */
    public void attemptWakeUp(int tag) {
        if (renamedRegisters.contains(tag)) {
            renamedRegisters.remove(Integer.valueOf(tag));
        }

        if (renamedRegisters.isEmpty()) {
            isReady = true;
            System.out.println("  Instruction " + tag + " awake");
        }
        else {
            System.err.println("  Fail to wake up, waiting on: " + renamedRegisters);
        }
    }
}